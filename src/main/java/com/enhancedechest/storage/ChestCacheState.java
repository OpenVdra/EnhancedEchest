package com.enhancedechest.storage;

import com.enhancedechest.model.ChestKind;
import com.enhancedechest.storage.EnderChestStorage.RawChestRow;
import com.enhancedechest.storage.EnderChestStorage.RawPlayerRow;
import com.enhancedechest.storage.StorageBackend.ChestKey;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * The pure in-memory model behind {@link CachedStorage}: the materialized {@code enderchests} /
 * {@code players} rows plus the dirty-key tracking, and every low-level map operation the domain
 * layer builds on (index allocation, row insert/remove, primary-flag clearing, snapshotting,
 * raw-row (de)materialization). It knows nothing about loading, eviction, residency, or the SQL
 * backend — those live in {@link OwnerResidencyCache}.
 *
 * <p><b>Not thread-safe.</b> Every method must be called while holding
 * {@link OwnerResidencyCache}'s lock; the residency cache is the sole owner of an instance and
 * only ever touches it inside that lock. {@code byte[]} contents are treated as immutable by
 * convention.
 */
final class ChestCacheState {

    /** One in-memory {@code enderchests} row. Mutated only under the residency lock. */
    static final class ChestRow {
        int size;
        @Nullable String customName;
        boolean primary;
        @Nullable byte[] data;
        boolean migrated;
        long lastUpdated;
        int kind;
        @Nullable Long expiresAt;
        @Nullable String icon;
    }

    /** One in-memory {@code players} row (settings + name index). Mutated only under the residency lock. */
    static final class PlayerRow {
        @Nullable String username;
        boolean editMode;
        int appliedDefaultSize;
    }

    /** Identity of one chest row (dirty-set key). */
    record ChestId(UUID owner, int index) {}

    /**
     * Immutable snapshot of a row taken before {@link CachedStorage#transferChests} starts mutating
     * the maps (live {@code TreeMap} entries must not be held across removals).
     */
    record RowSnap(int index, int size, @Nullable String customName, boolean primary,
                   @Nullable byte[] data, int kind, @Nullable String icon) {}

    /**
     * One drained batch of dirty rows: the writes to apply ({@code chestUpserts}/{@code chestDeletes}/
     * {@code playerRows}) plus the keys to re-mark dirty if the backend write fails
     * ({@code chestSnapshot}/{@code playerSnapshot}).
     */
    record DirtyBatch(List<RawChestRow> chestUpserts, List<ChestKey> chestDeletes,
                      List<ChestId> chestSnapshot, List<RawPlayerRow> playerRows,
                      List<UUID> playerSnapshot) {
        boolean isEmpty() {
            return chestSnapshot.isEmpty() && playerSnapshot.isEmpty();
        }
    }

    /** Per-owner chest rows, ordered by index (TreeMap keeps list/next-free-index ops trivial). */
    private final Map<UUID, TreeMap<Integer, ChestRow>> chests = new HashMap<>();
    private final Map<UUID, PlayerRow> players = new HashMap<>();
    /**
     * Chest indices changed since the last flush, keyed by owner (an owner is never mapped to an
     * empty set); row presence at flush time decides upsert vs delete. Keying by owner makes the
     * per-quit {@link #isClean} check O(1) and the quit-path group drain in {@link #collectDirty}
     * proportional to the drained owners, instead of both scanning every dirty key.
     */
    private final Map<UUID, Set<Integer>> dirtyChests = new HashMap<>();
    private final Set<UUID> dirtyPlayers = new HashSet<>();

    // ---- lookups ----

    @Nullable ChestRow row(UUID owner, int index) {
        TreeMap<Integer, ChestRow> owned = chests.get(owner);
        return owned == null ? null : owned.get(index);
    }

    TreeMap<Integer, ChestRow> ownedRows(UUID owner) {
        return chests.computeIfAbsent(owner, k -> new TreeMap<>());
    }

    /** The owner's rows, or {@code null} if none are materialized (read-only callers). */
    @Nullable TreeMap<Integer, ChestRow> ownedRowsOrNull(UUID owner) {
        return chests.get(owner);
    }

    PlayerRow playerRow(UUID owner) {
        return players.computeIfAbsent(owner, k -> new PlayerRow());
    }

