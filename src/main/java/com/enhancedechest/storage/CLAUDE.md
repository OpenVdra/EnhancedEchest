# storage/

The persistence layer: a lazy per-owner write-back cache in front of a narrow SQL backend, plus the
schema and its migrator. Callers only ever see `EnderChestStorage`; nothing outside this package holds
a `StorageBackend` reference.

## Files

| File | Responsibility |
|---|---|
| `EnderChestStorage` | The interface everyone else programs against. **Synchronous and thread-agnostic** |
| `CachedStorage` | The implementation everyone gets: a thin façade holding the ender-chest domain logic |
| `OwnerResidencyCache` | The coherence engine: the single lock, `withOwner` load-on-miss, flush, evict, pin/unpin |
| `ChestCacheState` | Pure in-memory row model + dirty tracking. Every method runs under the engine's lock |
| `StorageBackend` | The narrow SQL contract: init/close, backup, import, per-owner reads, whole-DB reads, batched flush |
| `StorageFactory` | Picks the backend from `database.type` |
| `AutosaveService` | Periodic async flush + idle eviction; the per-quitter write-back |
| `sql/AbstractSqlStorage` | All portable SQL: reads and the batched flush. **No per-row write DML** |
| `sql/SqliteStorage`, `MysqlStorage`, `PostgresStorage` | Only the dialect bits: `CREATE TABLE` DDL, pool config, upsert syntax |
| `sql/SchemaMigrator` | Legacy table renames, versioned forward-only column steps, index creation |

## Ownership is row existence

A player owns chest `index` iff a row exists for `(player_uuid, chest_index)`. There is no owners
table, no soft delete. `saveChest` on a deleted row is a no-op by design.

## The residency invariant (load-bearing)

`CachedStorage` loads **one player's rows on first touch** and is authoritative for that owner
afterwards. `init()` creates the schema and runs the migrator — it does **not** bulk-load.

- An owner's rows exist in memory **iff** the owner is in the `resident` set. Every per-owner method
  runs through `withOwner`, which re-checks residency and runs the operation **in the same lock hold**,
  so an eviction can never interleave between load and op.
- Corollaries: **dirty ⇒ resident**, and eviction takes only **clean** owners. So a non-resident
  owner's SQL rows are always current — which is what lets `findExpired` / `findUuidByName` trust the
  backend for non-resident owners.
- Concurrent misses on one owner collapse into a single backend read (the `loading` future map).
- Every method keeps **identical semantics** to the SQL implementation it replaced: index allocation,
  primary fallback, transfer collision rules, targeted settings upserts. Writes mark the row dirty.

Who loads an owner: normally the join prefetch (the settings preload in `PlayerSettingsListener`
materializes the whole owner), otherwise on demand inside whatever call missed — an admin command on an
offline player, an expiry sweep, a migration.

Who writes back:

| Path | When |
|---|---|
| `AutosaveService` timer | `database.autosave-interval` (default `3m`, min 30s, reload-safe): `flush()` then `evictIdle()` (offline + unpinned + clean) |
| `flushQuitterLater` | ~5s after a quit — delayed so the close-save of a chest open at quit lands first; a rejoin re-pins and the eviction declines |
| `CachedStorage.close()` | The final full flush at shutdown, after the session flush and the executor shutdown |
| `backup(Path)` and `importRows` | Both **flush first** so the snapshot / import sees every in-memory change |

`flush()` snapshots dirty rows under the lock and writes them **outside** it. `flushDirty` on
`AbstractSqlStorage` writes chests **and** players in one connection acquisition, one transaction, one
commit, using the dialect's native upsert. Row presence at flush time decides upsert vs `DELETE`. A
failed flush re-marks the rows dirty and retries on the next autosave. Dirty keys are tracked per owner
(`Map<UUID, Set<Integer>>`), so the per-quit `isClean` check is O(1).

Consequences: memory stays proportional to the online-player count; gameplay open/close costs **zero
queries** once a player is loaded; a hard crash loses at most one autosave interval, and only for
players who were online the whole time. **Cross-server sharing is unsupported by default** — see
[../crossserver/CLAUDE.md](../crossserver/CLAUDE.md) for the mode that makes it safe.

Whole-database questions still go to the backend: `findExpired` takes the backend's candidates, loads
each candidate owner, and lets the authoritative in-memory row decide; `countChests`, `findUuidByName`
and `loadAllPlayerNames` flush first and then trust SQL wholesale, so a rename resolves with no
autosave lag.

## Schema: `<prefix>enderchests` (default `echest_enderchests`)

| Column | Notes |
|---|---|
| `player_uuid` | part of PK |
| `chest_index` | part of PK; 1-based per player — **it is the number the player reads on the chest**, so a permanent chest is created at the **lowest free** index (`ChestCacheState.lowestFreeIndex`) and a delete frees that number. Temp chests alone are appended at `max+1` so they sort last |
| `size` | slot count, multiple of 9, 9–54 |
| `custom_name` | nullable; null → default numbered title |
| `is_primary` | the player's chosen main; **zero or one** per player, set only by "Set as main" |
| `container_data` | nullable serialized bytes (`serialization/ContainerCodec`) |
| `migrated` | flag, meaningful on chest #1 only |
| `last_updated` | write timestamp |
| `kind` | `0` NORMAL, `1` TEMP (overflow), `2` PERM (permission-granted) |
| `expires_at` | nullable epoch-ms; `NULL` = never. Scanned every sweep, so `ensureIndexes` best-effort-creates `idx_enderchests_expires` on every start — without it the scan reads most of the DB file on SQLite |
| `icon` | nullable material key (`minecraft:diamond`); `NULL` = default |

