# Storage, schema, settings cache & serialization

## Storage layer

`EnderChestStorage` models ownership as **row existence**: a player owns chest `index` iff a row exists
for `(player_uuid, chest_index)`. There is no separate "owners" table. All methods are **synchronous**
and thread-agnostic — the `com.enhancedechest.service` layer is the only caller and dispatches them onto
the shared `DbExecutor` async pool (see [concurrency-and-dupe-safety.md](concurrency-and-dupe-safety.md)).

### Lazy write-back cache (`CachedStorage`) — authoritative for resident owners

The `EnderChestStorage` the rest of the plugin sees is **`CachedStorage`**, a decorator over the SQL
backend that loads **one player's rows on first touch** and serves everything from memory after that.
It is split into three collaborators in the `storage` package: `CachedStorage` itself is now a thin
façade holding only the ender-chest domain logic; the coherence engine (the single lock, the
`withOwner` load-on-miss protocol, `flush`/`flushOwner`/`evictIdle`, `pin`/`unpin`) lives in
**`OwnerResidencyCache`**, and the pure in-memory row model + dirty tracking (the `chests`/`players`
maps, `dirtyChests`/`dirtyPlayers`, and every low-level row op) lives in **`ChestCacheState`**, whose
methods all run under the engine's lock. The behaviour below is unchanged by that split:

- `init()` initializes the SQL backend (schema + `SchemaMigrator`) only — no bulk load. A player's
  `enderchests` + `players` rows are read once by `loadOwner` (backend `loadChests`/`loadPlayer`) on
  the first storage call that touches them: normally the join prefetch (the settings preload in
  `PlayerSettingsListener` materializes the whole owner), otherwise on demand inside whatever call
  missed — an admin command on an offline player, an expiry sweep, a migration.
- **Residency invariant (load-bearing):** an owner's rows exist in memory iff the owner is in the
  `resident` set. Every per-owner method runs through `withOwner`, which re-checks residency and runs
  the operation **in the same lock hold**, so an eviction can never interleave between load and op.
  Corollaries: **dirty ⇒ resident**, and eviction takes only **clean** owners — so a non-resident
  owner's SQL rows are always current (this is what lets `findExpired`/`findUuidByName` trust the
  backend for non-resident owners). Concurrent misses on one owner collapse to a single backend read
  (the `loading` future map).
- Every interface method keeps **identical semantics** to the SQL implementation it replaced — index
  allocation (`max+1`), primary fallback, the transfer collision rules, targeted settings upserts,
  `saveChest` being a no-op on a deleted row, and so on. Writes mark the row **dirty**.
- `flush()` snapshots dirty rows under the lock, then writes them to SQL **outside** it —
  `flushDirty` on `AbstractSqlStorage` writes chests **and** players in one connection acquisition,
  one transaction, one commit, using the dialect's native upsert (`ON CONFLICT DO UPDATE` on
  SQLite/Postgres, `ON DUPLICATE KEY UPDATE` on MySQL/MariaDB — selected by the `UpsertSyntax` enum
  the subclass passes in). Row presence at flush time decides upsert vs `DELETE`. A failed flush
  re-marks the rows dirty and they retry on the next autosave. Dirty keys are tracked per owner
  (`Map<UUID, Set<Integer>>`), so the per-quit `isClean` check is O(1) and the quit-path group drain
  touches only the drained owners.
- Write-back + eviction is driven by **`AutosaveService`**: the periodic timer
  (`database.autosave-interval`, default `3m`, min 30s, reload-safe) calls `flush()` then `evictIdle()`
  (drops every owner that is offline/unpinned **and** clean); `flushQuitterLater` writes back and evicts
  one player ~5s after they quit (delayed so the close-save of a chest open at quit lands first; a
  rejoin re-pins them and the eviction declines); and **`CachedStorage.close()`** does the final full
  save at shutdown (ordered after `sessionManager.shutdown()` + `dbExecutor.shutdown()` in `onDisable`).
  The join/quit listener maintains the `pinned` set (online owners are never evicted).
