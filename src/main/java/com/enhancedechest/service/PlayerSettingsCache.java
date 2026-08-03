package com.enhancedechest.service;

import com.enhancedechest.model.PlayerSettings;
import com.enhancedechest.storage.EnderChestStorage;
import com.enhancedechest.telemetry.Telemetry;
import org.bukkit.Bukkit;
import org.slf4j.Logger;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Write-through read cache of per-player settings, keyed by UUID. Populated on join
 * ({@link #preloadSettings}), read by the dialog-open paths, updated in place on change, and evicted
 * on quit ({@link #evictSettings}) — so it is bounded by the online-player count. Writes go straight
 * to the DB (write-through), so the cache holds no dirty state and needs no shutdown flush. See the
 * leak-free invariant documented on {@link #preloadSettings}.
 *
 * <p>{@link #preloadSettings} is also where a player's name is recorded, because the settings row it
 * already loads carries the last known {@code username} — so the check is free and the write only
 * happens on a first-ever join or a rename. That is what makes {@link PlayerNameIndex} complete without
 * ever reading the {@code playerdata} folder: every player who joins is in the {@code players} table
 * from then on, and admin tab-completion needs no other source. {@code ChestOpener}'s open prelude does
 * the same check against its own already-loaded row, covering players who were online before this
 * plugin loaded.
 */
public final class PlayerSettingsCache {

    private final EnderChestStorage storage;
    private final DbExecutor db;
    private final Logger logger;
    private final PlayerNameIndex nameIndex;
    private final Telemetry telemetry;

    private final ConcurrentHashMap<UUID, PlayerSettings> settingsCache = new ConcurrentHashMap<>();

    public PlayerSettingsCache(EnderChestStorage storage, DbExecutor db, Logger logger,
                               PlayerNameIndex nameIndex, Telemetry telemetry) {
        this.storage = storage;
        this.db = db;
        this.logger = logger;
        this.nameIndex = nameIndex;
        this.telemetry = telemetry;
    }

    /**
     * Loads a player's settings into the cache on join. This is the cache's <b>only</b> inserter,
     * which keeps the leak-free invariant simple: every entry added here is removed by
     * {@link #evictSettings} on quit. The post-load online re-check covers the join-then-immediate-quit
     * race — if the player already left while the load was in flight (so {@code evictSettings} ran
     * before this put), the entry is dropped right after it is added, so nothing is ever orphaned.
     *
     * <p>{@code currentName} is the joining player's name, compared against the {@code username} on the
     * row that was just loaded. Recording it here rather than only on a chest open is what keeps
     * {@link PlayerNameIndex} — and therefore admin tab-completion — complete from the database alone,
     * with no {@code playerdata} scan anywhere in the plugin.
     */
    public void preloadSettings(UUID owner, String currentName) {
        db.supply(() -> storage.loadSettings(owner))
                .thenAccept(settings -> {
                    settingsCache.put(owner, settings);
                    markSeenAsync(owner, currentName);
                    if (Bukkit.getPlayer(owner) == null) {
                        settingsCache.remove(owner);
                    }
                })
                .exceptionally(e -> {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    logger.error("Failed to preload settings for {}", owner, cause);
                    telemetry.error(cause, "settings.preload");
                    return null;
                });
    }

    /**
     * Records a name in the in-memory {@link PlayerNameIndex} only, with <b>no</b> DB write — called on
     * join, where the name is already in hand, so an admin can tab-complete the player immediately
     * instead of waiting on {@link #preloadSettings}'s round trip (or losing the name entirely if the
     * startup index load failed). {@code now} as the last-seen time is exactly right: they just joined.
     */
    public void indexName(UUID owner, String username) {
        nameIndex.put(owner, username, System.currentTimeMillis());
    }

    /** Evicts a player's cached settings on quit. Paired with {@link #preloadSettings} so the cache stays bounded by online players. */
    public void evictSettings(UUID owner) {
        settingsCache.remove(owner);
    }

    /**
     * Returns the player's settings, served from the cache when present (the common case for an online
     * player). A miss — preload still in flight, or the player was already online before the plugin
     * loaded — falls back to a one-off DB read that is deliberately <b>not</b> cached, so
     * {@link #preloadSettings} remains the sole inserter and the leak-free invariant holds.
     */
    public CompletableFuture<PlayerSettings> loadSettingsAsync(UUID owner) {
        PlayerSettings cached = settingsCache.get(owner);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return db.supply(() -> storage.loadSettings(owner));
    }

    /**
     * Persists the player's edit-mode preference with a single targeted upsert (no preceding read),
     * leaving every other setting untouched. Write-through: the cached copy is updated in place first
     * (if present) so the next dialog open reflects the change without a DB read, then the DB is
     * written. Uses {@code computeIfPresent} so it never inserts — preserving the leak-free invariant.
     */
    public CompletableFuture<Void> setEditModeAsync(UUID owner, boolean editMode) {
        settingsCache.computeIfPresent(owner, (k, s) -> s.withEditMode(editMode));
        return db.run(() -> storage.setEditMode(owner, editMode));
    }

    /**
     * Persists the base-chest size baseline the default-size reconcile just applied, with a single targeted
     * upsert (no preceding read), leaving edit-mode untouched. Write-through: the cached copy is updated in
     * place first (if present) so the next reconcile sees the new baseline without a DB read (and so its
     * fast path holds), then the DB is written. Uses {@code computeIfPresent} so it never inserts —
     * preserving the leak-free invariant.
     */
    public CompletableFuture<Void> setAppliedDefaultSizeAsync(UUID owner, int size) {
        settingsCache.computeIfPresent(owner, (k, s) -> s.withAppliedDefaultSize(size));
        return db.run(() -> storage.setAppliedDefaultSize(owner, size));
    }

    /**
     * Records that the player is here now: their in-game name (offline {@code /ee view} resolution) and
     * {@code last_online}, which decides how long they keep showing up in admin name suggestions. Called
     * on join and quit, and by {@code ChestOpener}'s open prelude when the name it loaded is stale.
     *
     * <p>Unconditional, unlike the old name-only write, because {@code last_online} changes every time
     * by definition — and it costs no extra statement: {@code CachedStorage} only mutates the resident
     * row and marks it dirty, so this rides the next batched flush together with the player's chests.
     *
     * <p>Write-through: the cached copy is updated in place first (if present), then the storage layer.
     * Uses {@code computeIfPresent} so it never inserts — preserving the leak-free invariant.
     */
    public CompletableFuture<Void> markSeenAsync(UUID owner, String username) {
        long now = System.currentTimeMillis();
        settingsCache.computeIfPresent(owner, (k, s) -> s.withUsername(username));
        nameIndex.put(owner, username, now);
        return db.run(() -> storage.recordPlayerSeen(owner, username, now))
                .exceptionally(e -> {
                    logger.warn("Failed to record name for {}", owner, e.getCause() != null ? e.getCause() : e);
                    return null;
                });
    }

    /**
     * Drops every cached entry on shutdown. The write-through cache holds no dirty state (every change
     * was persisted immediately), so there is nothing to flush — just release the references.
     */
    public void clear() {
        settingsCache.clear();
    }
}
