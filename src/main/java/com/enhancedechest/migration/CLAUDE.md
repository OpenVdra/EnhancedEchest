# migration/

Getting other people's data in: the vanilla ender chest, three third-party chest/vault plugins, and an
older EnhancedEchest database. All of it runs on the shared `DbExecutor`, never on a region thread.

| File | Source |
|---|---|
| `MigrationService` | The vanilla ender chest → chest #1 (`/ee migrate vanilla`, or automatic on join) |
| `AxVaultsReader` + `AxVaultsMigrationService` | AxVaults' SQLite `data.db` |
| `PlayerVaultsXReader` + `PlayerVaultsXMigrationService` | PlayerVaults(X) flat files |
| `CustomEnderChestReader` + `CustomEnderChestMigrationService` | CustomEnderChest YAML player files |
| `SourceDatabaseReader` + `SourceSpec` + `DatabaseImportService` | Another EnhancedEchest database (`/ee import`) |

Every importer follows the same two rules, and new ones must too:

- **Skip, never overwrite.** A destination chest that already has `container_data` is reported as
  skipped, which makes every import idempotent and dupe-safe.
- **Reader and service are separate.** The reader only turns foreign bytes into `ItemStack[]` (or raw
  rows); the service owns the dupe-safe write. Readers are stateless or `AutoCloseable`, and safe off
  the main thread — item deserialization runs against frozen registries.

## Vanilla (`MigrationService.migrateOnline`)

Asynchronous, three phases:

1. **Entity thread** — snapshot the vanilla ender chest contents (cloned stacks, safe to cross threads).
2. **Exclusive DB phase** — `forceCloseAll(uuid, 1)` then `runExclusive(uuid, 1, …)`: re-check
   `isMigrated`, ensure chest #1, **merge** the snapshot into the current contents, and commit contents +
   overflow temp chest + the `migrated` flag in **one atomic transaction** (`completeMigration`).
3. **Entity thread again** — clear the vanilla ender chest. Cosmetic only: the DB copy is already
   authoritative and flagged.

Three load-bearing choices in phase 2:

- **Exclusivity.** Chest #1 may have a live session right then (the player raced `/ec` against the join
  pre-check, or an admin migrated them mid-edit); a naked write under it would be silently undone by
  that session's close-save. Don't replace this with a bespoke lock — it is the same per-(owner, index)
  serialization every other chest mutation uses.
- **Merge, not overwrite — and never resize.** Chest #1 can legitimately hold items, and its size is the
  admin/permission domain. A fresh, big-enough chest keeps the vanilla layout positionally; otherwise
  existing items stay put, vanilla stacks fill free slots, and the rest **spills into a temp chest**.
  Nothing is ever truncated, so the merge always succeeds — no retry loop.
- **The flag commits with the write.** The merge is **not** idempotent (a re-run would duplicate items),
  so "merged but not flagged" must be unobservable, both to a crash and to a queued second migration
  whose `isMigrated` re-check runs behind this op. Never split it back into `saveChest` + `setMigrated`.

The join trigger (`migration.enabled`, `JoinMigrationListener`) pre-checks `isMigrated` on the
`DbExecutor` first — never on the join thread — so an already-migrated player costs zero main-thread DB
per join even during a mass reconnect. Once everyone is migrated, turn `migration.enabled` off.

## Third-party sources

| Source | Layout | Notes |
|---|---|---|
| **AxVaults** | SQLite `plugins/AxVaults/data.db`, `axvaults_data.storage` blob: big-endian `[int slotCount]` then `[ushort len][len bytes]` per slot | Each item is Paper `serializeAsBytes` bytes → `deserializeBytes`. The default **H2** backend (`data.mv.db`) is **not** supported — the reader throws a clear "switch to SQLite" error, no H2 driver is shaded. AxVaults flushes only on autosave/quit/`/vaultadmin save`, so save before importing |
| **PlayerVaultsX** | No database: `plugins/PlayerVaults/newvaults/<uuid>.yml`, keys `vault1`, `vault2`, … each a MIME-Base64 `CardboardBox` frame (`int` slot count, then `int` length + bytes per slot) | An empty slot is the single byte `0x0`. Vault files written by an old non-Paper (Spigot) server use a different framing and are not handled |
| **CustomEnderChest** | `plugins/CustomEnderChest/playerdata/<uuid>.yml` with `enderchest-size` + `enderchest-inventory` (per-slot map or `null`) | One chest per player, not numbered vaults — it maps onto EnhancedEchest's base chest #1. Only `storage.type: yml` is read |

Vault-style sources write each vault into the EE chest of the **same index**, sized up to a multiple of
9 (cap 54).

## `/ee import` (database → database)

The DB→DB copy, for switching backends: the admin points `config.yml` at the **new** backend, restarts
with **no players online**, and runs the import; `SourceDatabaseReader` opens the **old** backend
read-only and hands its rows to `EnderChestStorage.importRows`, which writes them in one transaction.

- **Bytes are copied verbatim** — no decode, no re-encode — so the copy is exact and the cost is the
  batched destination writes.
- The destination must be empty and must differ from the source; the command refuses otherwise.
- No `SchemaMigrator` runs against the source: the docs tell the admin to load the source with this
  plugin version first, so a missing column surfaces as a SQLException that the service reports as
  "source schema outdated" rather than importing partial data.
- The source is assumed to use the active install's `database.table-prefix` — both are this plugin's
  schema.
- `CachedStorage` flushes before the copy, and imported rows refresh only owners already resident.
- Connection details come from `SourceSpec`, filled in by the static import dialog (which shows every
  field regardless of type; only the ones relevant to `type` are read).
