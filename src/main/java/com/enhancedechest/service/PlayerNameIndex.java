package com.enhancedechest.service;

import com.enhancedechest.storage.EnderChestStorage;
import com.enhancedechest.telemetry.Telemetry;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * In-memory index of every player name the plugin has ever recorded (the {@code players.username}
 * column — see {@code PlayerSettingsCache#markSeenAsync}), used to answer offline-player
 * tab-completion and name resolution from memory instead of hitting the DB on every keystroke.
 *
 * <p>Unlike {@link PlayerSettingsCache} (bounded to online players, evicted on quit), this index holds
 * every known player for the lifetime of the server — it is loaded once in full via {@link #loadAll}
 * at startup and kept in sync afterward by a single {@link #put} call per name change, so it never
 * needs a DB round-trip on the command/suggestion hot path.
 *
 * <p><b>The database is the only name source. Nothing here — and nothing that reads this index — may
 * touch the {@code playerdata} folder.</b> {@code Bukkit.getOfflinePlayers()} looks like the obvious way
 * to fill the gaps, but it builds one {@code OfflinePlayer} per file in that folder and {@code getName()}
 * on any whose profile is not in the usercache loads and decompresses that player's {@code .dat} file.
 * On a server with a long history that is tens of thousands of NBT reads and their transient buffers:
 * it pinned a server thread on disk I/O when the suggestion providers called it per keystroke, and doing
 * it in one startup sweep instead only concentrates the same memory pressure into a burst that can cost
 * the server an OOM kill. Names that are not in the DB are simply not completable; the admin types them
 * in full and the command still resolves them.
 *
 * <p>Coverage therefore comes from writes, not scans: {@code PlayerSettingsCache.preloadSettings}
 * records a joining player's name (reusing the settings row the join already loads, so a returning
 * player whose name is unchanged costs no write), and {@code ChestOpener}'s open prelude does the same.
 * Every player who joins is in the DB from then on.
 *
 * <p>Keyed by lower-cased name in a {@link ConcurrentSkipListMap} (not a plain hash map) specifically
 * so prefix search — the shape every tab-completion query actually needs — is a {@code subMap} range
 * lookup, O(log n + k) for k matches, rather than an O(n) scan of the whole roster. Lock-free reads
 * make it safe to call from Brigadier suggestion callbacks (main thread) while an async name update is
 * landing concurrently.
 */
public final class PlayerNameIndex {

    /**
     * One recorded player: their current known name, UUID, and the epoch-ms they were last seen
     * ({@code 0} when never recorded — a row that predates last-online tracking).
     */
    public record NameEntry(UUID uuid, String displayName, long lastOnline) {}

    private final StorageGateway gateway;
    private final Logger logger;
    private final Telemetry telemetry;

    private final ConcurrentSkipListMap<String, NameEntry> byLowerName = new ConcurrentSkipListMap<>();

    /**
     * How far back {@link #prefixMatches} will offer an offline player, in millis;
     * {@code 0} means no limit ({@code commands.suggest-offline-within: all}). Runtime-tunable through
     * {@link #setSuggestWindowMillis} from {@code /ee reload}, and read on the suggestion hot path from
     * a server thread, so volatile.
     */
    private volatile long suggestWindowMillis;

    public PlayerNameIndex(StorageGateway gateway, Logger logger, Telemetry telemetry, long suggestWindowMillis) {
        this.gateway = gateway;
        this.logger = logger;
        this.telemetry = telemetry;
        this.suggestWindowMillis = suggestWindowMillis;
    }

    /** Re-applies {@code commands.suggest-offline-within} after a reload. {@code 0} = suggest everyone. */
    public void setSuggestWindowMillis(long suggestWindowMillis) {
        this.suggestWindowMillis = suggestWindowMillis;
    }

    /** Loads every known (uuid, username, last-seen) triple from the DB once. Call exactly once, at plugin startup. */
    public CompletableFuture<Void> loadAll() {
        return gateway.loadAllPlayerNamesAsync()
                .thenAccept(names -> {
                    for (EnderChestStorage.PlayerNameRecord r : names) {
                        put(r.uuid(), r.username(), r.lastOnline());
                    }
                    logger.info("Loaded {} known player name(s) for offline lookups.", names.size());
                })
                .exceptionally(e -> {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    logger.error("Failed to load player name index", cause);
                    telemetry.error(cause, "startup.name-index-load");
                    return null;
                });
    }

    /**
     * Records or updates one player's name and last-seen time. Called by
     * {@code PlayerSettingsCache.markSeenAsync} alongside its DB write, so the index never drifts from
     * what {@code findUuidByName} would answer. A rename simply adds a new key; the old lower-cased name
     * is left pointing at the same UUID (the DB itself has no rename history either — this matches
     * {@code SQL_NAME_FIND}'s existing behavior).
     */
    public void put(UUID uuid, String username, long lastOnline) {
        if (uuid == null || username == null || username.isBlank()) {
            return;
        }
        byLowerName.put(username.toLowerCase(Locale.ROOT), new NameEntry(uuid, username, lastOnline));
    }

    /**
     * Case-insensitive point lookup, O(log n) — used to resolve an already-typed name to a UUID.
     *
     * <p><b>Not</b> filtered by the suggest window: that window only decides whose name is <i>offered</i>
     * while typing. A name typed in full must always resolve, however long its owner has been away —
     * otherwise an admin could no longer reach the chests of a player who stopped logging in, which is
     * exactly when they most often need to.
     */
    public UUID findUuid(String name) {
        NameEntry entry = byLowerName.get(name.toLowerCase(Locale.ROOT));
        return entry != null ? entry.uuid() : null;
    }

    /**
     * Returns up to {@code limit} known names starting with {@code lowerPrefix} (already lower-cased
     * by the caller), ascending, skipping players last seen longer ago than the suggest window.
     * {@code subMap} jumps straight to the matching range instead of scanning every entry, so this stays
     * cheap even once the roster is in the tens of thousands.
     *
     * <p>Filtered-out entries still cost a step of the walk, so with a narrow window and a broad prefix
     * this can examine many more than {@code limit} entries — still a lock-free in-memory walk of a few
     * thousand nodes at worst, and typing one more character collapses the range.
     */
    public List<NameEntry> prefixMatches(String lowerPrefix, int limit) {
        List<NameEntry> results = new ArrayList<>(Math.min(limit, 16));
        long window = suggestWindowMillis;
        long cutoff = window <= 0 ? Long.MIN_VALUE : System.currentTimeMillis() - window;
        var range = lowerPrefix.isEmpty()
                ? byLowerName
                : byLowerName.subMap(lowerPrefix, lowerPrefix + Character.MAX_VALUE);
        for (NameEntry entry : range.values()) {
            if (entry.lastOnline() < cutoff) {
                continue;
            }
            results.add(entry);
            if (results.size() >= limit) {
                break;
            }
        }
        return results;
    }
}