- Whole-database questions go to the backend: `findExpired` takes the backend's expiry candidates,
  loads each candidate owner, and lets the authoritative in-memory row decide (offline players' temp
  chests still expire; a stale candidate whose expiry changed in memory is ignored); `countChests`,
  `findUuidByName` and `loadAllPlayerNames` (startup name index) **flush first**, then trust SQL
  wholesale — so a rename resolves (and the old name stops resolving) with no autosave-interval lag,
  matching the pre-cache direct-SQL semantics.
- The two paths that read the SQL side directly both **flush first**: `backup(Path)` (so the snapshot
  contains every in-memory change) and `importRows` (`/ee import`; imported rows refresh only owners
  already resident, not dirtied — everyone else lazy-loads them from SQL).

Consequences: memory stays **proportional to the online-player count** (plus owners touched by admin
commands, held at most one autosave interval); gameplay open/close costs zero queries once a player is
loaded; a hard crash can lose at most one autosave interval of changes — and only for players online
the whole time, since quitters are written back within seconds (the DB write on clean shutdown is
guaranteed). **Cross-server sharing is unsupported by default**: while an owner is resident the cache is
authoritative and the quit write-back is delayed, so fast server switches on a shared database can
overwrite each other. With `cross-server.enabled`, a Redis-backed
`com.enhancedechest.crossserver.CrossServerCoordinator` closes exactly that hole by adding a
distributed leg to the residency invariant — **resident ⇒ this server holds the owner's Redis lock**:
`loadOwner` blocks on `acquireOwner` before the backend read and re-checks `isHeld` under the cache
lock before flipping residency (a raced eviction release forces re-acquire + re-read); both eviction
paths release (`beginRelease` inside the same lock hold that drops the rows, `finishRelease` — the
network DEL + `rel` publish — outside it) and only ever evict flushed-clean owners, so the next
acquirer always reads current SQL. Handover latency is bounded by pub/sub `req` messages: the waiting
server re-asks every 500ms poll round and the holder flushes+evicts+releases as soon as the player is
unpinned with no live/pending chest session (`ChestSessionManager.hasActivity`). Locks are never
stolen — a timed-out acquire (10s) throws `CrossServerLockException`, surfacing like a failed SQL
read; crashed holders free up via the 30s TTL (heartbeat every 10s, with a split-brain SEVERE log if a
held key turns up foreign). `findExpired` skips non-resident candidates whose owner is locked by
another server (that server's own sweep covers them). Single-server installs run on
`CrossServerCoordinator.NOOP`, which collapses the whole protocol to no-ops.

The SQL side implements the deliberately narrow **`StorageBackend`** interface (init/close, backup,
`importRows`, per-player reads `loadChests`/`loadPlayer`, the whole-database reads
`findExpired`/`countChests`/`findUuidByName`/`loadAllPlayers`, and the batched
`flushChests`/`flushPlayers`) — nothing outside the cache layer holds a `StorageBackend` reference, and
`AbstractSqlStorage` holds **no per-row write DML**; all row-level semantics (index allocation `max+1`,
primary resolution, transfer collision rules, targeted settings upserts) live in `CachedStorage`. Only
`CREATE TABLE` statements are dialect-specific, injected by each subclass (`SqliteStorage`,
`MysqlStorage`, `PostgresStorage`) as a `String...` of DDL run in order by `init()` (currently
`<prefix>enderchests` + `<prefix>players`, see below). Connections come from a HikariCP pool (size 1 for
SQLite, configurable otherwise). `StorageFactory` picks the backend from `config.type`. The `DbExecutor`
dispatch convention is unchanged (services still never call storage on a region/main thread; cache-miss
JDBC runs on those executor threads), which is what keeps the dupe-safety ordering model intact without
modification.

### Table prefix (`database.table-prefix`, default `echest_`)

Every table this plugin creates is prefixed (`echest_enderchests`, `echest_players`,
`echest_schema_meta`) so the plugin's data is identifiable and safe to keep in a database shared with
other plugins. `PluginConfig.getTablePrefix()` sanitizes the configured value to `[A-Za-z0-9_]` (falling
back to `echest_` if that leaves nothing) since table names are concatenated directly into SQL — they
can't be bound as JDBC parameters. `AbstractSqlStorage` builds every `SQL_*` statement as an **instance**
field in its constructor from `<prefix>enderchests`/`<prefix>players` (no longer `static final`
constants); each dialect subclass formats its own `CREATE TABLE` DDL with the same prefix and passes it
through. `SourceDatabaseReader` (`/ee import`'s source-side reader) assumes the source shares the active
install's prefix, since both are the same plugin's schema.

`SchemaMigrator.renameLegacyTables(dataSource, prefix)` runs once at the very start of
`AbstractSqlStorage.init()`, **before** the CREATE TABLE IF NOT EXISTS statements: for each of
`enderchests`/`players`/`schema_meta` it renames the bare (pre-prefix) table to its prefixed name via the
portable `ALTER TABLE ... RENAME TO ...` (accepted by SQLite, MySQL/MariaDB and PostgreSQL alike), guarded
by `tableExists(old) && !tableExists(new)` so it is idempotent and a no-op once already prefixed. This
must run *before* CREATE, not after — the CREATE would otherwise materialize an empty table under the new
prefixed name first, and the rename target would then already exist and fail. Failures here are logged
and swallowed per table (never fatal), so a rename hiccup degrades to a fresh empty table rather than
blocking startup. The old 1.0.4 `player_settings` → `players` merge (`SchemaMigrator` version-1 `Step`)
now targets `<prefix>players` and still runs *after* the rename+CREATE, so a hypothetical pre-1.0.4
install jumping straight to a prefixed build merges into the already-prefixed (freshly created) table.

SQLite runs in **WAL mode** with `synchronous=NORMAL` (set as driver properties in
`SqliteStorage.buildConfig`, applied as PRAGMAs per connection; `journal_mode` also persists in the DB
file): readers aren't blocked by the writer and commits become WAL appends. Its `connectionTimeout` is a
deliberate **30s** — the backup's `VACUUM INTO` holds the single connection for the whole snapshot, and
an autosave flush that lands mid-backup must ride that out and then succeed rather than time out
unwritten.

`init()` also calls `SchemaMigrator.migrate(dataSource, prefix)` right after running the CREATE
statements: a versioned, forward-only migrator (`<prefix>schema_meta` table) that brings an existing
(older) database up to the current schema — additive column steps guarded by a JDBC-metadata
`columnExists` check, occasional table renames/merges guarded by `tableExists` (e.g. the 1.0.4
`player_settings` → `players` merge). A fresh install's CREATE statements already carry every column, so
the migrator's steps no-op on it. `ensureIndexes` (the `expires_at` index) also carries the prefix in both
the table and the index name, since SQLite/PostgreSQL require index names unique database-wide.

**Rule:** all SQL portable except the flush upserts and the DDL. The flush write-back deliberately
uses the dialect's native upsert (`ON CONFLICT (…) DO UPDATE SET col = excluded.col` on
SQLite/Postgres, `ON DUPLICATE KEY UPDATE col = VALUES(col)` on MySQL/MariaDB) — one B-tree mutation
and one batch execution per row instead of the two the old portable delete-then-insert paid; the
clause is built once in `AbstractSqlStorage` from the `UpsertSyntax` enum the subclass passes. The
verbatim `/ee import` copy keeps the plain `INSERT` (a duplicate key must fail the import).

## Schema: `enderchests` (table: `<prefix>enderchests`, default `echest_enderchests`)

| Column | Notes |
|--------|-------|
| `player_uuid` | part of PK |
| `chest_index` | part of PK; per-player 1-based index |
| `size` | slot count (multiple of 9, 9–54) |
| `custom_name` | nullable; null → default numbered title |
| `is_primary` | the player's chosen main; **zero or one** per player (set only by "Set as main") |
| `container_data` | nullable serialized bytes (`ContainerCodec`) |
| `migrated` | flag, meaningful on chest #1 only |
| `last_updated` | write timestamp |
| `kind` | `0` = NORMAL, `1` = TEMP (overflow), `2` = PERM (permission-granted) — see [expiry-and-temp-chests.md](expiry-and-temp-chests.md) and [commands-and-permissions.md](commands-and-permissions.md#permission-granted-chests) |
| `expires_at` | nullable epoch-ms expiry; `NULL` = never. Queried by the sweep's DB-side candidate scan (`findExpired`) every sweep, so `SchemaMigrator.ensureIndexes` best-effort-creates `idx_enderchests_expires` on every start (idempotent, reusing the historical index name; failures swallowed) — without it the scan is a full table scan, and on SQLite the inline blobs make that read most of the DB file each sweep on a large roster |
| `icon` | nullable material key (e.g. `minecraft:diamond`) of the list icon; `NULL` = default. Rendered as an Adventure sprite component in the dialogs |

Key operations (all served from memory by `CachedStorage`): `createChest` (next index, **never**
auto-primary; optional `expiresAt`), `createPermChest`
(next index, `kind = PERM`, no expiry, never auto-primary — used by the permission reconcile), `ensureChest`
(create at a fixed index if absent — migration only, also never auto-primary), `resizeChest`,
`deleteChest` (**no survivor promotion** — if the deleted chest was the main, the player simply has no
main until they pick one), `renameChest`, `setIcon`, `setPrimary` (clear-then-set — the
only way a chest becomes primary), `clearPrimary`, `isMigrated`/`setMigrated`, the item-moving
`spillShrink` / `spillRemove`, and the sweeper query `findExpired`. `saveChest` updates contents only
(a no-op on a deleted row) and never touches size, name, or primary.

Primary resolution (`getPrimaryIndex`) filters `kind != TEMP` (everything **except** TEMP) and prefers
the flagged main, otherwise the lowest-indexed non-temp chest. Both NORMAL and PERM chests are eligible
to be opened by `/ec` and set as the main; only temp chests are excluded.

## Schema: `players` (table: `<prefix>players`, default `echest_players`)

Per-player state, **one row per player** (`player_uuid` PK), separate from `enderchests` because it is
per-player, not per-chest. Wide table, one typed column per setting (not EAV/JSON) — fast, type-safe,
DB-level defaults. Named `players` rather than `player_settings` because it also carries identity data
(the name index), not just behavioural preferences — before 1.0.4 this was two tables (`player_settings`
+ `player_names`); the migrator merges them on upgrade (see above).

| Column | Notes |
|--------|-------|
| `player_uuid` | PK |
| `username` | nullable; the player's name as last recorded, backing offline `/ee view` name→UUID resolution. Column 2 on a fresh install; an upgraded database keeps it physically wherever the migrator's `ALTER TABLE ADD COLUMN` landed it (always the end — no portable "add after X"), which is harmless since every DML here addresses columns by name |
| `edit_mode` | bool (0/1, default 0) — remembers whether `/eclist` opens in edit mode across sessions |
| `applied_default_size` | the base-chest size dictated by `enhancedechest.default_size.<size>`, or `0` when not permission-managed — see [commands-and-permissions.md](commands-and-permissions.md#permission-granted-chests) |

Mapped to the `PlayerSettings` record (loaded/saved **whole**, never null — an absent row reads as
`PlayerSettings.defaults()`). `saveSettings` (whole object) and `setEditMode`/`setAppliedDefaultSize`
(single targeted field) are targeted upserts on the in-memory row — `saveSettings` deliberately excludes
`username`, which is written only by the separate `upsertPlayerName`/`findUuidByName` pair, so a save built
from a stale `PlayerSettings` copy can never clobber a name recorded since it was loaded.

`upsertPlayerName` is called **lazily**, and not on join at all: `ChestOpener.reconcileForOpen` — the
shared prelude for every self-open path (`/ec`, `/eclist`, right-click) — reuses the settings row it
already loaded there, compares the loaded `username` against the player's current name, and only writes
when they differ (a rename, or the first time that player ever opens an ender chest). A player who joins
but never opens a chest costs no write at all.

**To add a setting:** add a component to `PlayerSettings`, a column to all three DDLs (+ a
`SchemaMigrator` step), a field on `RawPlayerRow` mapped in `loadAllPlayers`/`batchPlayers`, and the
in-memory `PlayerRow` handling in `CachedStorage`.

### Write-through settings cache (`PlayerSettingsCache`)

Settings are read on every dialog open, so they are cached in RAM keyed by UUID. `PlayerSettingsListener`
preloads on join and evicts on quit, so the map is **bounded by the online-player count**.
`loadSettingsAsync` serves from the cache (a miss falls back to a one-off storage read that is *not*
cached, keeping `preloadSettings` the sole inserter). `setEditModeAsync` is **write-through**: it updates
the cached copy in place (`computeIfPresent`, never inserts) and writes the store immediately, so the
cache never holds dirty state and needs no shutdown flush. (With `CachedStorage` underneath this is a
cache over a cache — kept because it also carries the join/quit lifecycle and the lazy username write.)

**Leak-free invariant:** every entry is added by a join preload and removed by the matching quit
eviction; the join-then-immediate-quit race is closed by a post-load online re-check in `preloadSettings`
that drops an entry whose player already left. `onEnable` preloads already-online players (a `/reload`
fires no join event for them).

## Serialization (`ContainerCodec`)

Converts `ItemStack[] ⇄ byte[]`, parameterized by chest size on decode. `MAX_SIZE` is 54, `SLOT_STEP` is
9. Decode failures throw `CodecException`, which the service surfaces to the player (`chest.codec-failed`)
and refuses to open rather than risk clobbering stored data. Encoding on save is always synchronous on
the global thread (load-bearing for dupe-safety); decoding on open runs on the async DB executor alongside
the row load, so the NBT work never lands on a tick thread (see
[concurrency-and-dupe-safety.md](concurrency-and-dupe-safety.md)).

Stored bytes are `[1-byte version tag] + [body]`. The tag lets the format evolve without orphaning DB rows:

- **0x02 (current, write path)** — body is `ItemStack.serializeItemsAsBytes(ItemStack[])`: stable Paper
  API, preserves slot positions/length (null → `empty()`) and migrates across Minecraft versions on read.
- **0x01 (legacy, read-only)** — body is `ItemStack.serializeAsBytes()` of a `SHULKER_BOX` "vehicle"
  carrying an `@Experimental` `CONTAINER` data component. Older builds wrote this; it's still decoded but
  never written. Rows re-save as 0x02 the next time a chest is closed (lazy migration). **Keep this
  branch** — removing it would orphan legacy rows that haven't been touched since the upgrade.

## Auto-backup (`BackupService`)

Scheduled DB snapshots, modelled on `ExpirySweeper`: an async repeating timer (via `Scheduler`) at `backup.interval`
calls `EnderChestStorage.backup(Path)` — which, on `CachedStorage`, first flushes all dirty in-memory rows
so the snapshot is complete — then prunes the `backup.folder` to the most recent `backup.keep`
files (`keep <= 0` = unlimited). Snapshot names are `enderchests-<yyyyMMdd-HHmmss>.db`, so lexical order is
chronological. All work runs off the region/main thread and failures are logged, never thrown.

Backup is a **capability**, not a guarantee: `supportsBackup()` is false by default and only `SqliteStorage`
overrides it, using `VACUUM INTO` (a consistent, defragmented copy taken without pausing saves — never copy
the raw `.db` file). For mysql/mariadb/postgres the service logs a one-time warning and stays idle; those
must be backed up with the DB server's own tooling. Config: `backup.{enabled,interval,keep,on-startup,folder}`
— `enabled`/`interval`/`keep` are runtime-tunable via `/ee reload` (`reschedule`); `folder` is bound at
startup. `on-startup` runs one extra snapshot at enable.