    @Nullable PlayerRow playerRowOrNull(UUID owner) {
        return players.get(owner);
    }

    int maxIndex(UUID owner) {
        TreeMap<Integer, ChestRow> owned = chests.get(owner);
        return (owned == null || owned.isEmpty()) ? 0 : owned.lastKey();
    }

    /** Applies {@code action} to every resident owner's rows (the {@link CachedStorage#findExpired} scan). */
    void forEachRow(RowVisitor action) {
        chests.forEach((owner, owned) -> owned.forEach((index, row) -> action.visit(owner, index, row)));
    }

    /** Visitor for {@link #forEachRow}. */
    @FunctionalInterface
    interface RowVisitor {
        void visit(UUID owner, int index, ChestRow row);
    }

    // ---- mutations ----

    void markChestDirty(UUID owner, int index) {
        dirtyChests.computeIfAbsent(owner, k -> new HashSet<>()).add(index);
    }

    void markPlayerDirty(UUID owner) {
        dirtyPlayers.add(owner);
    }

    void insertRow(UUID owner, int index, int size, @Nullable String customName, boolean primary,
                   @Nullable byte[] data, boolean migrated, int kind, @Nullable Long expiresAt,
                   @Nullable String icon) {
        ChestRow row = new ChestRow();
        row.size = size;
        row.customName = customName;
        row.primary = primary;
        row.data = data;
        row.migrated = migrated;
        row.lastUpdated = System.currentTimeMillis();
        row.kind = kind;
        row.expiresAt = expiresAt;
        row.icon = icon;
        ownedRows(owner).put(index, row);
        markChestDirty(owner, index);
    }

    /** Inserts a temp (kind=TEMP) chest at the next free index, carrying spilled bytes and an expiry. */
    void insertTempRow(UUID owner, int size, byte[] data, long expiresAt) {
        insertRow(owner, maxIndex(owner) + 1, size, null, false, data, false,
                ChestKind.TEMP.code(), expiresAt, null);
    }

    void removeRow(UUID owner, int index) {
        TreeMap<Integer, ChestRow> owned = chests.get(owner);
        if (owned == null || owned.remove(index) == null) {
            return;
        }
        if (owned.isEmpty()) {
            chests.remove(owner);
        }
        markChestDirty(owner, index);
    }

    void clearPrimary(UUID owner) {
        TreeMap<Integer, ChestRow> owned = chests.get(owner);
        if (owned == null) {
            return;
        }
        owned.forEach((index, row) -> {
            if (row.primary) {
                row.primary = false;
                markChestDirty(owner, index);
            }
        });
    }

    /**
     * Snapshots a player's rows as immutable copies, ordered by index. {@code normalOnly} keeps only
     * NORMAL rows; {@code onlyIndex} (when non-null) keeps only that index.
     */
    List<RowSnap> snapshotRows(UUID owner, @Nullable Integer onlyIndex, boolean normalOnly) {
        TreeMap<Integer, ChestRow> owned = chests.get(owner);
        List<RowSnap> result = new ArrayList<>();
        if (owned == null) {
            return result;
        }
        owned.forEach((index, row) -> {
            if (normalOnly && row.kind != ChestKind.NORMAL.code()) return;
            if (onlyIndex != null && index.intValue() != onlyIndex.intValue()) return;
            result.add(new RowSnap(index, row.size, row.customName, row.primary,
                    row.data, row.kind, row.icon));
        });
        return result;
    }

    // ---- raw-row (de)materialization (cache-miss load, post-import refresh, flush) ----

    /** Materializes one in-memory row as the verbatim flush/import row shape. */
    RawChestRow toRaw(UUID owner, int index, ChestRow row) {
        return new RawChestRow(owner.toString(), index, row.size, row.customName,
                row.primary ? 1 : 0, row.data, row.migrated ? 1 : 0, row.lastUpdated,
                row.kind, row.expiresAt, row.icon);
    }

    /** Loads one verbatim chest row into memory (cache-miss load and post-import refresh; not dirtied). */
    void applyRawChest(RawChestRow c) {
        ChestRow row = new ChestRow();
        row.size = c.size();
        row.customName = c.customName();
        row.primary = c.isPrimary() != 0;
        row.data = c.containerData();
        row.migrated = c.migrated() != 0;
        row.lastUpdated = c.lastUpdated();
        row.kind = c.kind();
        row.expiresAt = c.expiresAt();
        row.icon = c.icon();
        ownedRows(UUID.fromString(c.playerUuid())).put(c.chestIndex(), row);
    }

