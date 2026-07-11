package com.enhancedechest.service;

import com.enhancedechest.telemetry.Telemetry;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * In-memory index of every player name the plugin has ever recorded (the {@code players.username}
 * column — see {@code PlayerSettingsCache#setUsernameAsync}), used to answer offline-player
 * tab-completion and name resolution from memory instead of hitting the DB on every keystroke.
 *
 * <p>Unlike {@link PlayerSettingsCache} (bounded to online players, evicted on quit), this index holds
 * every known player for the lifetime of the server — it is loaded once in full via {@link #loadAll}
 * at startup and kept in sync afterward by a single {@link #put} call per name change, so it never
 * needs a DB round-trip on the command/suggestion hot path.
 *
 * <p>Keyed by lower-cased name in a {@link ConcurrentSkipListMap} (not a plain hash map) specifically
 * so prefix search — the shape every tab-completion query actually needs — is a {@code subMap} range
 * lookup, O(log n + k) for k matches, rather than an O(n) scan of the whole roster. Lock-free reads
 * make it safe to call from Brigadier suggestion callbacks (main thread) while an async name update is
 * landing concurrently.
 */
public final class PlayerNameIndex {

    /** One recorded player: their current known name and UUID. */
    public record NameEntry(UUID uuid, String displayName) {}

    private final StorageGateway gateway;
    private final Logger logger;
    private final Telemetry telemetry;

    private final ConcurrentSkipListMap<String, NameEntry> byLowerName = new ConcurrentSkipListMap<>();

    public PlayerNameIndex(StorageGateway gateway, Logger logger, Telemetry telemetry) {
        this.gateway = gateway;
        this.logger = logger;
        this.telemetry = telemetry;
    }

    /** Loads every known (uuid, username) pair from the DB once. Call exactly once, at plugin startup. */
    public CompletableFuture<Void> loadAll() {
        return gateway.loadAllPlayerNamesAsync()
                .thenAccept(names -> {
                    for (Map.Entry<UUID, String> e : names.entrySet()) {
                        put(e.getKey(), e.getValue());
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
     * Records or updates one player's name. Called by {@code PlayerSettingsCache.setUsernameAsync}
     * alongside its DB write, so the index never drifts from what {@code findUuidByName} would answer.
     * A rename simply adds a new key; the old lower-cased name is left pointing at the same UUID (the
     * DB itself has no rename history either — this matches {@code SQL_NAME_FIND}'s existing behavior).
     */
    public void put(UUID uuid, String username) {
        if (uuid == null || username == null || username.isBlank()) {
            return;
        }
        byLowerName.put(username.toLowerCase(Locale.ROOT), new NameEntry(uuid, username));
    }

    /** Case-insensitive point lookup, O(log n) — used to resolve an already-typed name to a UUID. */
    public UUID findUuid(String name) {
        NameEntry entry = byLowerName.get(name.toLowerCase(Locale.ROOT));
        return entry != null ? entry.uuid() : null;
    }

    /**
     * Returns up to {@code limit} known names starting with {@code lowerPrefix} (already lower-cased
     * by the caller), ascending. {@code subMap} jumps straight to the matching range instead of
     * scanning every entry, so this stays cheap even once the roster is in the tens of thousands.
     */
    public List<NameEntry> prefixMatches(String lowerPrefix, int limit) {
        List<NameEntry> results = new ArrayList<>(Math.min(limit, 16));
        var range = lowerPrefix.isEmpty()
                ? byLowerName
                : byLowerName.subMap(lowerPrefix, lowerPrefix + Character.MAX_VALUE);
        for (NameEntry entry : range.values()) {
            results.add(entry);
            if (results.size() >= limit) {
                break;
            }
        }
        return results;
    }
}
