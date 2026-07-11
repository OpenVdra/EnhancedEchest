package com.enhancedechest.storage;

import com.enhancedechest.model.ChestKind;
import com.enhancedechest.model.ChestSummary;
import com.enhancedechest.model.EnderChestData;
import com.enhancedechest.model.PlayerSettings;
import com.enhancedechest.storage.ChestCacheState.ChestRow;
import com.enhancedechest.storage.ChestCacheState.RowSnap;
import com.enhancedechest.telemetry.Telemetry;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Lazy-loading write-back cache over the SQL backend, and the {@link EnderChestStorage} the rest of
 * the plugin sees. A player's rows are read from SQL once, on first touch (normally the join
 * prefetch; otherwise on demand inside whatever storage call missed — an admin command on an offline
 * player, an expiry sweep, a migration), and every operation after that is served from memory with
 * identical semantics to the old SQL (index allocation, primary fallback, transfer collision rules,
 * targeted settings upserts).
 *
 * <p>This class is a thin façade holding only the ender-chest domain logic; the machinery is split
 * into two collaborators in this package:
 * <ul>
 *   <li>{@link ChestCacheState} — the pure in-memory row model (materialized rows + dirty tracking +
 *       the low-level map operations these methods build on).</li>
 *   <li>{@link OwnerResidencyCache} — the residency/flush/eviction coherence engine (the single lock,
 *       load-on-miss protocol, write-back, and idle eviction). Every per-owner method below runs its
 *       body through {@link OwnerResidencyCache#withOwner} so the owner is guaranteed resident for
 *       the duration; see that class for the load-bearing residency invariant and lock ordering.</li>
 * </ul>
 *
 * <p><b>Consequence — no cross-server sharing:</b> while an owner is resident this cache is
 * authoritative and the quit write-back is delayed, so two servers pointed at the same database can
 * still overwrite each other on fast server switches. Running multiple servers against one database
 * remains unsupported.
 */
public final class CachedStorage implements EnderChestStorage {

    private final StorageBackend backend;
    private final Logger logger;
    private final Telemetry telemetry;
    private final ChestCacheState state = new ChestCacheState();
    private final OwnerResidencyCache cache;

    public CachedStorage(StorageBackend backend, Logger logger, Telemetry telemetry) {
        this.backend = backend;
        this.logger = logger;
        this.telemetry = telemetry;
        this.cache = new OwnerResidencyCache(backend, state, logger);
    }

    // ---- lifecycle ----

    /** Initializes the SQL backend (schema + migrations). Player data is loaded lazily, per owner. */
    @Override
    public void init() {
        backend.init();
        logger.info("Storage ready — {} ender chest row(s) in the database; "
                + "player data is loaded into memory on join / first use", backend.countChests());
    }

    /** Flushes everything still dirty, then closes the SQL backend. */
    @Override
    public void close() {
        try {
            cache.flush();
        } catch (Exception e) {
            logger.error("Final flush to the database failed on shutdown — recent changes may be lost", e);
            telemetry.error(e, "storage.shutdown-flush");
        }
        backend.close();
    }

    // ---- online lifecycle (join/quit listener) + write-back scheduling (AutosaveService) ----

    /** Marks an owner online: their rows survive every eviction until {@link #unpin}. */
    public void pin(UUID owner) {
        cache.pin(owner);
    }

    /** Unmarks an owner as online. Their rows stay resident until flushed clean and evicted. */
    public void unpin(UUID owner) {
        cache.unpin(owner);
    }

    /** Writes every dirty row back to SQL. @return rows written/deleted (0 when nothing was dirty). */
    public int flush() {
        return cache.flush();
    }

    /** Writes one quitter's dirty rows back, then evicts them if clean and still offline. */
    public void flushOwner(UUID owner) {
        cache.flushOwner(owner);
    }

    /** Evicts every owner that is offline and fully flushed. @return the number evicted. */
    public int evictIdle() {
        return cache.evictIdle();
    }

    // ---- backup & import (the paths that read the SQL side directly: flush first) ----

    @Override
    public boolean supportsBackup() {
        return backend.supportsBackup();
    }

    @Override
    public void backup(Path target) throws Exception {
        flush();                                   // the snapshot must contain every in-memory change
        backend.backup(target);
    }

    @Override
    public int[] importRows(List<RawPlayerRow> players, List<RawChestRow> chests) {
        // Sync pending deletes first so the verbatim inserts cannot collide with stale backend rows,
        // then write the import into SQL. Only owners already materialized need their cache refreshed
        // (not dirtied — the rows are already persisted); everyone else lazy-loads the imported rows
        // straight from SQL on first touch.
        flush();
        int[] counts = backend.importRows(players, chests);
        cache.runLocked(() -> {
            for (RawChestRow c : chests) {
                if (cache.isResidentLocked(UUID.fromString(c.playerUuid()))) {
                    state.applyRawChest(c);
                }
            }
            for (RawPlayerRow p : players) {
                if (cache.isResidentLocked(UUID.fromString(p.playerUuid()))) {
                    state.applyRawPlayer(p);
                }
            }
            return null;
        });
        return counts;
    }

    @Override
    public long countChests() {
        flush();                                   // pending creates/deletes must be visible to the count
        return backend.countChests();
    }

    // ---- chest reads ----

    @Override
    public List<ChestSummary> listChests(UUID owner) {
        return cache.withOwner(owner, () -> {
            TreeMap<Integer, ChestRow> owned = state.ownedRowsOrNull(owner);
            if (owned == null) {
                return List.of();
            }
            List<ChestSummary> result = new ArrayList<>(owned.size());
            owned.forEach((index, row) -> result.add(new ChestSummary(index, row.size, row.customName,
                    row.primary, ChestKind.fromCode(row.kind), row.expiresAt, row.icon)));
            return result;
        });
    }

    @Override
    public int getPrimaryIndex(UUID owner) {
        // Mirrors SQL_PRIMARY: TEMP chests excluded; the flagged chest wins, else the lowest index.
        return cache.withOwner(owner, () -> {
            TreeMap<Integer, ChestRow> owned = state.ownedRowsOrNull(owner);
            if (owned == null) {
                return -1;
            }
            int lowest = -1;
            for (Map.Entry<Integer, ChestRow> e : owned.entrySet()) {
                if (e.getValue().kind == ChestKind.TEMP.code()) continue;
                if (e.getValue().primary) return e.getKey();
                if (lowest == -1) lowest = e.getKey();
            }
            return lowest;
        });
    }

    @Override
    public @Nullable EnderChestData loadChest(UUID owner, int index) {
        return cache.withOwner(owner, () -> {
            ChestRow row = state.row(owner, index);
            if (row == null) {
                return null;
            }
            return new EnderChestData(owner, index, row.size, row.customName, row.data,
                    ChestKind.fromCode(row.kind), row.expiresAt, row.icon);
        });
    }

    @Override
    public List<ExpiredRef> findExpired(long now) {
        // Two authoritative sources, and — crucially — no blob loads. For a NON-resident owner the
        // backend row is current (residency invariant: non-resident ⇒ clean ⇒ SQL up to date), so its
        // expiry candidates are trusted verbatim without ever materializing that owner's chests. For a
        // RESIDENT owner the in-memory row is authoritative (it may be dirty and ahead of SQL), so those
        // come from the cache scan and the backend's candidates for them are ignored (residency is
        // stable within the lock, so the two sets never overlap — the state only holds resident owners).
        // This avoids pulling every offline candidate's full chest rows + item blobs onto the heap each
        // sweep, and with no load there is no load-then-evict race to miss a beat.
        List<StorageBackend.ExpiredKey> candidates = backend.findExpired(now);
        return cache.runLocked(() -> {
            List<ExpiredRef> result = new ArrayList<>();
            for (StorageBackend.ExpiredKey key : candidates) {
                UUID owner = UUID.fromString(key.playerUuid());
                if (!cache.isResidentLocked(owner)) {              // non-resident ⇒ backend is current
                    result.add(new ExpiredRef(owner, key.chestIndex(), ChestKind.fromCode(key.kind())));
                }
            }
            state.forEachRow((owner, index, row) -> {              // resident ⇒ memory wins
                if (row.expiresAt != null && row.expiresAt <= now) {
                    result.add(new ExpiredRef(owner, index, ChestKind.fromCode(row.kind)));
                }
            });
            return result;
        });
    }

    // ---- chest writes ----

    @Override
    public void saveChest(UUID owner, int index, byte[] containerData) {
        cache.mutateOwner(owner, () -> {
            ChestRow row = state.row(owner, index);
            if (row == null) {
                return;                            // row deleted while open — same no-op as the SQL UPDATE
            }
            row.data = containerData;
            row.lastUpdated = System.currentTimeMillis();
            state.markChestDirty(owner, index);
        });
    }

    @Override
    public int createChest(UUID owner, int size, @Nullable Long expiresAt) {
        return cache.withOwner(owner, () -> {
            int newIndex = state.maxIndex(owner) + 1;
            state.insertRow(owner, newIndex, size, null, false, null, false, ChestKind.NORMAL.code(),
                    expiresAt, null);
            return newIndex;
        });
    }

    @Override
    public int createPermChest(UUID owner, int size) {
        return cache.withOwner(owner, () -> {
            int newIndex = state.maxIndex(owner) + 1;
            state.insertRow(owner, newIndex, size, null, false, null, false, ChestKind.PERM.code(),
                    null, null);
            return newIndex;
        });
    }

    @Override
    public void ensureChest(UUID owner, int index, int size) {
        cache.mutateOwner(owner, () -> {
            if (state.row(owner, index) != null) {
                return;
            }
            state.insertRow(owner, index, size, null, false, null, false, ChestKind.NORMAL.code(),
                    null, null);
        });
    }

    @Override
    public void resizeChest(UUID owner, int index, int size) {
        cache.mutateOwner(owner, () -> {
            ChestRow row = state.row(owner, index);
            if (row == null) {
                return;
            }
            row.size = size;
            state.markChestDirty(owner, index);
        });
    }

    @Override
    public void deleteChest(UUID owner, int index) {
        cache.mutateOwner(owner, () -> state.removeRow(owner, index));
    }

    @Override
    public void clearChestContents(UUID owner, int index) {
        cache.mutateOwner(owner, () -> {
            ChestRow row = state.row(owner, index);
            if (row == null) {
                return;
            }
            row.data = null;
            row.lastUpdated = System.currentTimeMillis();
            state.markChestDirty(owner, index);
        });
    }

    @Override
    public void spillShrink(UUID owner, int index, int newSize, byte[] visible,
                            @Nullable byte[] overflow, int tempSize, long tempExpiresAt) {
        cache.mutateOwner(owner, () -> {
            ChestRow row = state.row(owner, index);
            if (row != null) {
                row.size = newSize;
                row.data = visible;
                row.lastUpdated = System.currentTimeMillis();
                state.markChestDirty(owner, index);
            }
            if (overflow != null) {
                state.insertTempRow(owner, tempSize, overflow, tempExpiresAt);
            }
        });
    }

    @Override
    public void spillRemove(UUID owner, int index, @Nullable byte[] items, int tempSize, long tempExpiresAt) {
        cache.mutateOwner(owner, () -> {
            if (items != null) {
                state.insertTempRow(owner, tempSize, items, tempExpiresAt);
            }
            state.removeRow(owner, index);
        });
    }

    @Override
    public int transferChests(UUID from, UUID to, @Nullable Integer onlyIndex,
                              Set<Integer> preserveDestIndices, long tempExpiresAt) {
        return cache.withOwners(from, to, () -> {
            // Snapshot of the source NORMAL chests in scope, ordered by index.
            List<RowSnap> src = state.snapshotRows(from, onlyIndex, true);
            if (src.isEmpty()) {
                return 0;
            }

            // Snapshot of every destination row before any mutation (scope, collisions, preserved items).
            List<RowSnap> dest = state.snapshotRows(to, null, false);

            int destMax = dest.isEmpty() ? 0 : dest.get(dest.size() - 1).index();
            int srcMax = src.get(src.size() - 1).index();
            // Temp/relocated rows go above everything so they can never collide with an incoming
            // source index (all <= srcMax) or an existing destination index.
            int freeIndex = Math.max(destMax, srcMax) + 1;

            Set<Integer> srcIndices = new HashSet<>();
            for (RowSnap r : src) srcIndices.add(r.index());

            // Destination NORMAL chests this transfer replaces: the one at onlyIndex, or all of them.
            List<RowSnap> destScope = new ArrayList<>();
            for (RowSnap d : dest) {
                if (d.kind() != ChestKind.NORMAL.code()) continue;
                if (onlyIndex == null || d.index() == onlyIndex.intValue()) destScope.add(d);
            }

            // 1. Preserve flagged destination items as temp chests before they are removed.
            for (RowSnap d : destScope) {
                if (preserveDestIndices.contains(d.index()) && d.data() != null) {
                    state.insertRow(to, freeIndex++, d.size(), null, false, d.data(),
                            false, ChestKind.TEMP.code(), tempExpiresAt, null);
                }
            }

            // 2. Remove the destination NORMAL chests in scope.
            Set<Integer> removed = new HashSet<>();
            for (RowSnap d : destScope) {
                state.removeRow(to, d.index());
                removed.add(d.index());
            }

            // 3. On a full transfer the copied source primary becomes the only main; clear any old flag.
            if (onlyIndex == null) {
                state.clearPrimary(to);
            }

            // 4. Relocate any surviving destination row (PERM/TEMP, or an out-of-scope NORMAL on a
            //    single-index transfer) that still sits on an index the source is about to occupy.
            TreeMap<Integer, ChestRow> toOwned = state.ownedRows(to);
            for (RowSnap d : dest) {
                if (removed.contains(d.index())) continue;
                if (srcIndices.contains(d.index())) {
                    ChestRow moving = toOwned.remove(d.index());
                    if (moving != null) {
                        toOwned.put(freeIndex, moving);
                        state.markChestDirty(to, d.index());
                        state.markChestDirty(to, freeIndex);
                        freeIndex++;
                    }
                }
            }

            // 5. Write the source chests onto the destination at their original indices (NORMAL,
            //    never-expiring, migrated flag reset — same as the SQL full-insert).
            for (RowSnap r : src) {
                boolean primary = onlyIndex == null && r.primary();
                state.insertRow(to, r.index(), r.size(), r.customName(), primary, r.data(), false,
                        ChestKind.NORMAL.code(), null, r.icon());
            }

            // 6. Remove the source chests (this is a move, not a copy — no duplicate items).
            for (RowSnap r : src) {
                state.removeRow(from, r.index());
            }

            return src.size();
        });
    }

    @Override
    public void renameChest(UUID owner, int index, @Nullable String name) {
        cache.mutateOwner(owner, () -> {
            ChestRow row = state.row(owner, index);
            if (row == null) {
                return;
            }
            row.customName = name;
            state.markChestDirty(owner, index);
        });
    }

    @Override
    public void setIcon(UUID owner, int index, @Nullable String icon) {
        cache.mutateOwner(owner, () -> {
            ChestRow row = state.row(owner, index);
            if (row == null) {
                return;
            }
            row.icon = icon;
            state.markChestDirty(owner, index);
        });
    }

    @Override
    public void setPrimary(UUID owner, int index) {
        cache.mutateOwner(owner, () -> {
            state.clearPrimary(owner);
            ChestRow row = state.row(owner, index);
            if (row != null) {
                row.primary = true;
                state.markChestDirty(owner, index);
            }
        });
    }

    @Override
    public void clearPrimary(UUID owner) {
        cache.mutateOwner(owner, () -> state.clearPrimary(owner));
    }

    @Override
    public boolean isMigrated(UUID owner) {
        return cache.withOwner(owner, () -> {
            ChestRow row = state.row(owner, 1);
            return row != null && row.migrated;
        });
    }

    @Override
    public void setMigrated(UUID owner, boolean migrated) {
        cache.mutateOwner(owner, () -> {
            ChestRow row = state.row(owner, 1);
            if (row == null) {
                return;
            }
            row.migrated = migrated;
            state.markChestDirty(owner, 1);
        });
    }

    @Override
    public void completeMigration(UUID owner, byte[] containerData,
                                  @Nullable byte[] overflow, int tempSize, long tempExpiresAt) {
        cache.mutateOwner(owner, () -> {
            ChestRow row = state.row(owner, 1);
            if (row != null) {
                row.data = containerData;
                row.migrated = true;
                row.lastUpdated = System.currentTimeMillis();
                state.markChestDirty(owner, 1);
            }
            if (overflow != null) {
                state.insertTempRow(owner, tempSize, overflow, tempExpiresAt);
            }
        });
    }

    // ---- player settings & name index ----

    @Override
    public PlayerSettings loadSettings(UUID owner) {
        return cache.withOwner(owner, () -> {
            ChestCacheState.PlayerRow p = state.playerRowOrNull(owner);
            if (p == null) {
                return PlayerSettings.defaults();
            }
            return new PlayerSettings(p.editMode, p.appliedDefaultSize, p.username);
        });
    }

    @Override
    public void saveSettings(UUID owner, PlayerSettings settings) {
        // Whole-object save of editMode/appliedDefaultSize; username stays whatever the row holds
        // (written only via upsertPlayerName), preserving the SQL contract.
        cache.mutateOwner(owner, () -> {
            ChestCacheState.PlayerRow p = state.playerRow(owner);
            p.editMode = settings.editMode();
            p.appliedDefaultSize = settings.appliedDefaultSize();
            state.markPlayerDirty(owner);
        });
    }

    @Override
    public void setEditMode(UUID owner, boolean editMode) {
        cache.mutateOwner(owner, () -> {
            state.playerRow(owner).editMode = editMode;
            state.markPlayerDirty(owner);
        });
    }

    @Override
    public int getAppliedDefaultSize(UUID owner) {
        return cache.withOwner(owner, () -> {
            ChestCacheState.PlayerRow p = state.playerRowOrNull(owner);
            return p == null ? 0 : p.appliedDefaultSize;
        });
    }

    @Override
    public void setAppliedDefaultSize(UUID owner, int size) {
        cache.mutateOwner(owner, () -> {
            state.playerRow(owner).appliedDefaultSize = size;
            state.markPlayerDirty(owner);
        });
    }

    @Override
    public void upsertPlayerName(UUID owner, String name) {
        cache.mutateOwner(owner, () -> {
            state.playerRow(owner).username = name;
            state.markPlayerDirty(owner);
        });
    }

    @Override
    public @Nullable UUID findUuidByName(String name) {
        // Flush pending renames so SQL is authoritative, then let the backend answer — the same
        // flush-then-backend pattern as backup/countChests/importRows. This restores the exact
        // pre-cache semantics: a rename overwrites the username column, so the *old* name stops
        // resolving at once, instead of lingering for up to one autosave interval while the dirty
        // row sat in memory. Runs on the async storage executor, so the flush JDBC is off the
        // region thread.
        flush();
        String uuid = backend.findUuidByName(name);
        return uuid == null ? null : UUID.fromString(uuid);
    }

    @Override
    public Map<UUID, String> loadAllPlayerNames() {
        // Startup name-index load: flush first so the backend scan already reflects every rename,
        // then trust it wholesale (no manual overlay), matching the other whole-table reads.
        flush();
        Map<UUID, String> names = new HashMap<>();
        for (RawPlayerRow p : backend.loadAllPlayers()) {
            if (p.username() != null) {
                names.put(UUID.fromString(p.playerUuid()), p.username());
            }
        }
        return names;
    }
}