Key operations: `createChest` (next free index, optional `expiresAt`, **never** auto-primary),
`createPermChest`, `ensureChest` (fixed index, migration only), `resizeChest`, `deleteChest` (**no**
survivor promotion), `renameChest`, `setIcon`, `setPrimary` (clear-then-set — the only way a chest
becomes primary), `clearPrimary`, `isMigrated`/`setMigrated`, `spillShrink`/`spillRemove`, `reclaimTemp`,
`transferChests`, `findExpired`. `getPrimaryIndex` filters `kind != TEMP`, prefers the flagged main and
otherwise falls back to the lowest non-temp index — so both NORMAL and PERM chests can be opened by
`/ec` and set as main.

## Schema: `<prefix>players` (default `echest_players`)

One row per player, separate from `enderchests` because it is per-player, not per-chest. Wide table,
one typed column per setting (not EAV/JSON) — fast, type-safe, DB-level defaults.

| Column | Notes |
|---|---|
| `player_uuid` | PK |
| `username` | nullable; last recorded name, backing offline `/ee view` name→UUID resolution |
| `edit_mode` | remembers whether `/eclist` opens in edit mode across sessions |
| `applied_default_size` | base-chest size dictated by `enhancedechest.default_size.<size>`, `0` when not permission-managed |
| `last_online` | epoch-ms of the player's last join/quit, `0` if never recorded. Decides how long they stay in admin `<player>` suggestions (`commands.suggest-offline-within`) |

`edit_mode` and `applied_default_size` map to the `PlayerSettings` record, loaded and saved **whole**,
never null (an absent row reads as `PlayerSettings.defaults()`). `saveSettings` deliberately **excludes**
`username` and `last_online` — both are written only by `recordPlayerSeen`, so a save built from a stale
copy can never clobber a name or timestamp recorded since. `last_online` is deliberately **not** on
`PlayerSettings` at all: nothing reads it per-player, only `loadAllPlayerNames` reads it in bulk to seed
the name index.

`recordPlayerSeen` is called on join and quit (`PlayerSettingsListener`) and from
`ChestOpener.reconcileForOpen` when the loaded name is stale. It only mutates the resident row and marks
it dirty, so calling it twice a session costs no extra statement — the write rides the next batched
flush. That is what keeps the name index complete **without ever reading the `playerdata` folder**; see
[../service/CLAUDE.md](../service/CLAUDE.md).

**To add a setting:** a component on `PlayerSettings`, a column in all three DDLs **plus** a
`SchemaMigrator` step, a field on `RawPlayerRow` mapped in `loadAllPlayers`/`batchPlayers`, and the
in-memory `PlayerRow` handling in `CachedStorage`.

## SQL layer rules

- **All SQL is portable except the flush upserts and the DDL.** The flush uses the dialect's native
  upsert (`ON CONFLICT … DO UPDATE` on SQLite/Postgres, `ON DUPLICATE KEY UPDATE` on MySQL/MariaDB),
  built once from the `UpsertSyntax` enum the subclass passes in. The verbatim `/ee import` copy keeps a
  plain `INSERT` — a duplicate key must fail the import.
- **All row-level semantics live in `CachedStorage`**, not in SQL. `AbstractSqlStorage` holds no per-row
  write DML.
- **Table prefix** (`database.table-prefix`, default `echest_`): `PluginConfig.getTablePrefix()`
  sanitizes it to `[A-Za-z0-9_]` because table names are concatenated into SQL and cannot be bound as
  parameters. Every `SQL_*` statement is an **instance** field built in the constructor, not a
  `static final` constant.
- **`SchemaMigrator.renameLegacyTables` runs first**, before the `CREATE TABLE IF NOT EXISTS`
  statements — a CREATE would otherwise materialize an empty prefixed table and the rename would fail.
  It is idempotent (`tableExists(old) && !tableExists(new)`) and its failures are logged and swallowed
  per table, never fatal. `migrate` then runs the versioned steps (guarded by `columnExists` /
  `tableExists`), which no-op on a fresh install.
- **SQLite runs in WAL mode** with `synchronous=NORMAL`, and its `connectionTimeout` is a deliberate
  **30s**: the backup's `VACUUM INTO` holds the single connection for the whole snapshot, and an
  autosave flush landing mid-backup must ride it out rather than time out unwritten.

## Serialization and backup

`serialization/ContainerCodec` converts `ItemStack[] ⇄ byte[]`, parameterized by chest size on decode.
Stored bytes are `[1-byte version tag] + [body]`: **0x02** (current) is
`ItemStack.serializeItemsAsBytes`; **0x01** (legacy, read-only) is a shulker-box "vehicle" carrying a
`CONTAINER` data component. **Keep the 0x01 branch** — removing it orphans rows untouched since the
upgrade; they re-save as 0x02 on the next close. Decode failures throw `CodecException`, which the
service surfaces as `chest.codec-failed` and refuses to open rather than risk clobbering stored data.

`backup/BackupService` takes scheduled snapshots via `EnderChestStorage.backup(Path)` (which flushes
first) and prunes `backup.folder` to the newest `backup.keep` files. Backup is a **capability**, not a
guarantee: `supportsBackup()` is false by default and only `SqliteStorage` overrides it, using
`VACUUM INTO` — never copy the raw `.db` file. For MySQL/MariaDB/PostgreSQL the service logs one warning
and stays idle; those are backed up with the DB server's own tooling.
