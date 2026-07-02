package com.enhancedechest.service;

import com.enhancedechest.model.PlayerSettings;
import com.enhancedechest.storage.EnderChestStorage;
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
 * <p>The name index is <b>not</b> touched here. It is written lazily by {@code ChestOpener}'s shared
 * open prelude — the first time a player actually opens their ender chest (or the list/right-click
 * equivalents) — reusing the settings row already loaded there, rather than unconditionally on join. See
 * {@link #setUsernameAsync}.
 */
public final class PlayerSettingsCache {

    private final EnderChestStorage storage;
    private final DbExecutor db;
    private final Logger logger;

    private final ConcurrentHashMap<UUID, PlayerSettings> settingsCache = new ConcurrentHashMap<>();

    public PlayerSettingsCache(EnderChestStorage storage, DbExecutor db, Logger logger) {
        this.storage = storage;
        this.db = db;
        this.logger = logger;
    }

    /**
     * Loads a player's settings into the cache on join. This is the cache's <b>only</b> inserter,
     * which keeps the leak-free invariant simple: every entry added here is removed by
     * {@link #evictSettings} on quit. The post-load online re-check covers the join-then-immediate-quit
     * race — if the player already left while the load was in flight (so {@code evictSettings} ran
     * before this put), the entry is dropped right after it is added, so nothing is ever orphaned.
     */
    public void preloadSettings(UUID owner) {
        db.supply(() -> storage.loadSettings(owner))
                .thenAccept(settings -> {
                    settingsCache.put(owner, settings);
                    if (Bukkit.getPlayer(owner) == null) {
                        settingsCache.remove(owner);
                    }
                })
                .exceptionally(e -> {
                    logger.error("Failed to preload settings for {}", owner, e.getCause() != null ? e.getCause() : e);
                    return null;
                });
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
     * Records the player's current in-game name (offline {@code /ee view} resolution), with a single
     * targeted upsert (no preceding read). Called by {@code ChestOpener}'s open prelude only when the
     * loaded {@code username} differs from the player's current name — a returning player whose name
     * hasn't changed costs no write. Write-through: the cached copy is updated in place first (if
     * present), then the DB is written. Uses {@code computeIfPresent} so it never inserts — preserving
     * the leak-free invariant.
     */
    public CompletableFuture<Void> setUsernameAsync(UUID owner, String username) {
        settingsCache.computeIfPresent(owner, (k, s) -> s.withUsername(username));
        return db.run(() -> storage.upsertPlayerName(owner, username))
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
