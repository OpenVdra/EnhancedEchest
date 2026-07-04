# Storage, schema, settings cache & serialization

## Storage layer

`EnderChestStorage` models ownership as **row existence**: a player owns chest `index` iff a row exists
for `(player_uuid, chest_index)`. There is no separate "owners" table. All methods are **synchronous**
and thread-agnostic — the `com.enhancedechest.service` layer is the only caller and dispatches them onto
the shared `DbExecutor` async pool (see [concurrency-and-dupe-safety.md](concurrency-and-dupe-safety.md)).

`AbstractSqlStorage` holds all DML as plain SQL valid across SQLite, MySQL/MariaDB and PostgreSQL. Only
`CREATE TABLE` statements are dialect-specific, injected by each subclass (`SqliteStorage`,
`MysqlStorage`, `PostgresStorage`) as a `String...` of DDL run in order by `init()` (currently
`enderchests` + `players`). New chest indexes are computed in Java (`MAX(chest_index)+1`), so no
dialect-specific upsert is required. Connections come from a HikariCP pool (size 1 for SQLite,
configurable otherwise). `StorageFactory` picks the backend from `config.type`.

SQLite runs in **WAL mode** with `synchronous=NORMAL` (set as driver properties in
`SqliteStorage.buildConfig`, applied as PRAGMAs per connection; `journal_mode` also persists in the DB
file): readers aren't blocked by the writer and commits become WAL appends. Its `connectionTimeout` is a
deliberate **30s** — the backup's `VACUUM INTO` holds the single connection for the whole snapshot, and a
save that lands mid-backup must ride that out and then succeed rather than time out unwritten.

`init()` also calls `SchemaMigrator.migrate()` right after running the CREATE statements: a versioned,
forward-only migrator (`schema_meta` table) that brings an existing (older) database up to the current
schema — additive column steps guarded by a JDBC-metadata `columnExists` check, occasional table
renames/merges guarded by `tableExists` (e.g. the 1.0.4 `player_settings` → `players` merge). A fresh
install's CREATE statements already carry every column, so the migrator's steps no-op on it.

**Rule:** all DML portable, only DDL per-dialect. Avoid `ON CONFLICT` / `ON DUPLICATE KEY`; do a portable
`UPDATE`-then-`INSERT`-if-no-row upsert instead.

## Schema: `enderchests`

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
| `expires_at` | nullable epoch-ms expiry; `NULL` = never. Indexed (`idx_enderchests_expires`) |
| `icon` | nullable material key (e.g. `minecraft:diamond`) of the list icon; `NULL` = default. Rendered as an Adventure sprite component in the dialogs |

Key operations: `createChest` (next index, **never** auto-primary; optional `expiresAt`), `createPermChest`
(next index, `kind = PERM`, no expiry, never auto-primary — used by the permission reconcile), `ensureChest`
(create at a fixed index if absent — migration only, also never auto-primary), `resizeChest`,
`deleteChest` (**no survivor promotion** — if the deleted chest was the main, the player simply has no
main until they pick one), `renameChest`, `setIcon`, `setPrimary` (clear-then-set in a transaction — the
only way a chest becomes primary), `clearPrimary`, `isMigrated`/`setMigrated`, the item-moving
`spillShrink` / `spillRemove`, and the sweeper query `findExpired`. `saveChest` is **UPDATE-only** and
never touches size, name, or primary.

Primary resolution (`SQL_PRIMARY`) filters `kind <> 1` (everything **except** TEMP) and orders
`is_primary DESC, chest_index ASC`, so it returns the flagged main when one exists and otherwise the
lowest-indexed non-temp chest. Both NORMAL and PERM chests are eligible to be opened by `/ec` and set as
the main; only temp chests are excluded.

## Schema: `players`

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
(single targeted field, no preceding read) are all portable upserts — `saveSettings` deliberately excludes
`username`, which is written only by the separate `upsertPlayerName`/`findUuidByName` pair, so a save built
from a stale in-memory `PlayerSettings` can never clobber a name recorded since it was loaded.

`upsertPlayerName` is called **lazily**, and not on join at all: `ChestOpener.reconcileForOpen` — the
shared prelude for every self-open path (`/ec`, `/eclist`, right-click) — reuses the settings row it
already loaded there, compares the loaded `username` against the player's current name, and only writes
when they differ (a rename, or the first time that player ever opens an ender chest). A player who joins
but never opens a chest costs no write at all.

**To add a setting:** add a component to `PlayerSettings`, a column to all three DDLs, and a mapping in
`loadSettings`/`saveSettings`.

### Write-through settings cache (`PlayerSettingsCache`)

Settings are read on every dialog open, so they are cached in RAM keyed by UUID. `PlayerSettingsListener`
preloads on join and evicts on quit, so the map is **bounded by the online-player count**.
`loadSettingsAsync` serves from the cache (a miss falls back to a one-off DB read that is *not* cached,
keeping `preloadSettings` the sole inserter). `setEditModeAsync` is **write-through**: it updates the
cached copy in place (`computeIfPresent`, never inserts) and writes the DB immediately, so the cache
never holds dirty state and needs no shutdown flush.

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

Scheduled DB snapshots, modelled on `ExpirySweeper`: a FoliaLib async repeating timer at `backup.interval`
calls `EnderChestStorage.backup(Path)`, then prunes the `backup.folder` to the most recent `backup.keep`
files (`keep <= 0` = unlimited). Snapshot names are `enderchests-<yyyyMMdd-HHmmss>.db`, so lexical order is
chronological. All work runs off the region/main thread and failures are logged, never thrown.

Backup is a **capability**, not a guarantee: `supportsBackup()` is false by default and only `SqliteStorage`
overrides it, using `VACUUM INTO` (a consistent, defragmented copy taken without pausing saves — never copy
the raw `.db` file). For mysql/mariadb/postgres the service logs a one-time warning and stays idle; those
must be backed up with the DB server's own tooling. Config: `backup.{enabled,interval,keep,on-startup,folder}`
— `enabled`/`interval`/`keep` are runtime-tunable via `/ee reload` (`reschedule`); `folder` is bound at
startup. `on-startup` runs one extra snapshot at enable.
