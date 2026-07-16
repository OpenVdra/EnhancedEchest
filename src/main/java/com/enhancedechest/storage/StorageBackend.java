package com.enhancedechest.storage;

import com.enhancedechest.storage.EnderChestStorage.RawChestRow;
import com.enhancedechest.storage.EnderChestStorage.RawPlayerRow;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;

/**
 * The SQL half of the lazy-load + write-back cache model: the narrow contract {@link CachedStorage}
 * needs from a database backend. The cache is authoritative for every owner it holds <i>resident</i>,
 * so the backend is asked to read one player's rows on a cache miss ({@link #loadOwner}),
 * bulk-write dirty rows back (autosave / quit / shutdown), answer the few
 * whole-database questions the cache cannot ({@link #findExpired}, {@link #countChests},
 * {@link #findUuidByName}, {@link #loadAllPlayers} for the startup name index), snapshot itself for a
 * backup, and receive the verbatim {@code /ee import} copy — there are deliberately <b>no per-row
 * write operations</b> here; all row-level semantics live in {@link CachedStorage}.
 *
 * <p>All methods execute synchronously on the calling thread; callers keep them off region/main
 * threads (cache-miss loads run on the {@code DbExecutor}, everything else on async timers or the
 * shutdown flush).
 */
public interface StorageBackend {

    /** Creates schema, runs schema migrations, and prepares connections. Called once before anything else. */
    void init();

    /** Closes all connections. Safe to call even if init() was not completed. */
    void close();

    /**
     * True if this backend can produce a file snapshot via {@link #backup(Path)}. Only the
     * file-based SQLite backend supports it; remote backends (MySQL/MariaDB/Postgres) return false
     * and must be backed up with the database server's own tooling.
     */
    default boolean supportsBackup() {
        return false;
    }

    /**
     * Writes a consistent snapshot of the entire database to {@code target} (which must not already
     * exist). Only valid when {@link #supportsBackup()} is true. The caller ({@code CachedStorage})
     * flushes dirty rows first, so the snapshot always contains every in-memory change.
     *
     * @throws Exception if the snapshot fails; the caller logs it and leaves the live DB untouched.
     */
    default void backup(Path target) throws Exception {
        throw new UnsupportedOperationException("Backup is not supported by this storage backend");
    }

    /**
     * Bulk-inserts raw {@code players} and {@code enderchests} rows <b>verbatim</b> into this (fresh)
     * backend, in a single transaction — the DB→DB conversion primitive behind {@code /ee import}.
     * A duplicate primary key (or any other failure) rolls the whole transaction back and throws.
     *
     * @return {@code [playersInserted, chestsInserted]}
     */
    int[] importRows(List<RawPlayerRow> players, List<RawChestRow> chests);

    /** Primary key of one {@code enderchests} row, used to flush a deletion. */
    record ChestKey(String playerUuid, int chestIndex) {}

    /**
     * Reads every {@code players} row verbatim. Used once at startup to build the in-memory
     * {@code PlayerNameIndex} (uuid → username is tiny even for huge rosters); chest data is never
     * bulk-loaded.
     */
    List<RawPlayerRow> loadAllPlayers();

    // ---- per-player cache-miss reads ----

    /** One player's full cache-miss load: their {@code enderchests} rows plus their (nullable) {@code players} row. */
    record OwnerRows(List<RawChestRow> chests, @Nullable RawPlayerRow player) {}

    /**
     * Reads one player's {@code enderchests} rows and {@code players} row verbatim in a single
     * connection acquisition (lazy cache load on first touch — the hottest backend read, so it must
     * not pay for two pool round-trips).
     */
    OwnerRows loadOwner(String playerUuid);

    // ---- whole-database questions the cache cannot answer from resident owners alone ----

    /** Primary key + kind of one expired {@code enderchests} row, returned by {@link #findExpired}. */
    record ExpiredKey(String playerUuid, int chestIndex, int kind) {}

    /**
     * Keys of every row whose {@code expires_at} is set and at or before {@code now}. Candidates only:
     * the caller re-verifies each hit against the authoritative in-memory row (which may be newer than
     * what was last flushed here).
     */
    List<ExpiredKey> findExpired(long now);

    /** Total number of {@code enderchests} rows. The caller flushes first so pending changes count. */
    long countChests();

    /**
     * Resolves a stored username to its UUID string, case-insensitively, or null when unknown.
     * The caller checks resident (possibly renamed-but-unflushed) owners first.
     */
    @Nullable String findUuidByName(String name);

    /**
     * Writes every dirty row back in <b>one</b> transaction: each {@code chestUpserts} /
     * {@code playerRows} row replaces whatever the table holds for its key (native dialect upsert),
     * and every {@code chestDeletes} key is removed. A single transaction and a single connection
     * acquisition — the flush runs under the cache's flush lock, so its duration directly gates quit
     * write-back latency. A failure rolls the whole flush back and throws, so the caller can re-mark
     * the rows dirty and retry on the next autosave.
     */
    void flushDirty(List<RawChestRow> chestUpserts, List<ChestKey> chestDeletes, List<RawPlayerRow> playerRows);
}