    /** Loads one verbatim player row into memory (cache-miss load and post-import refresh; not dirtied). */
    void applyRawPlayer(RawPlayerRow p) {
        PlayerRow row = new PlayerRow();
        row.username = p.username();
        row.editMode = p.editMode() != 0;
        row.appliedDefaultSize = p.appliedDefaultSize();
        players.put(UUID.fromString(p.playerUuid()), row);
    }

    // ---- residency / flush support (used by OwnerResidencyCache) ----

    /** True when no dirty row belongs to {@code owner}. */
    boolean isClean(UUID owner) {
        return !dirtyPlayers.contains(owner) && !dirtyChests.containsKey(owner);
    }

    /** Drops one owner's materialized rows (residency removal lives in the cache). */
    void dropOwner(UUID owner) {
        chests.remove(owner);
        players.remove(owner);
    }

    /**
     * Drains the dirty sets into a {@link DirtyBatch}: every drained chest key becomes an upsert (row
     * still present) or a delete (row gone), and every drained player key its current row. {@code only}
     * non-null restricts the drain to those owners (the quit-path group flush). The returned snapshots
     * let a failed flush re-mark the exact drained keys dirty via {@link #restoreDirty}.
     */
    DirtyBatch collectDirty(@Nullable Set<UUID> only) {
        List<RawChestRow> chestUpserts = new ArrayList<>();
        List<ChestKey> chestDeletes = new ArrayList<>();
        List<ChestId> chestSnapshot = new ArrayList<>();
        List<RawPlayerRow> playerRows = new ArrayList<>();
        List<UUID> playerSnapshot = new ArrayList<>();
        if (only != null) {
            // Targeted drain (quit-path group flush): touch only the requested owners' entries.
            for (UUID owner : only) {
                Set<Integer> indices = dirtyChests.remove(owner);
                if (indices != null) {
                    drainOwnerChests(owner, indices, chestUpserts, chestDeletes, chestSnapshot);
                }
                if (dirtyPlayers.remove(owner)) {
                    drainPlayer(owner, playerRows, playerSnapshot);
                }
            }
        } else {
            for (Iterator<Map.Entry<UUID, Set<Integer>>> it = dirtyChests.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<UUID, Set<Integer>> e = it.next();
                it.remove();
                drainOwnerChests(e.getKey(), e.getValue(), chestUpserts, chestDeletes, chestSnapshot);
            }
            for (Iterator<UUID> it = dirtyPlayers.iterator(); it.hasNext(); ) {
                UUID uuid = it.next();
                it.remove();
                drainPlayer(uuid, playerRows, playerSnapshot);
            }
        }
        return new DirtyBatch(chestUpserts, chestDeletes, chestSnapshot, playerRows, playerSnapshot);
    }

    private void drainOwnerChests(UUID owner, Set<Integer> indices, List<RawChestRow> chestUpserts,
                                  List<ChestKey> chestDeletes, List<ChestId> chestSnapshot) {
        for (int index : indices) {
            chestSnapshot.add(new ChestId(owner, index));
            ChestRow row = row(owner, index);
            if (row == null) {
                chestDeletes.add(new ChestKey(owner.toString(), index));
            } else {
                chestUpserts.add(toRaw(owner, index, row));
            }
        }
    }

    private void drainPlayer(UUID owner, List<RawPlayerRow> playerRows, List<UUID> playerSnapshot) {
        playerSnapshot.add(owner);
        PlayerRow p = players.get(owner);
        if (p != null) {
            playerRows.add(new RawPlayerRow(owner.toString(), p.username,
                    p.editMode ? 1 : 0, p.appliedDefaultSize));
        }
    }

    /** Re-marks a drained batch dirty after a failed flush, so the next autosave retries it. */
    void restoreDirty(DirtyBatch batch) {
        for (ChestId id : batch.chestSnapshot()) {
            markChestDirty(id.owner(), id.index());
        }
        dirtyPlayers.addAll(batch.playerSnapshot());
    }
}
