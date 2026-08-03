# CODEMAP — EnhancedEchest

Where every class lives and what it is responsible for. This is the **index**; the **rules** live in
[CLAUDE.md](../CLAUDE.md) and the nine per-package `CLAUDE.md` files. The machine-readable twin of this
file is [.claude/codemap.json](codemap.json).

`1.2.0` · Java 21 · Paper API 1.21.11 · 87 files, ~15k LOC · base package `com.enhancedechest`
(paths below are relative to `src/main/java/com/enhancedechest/`).

Hand-maintained: when you add, remove or rename a class, update the row here **and** the entry in
`codemap.json`.

---

## Start here

| I want to… | Go to |
|---|---|
| Change what happens when a chest opens | [service/ChestOpener.java](../src/main/java/com/enhancedechest/service/ChestOpener.java) → [service/ChestSessionManager.java](../src/main/java/com/enhancedechest/service/ChestSessionManager.java) |
| Touch anything that could duplicate items | [service/ChestSessionManager.java](../src/main/java/com/enhancedechest/service/ChestSessionManager.java) + [service/CLAUDE.md](../src/main/java/com/enhancedechest/service/CLAUDE.md) |
| Add a column / change the schema | [storage/sql/SchemaMigrator.java](../src/main/java/com/enhancedechest/storage/sql/SchemaMigrator.java) + the DDL in all three dialects |
| Add or change a config key | [config/PluginConfig.java](../src/main/java/com/enhancedechest/config/PluginConfig.java) + `resources/config.yml`; a **rename** also needs [config/ConfigMigrations.java](../src/main/java/com/enhancedechest/config/ConfigMigrations.java) |
| Add or change a command | [EnhancedEchestBootstrap.java](../src/main/java/com/enhancedechest/EnhancedEchestBootstrap.java) (the tree) + `command/` (the body) |
| Add a message or menu label | [lang/LanguageManager.java](../src/main/java/com/enhancedechest/lang/LanguageManager.java) + a key in **every** bundled locale |
| Change a dialog | [gui/dialog/ChestDialogs.java](../src/main/java/com/enhancedechest/gui/dialog/ChestDialogs.java) — all Dialog API usage is isolated there |
| Wire a new service | [EnhancedEchestPlugin.java](../src/main/java/com/enhancedechest/EnhancedEchestPlugin.java): construct in `onEnable`, re-apply in `reload()`, stop in `onDisable` |
| Schedule anything | [scheduler/Scheduler.java](../src/main/java/com/enhancedechest/scheduler/Scheduler.java) — never `Bukkit.getScheduler()` |
| Import from another plugin/database | `migration/` + [migration/CLAUDE.md](../src/main/java/com/enhancedechest/migration/CLAUDE.md) |
| Run several servers on one database | `crossserver/` + [crossserver/CLAUDE.md](../src/main/java/com/enhancedechest/crossserver/CLAUDE.md) |

---

## Entry points

| File | Lines | Role |
|---|---:|---|
| `EnhancedEchestBootstrap.java` | 525 | `PluginBootstrap`. Builds the whole Brigadier tree (`/enderchest`, `/ec`, `/eclist`, `/ee`) on `LifecycleEvents.COMMANDS`, before enable. Holds the permission node constants. |
| `EnhancedEchestPlugin.java` | 440 | The wiring hub. Every service is a hand-constructed field; **no DI container, so the order in `onEnable`/`onDisable` is load-bearing**. |

### Startup order (`onEnable`)

1. config (`YamlMigrator` **first**, then `PluginConfig`), `Scheduler`, `ContainerCodec`, `IconCatalog.setExternalLangDir`
2. `Telemetry` — before storage, so every later layer can report errors
3. the cross-server coordinator when `cross-server.enabled` (a misconfiguration **disables the plugin**, never degrades silently)
4. `StorageFactory.create` → wrapped in `CachedStorage` → `init()` (DDL + `SchemaMigrator`; no bulk load)
5. `LanguageManager`, then its translator registered once on `GlobalTranslator`
6. services bottom-up: `DbExecutor` → `StorageGateway` / `PlayerNameIndex` / `PlayerSettingsCache` → `ChestActivityLogger` → `ChestSessionManager` → `ChestSpillService` / `ChestTransferService` / `PermissionChestService` / `DatabaseImportService` → `ChestOpener`
7. migration services, `ExpirySweeper`, `BackupService`, `AutosaveService`
8. listeners, pin + preload online players, update check, bStats, banner

### Shutdown order (`onDisable`)

detach translator → stop timers → `sessionManager.shutdown()` (persists every open session, blocks ≤30s)
→ telemetry, settings cache → `dbExecutor.shutdown()` → `storage.close()` (final full flush) → Redis → cancel tasks.

---

## service/ — sessions, dupe-safety, async dispatch

The one layer allowed to put storage calls on the async pool. See [service/CLAUDE.md](../src/main/java/com/enhancedechest/service/CLAUDE.md).

| File | Lines | Role |
|---|---:|---|
| `ChestActivityLogger.java` | 936 | Bounded, batched audit pipeline. The Bukkit thread captures occupied slots as immutable strings/numbers once; diffing, rendering, rotation and file I/O run on one dedicated worker. Plain-text `OPEN`/`CLOSE` headers + `ADD`/`TAKE` totals, optional `HAVE` contents lines, shulker contents in the detail string. |
| `ChestOpener.java` | 688 | Decides **what** to open for `/ec`, `/eclist`, right-click and `/ee view`; reconciles permission chests; drives the management dialogs and `/ee import`. |
| `ChestSessionManager.java` | 664 | **The dupe-safety core.** The only class that mutates the live-session map, and only on the global bookkeeping thread. `open()` is the sole open funnel; `forceCloseAll` + `runExclusive` serialize admin mutations per `(owner, index)`. |
| `ChestSpillService.java` | 349 | Shrink-spill, delete (spill or force), bulk delete, and `reclaimTempInto` (temp chest → newly granted chest). Force-closes viewers, then runs exclusively per chest. |
| `ChestTransferService.java` | 235 | `/ee transfer` — moves NORMAL chests onto another account. A **move**, never a copy; the row swap is one transaction. |
| `PermissionChestService.java` | 262 | Grants/revokes `PERM` chests from `enhancedechest.additional_amount.<count>.slot.<size>`; all matching permissions stack. `reconcile()` diffs target vs held. |
| `PlayerSettingsCache.java` | 158 | Write-through read cache of per-player settings, bounded by online players. Also records `username` / `last_online`. |
| `PlayerNameIndex.java` | 151 | In-memory index of every recorded player name; loaded once at startup, answers offline tab-completion with no DB round-trip. |
| `DbExecutor.java` | 82 | The shared daemon pool for all async storage work (`EnhancedEchest-db`). Bounded at 64, sized per backend, closed last on disable. |
| `TempReclaimNotifier.java` | 73 | Tells the owner which chest their temp items moved into. Hooked on the tail of `reclaimTempInto` so no call site can forget it. |
| `StorageGateway.java` | 72 | Thin async wrapper over `EnderChestStorage` — pure delegation, no session logic. |

---

## storage/ — the cache, the SQL backends, the schema

Storage methods are **synchronous**; callers dispatch. See [storage/CLAUDE.md](../src/main/java/com/enhancedechest/storage/CLAUDE.md).

| File | Lines | Role |
|---|---:|---|
| `CachedStorage.java` | 637 | The `EnderChestStorage` everyone actually sees. Lazy-loading write-back cache; a thin façade holding only the ender-chest domain logic. |
| `sql/AbstractSqlStorage.java` | 377 | The JDBC backend shared by all dialects: DDL, per-player reads, batched dirty flush, whole-database queries, verbatim import copy, HikariCP, `database.ssl` modes. **No per-row write DML.** |
| `sql/SchemaMigrator.java` | 377 | Versioned, forward-only migrator for **existing** installs (fresh installs land on latest via `CREATE TABLE`). Also `renameLegacyTables` (`enderchests`/`players`/`schema_meta` → `echest_*`), which runs *before* `CREATE TABLE`. |
| `OwnerResidencyCache.java` | 362 | The coherence engine: load-on-miss residency protocol, write-back flush, idle eviction, the single lock. `withOwner()` = residency re-check + operation in one lock hold. Knows only "owners" and "dirty rows". |
| `ChestCacheState.java` | 346 | The pure in-memory row model + dirty tracking + index allocation. **Not thread-safe** — only touched under the residency cache's lock. |
| `EnderChestStorage.java` | 320 | The synchronous storage contract. Ownership: a player owns a chest iff a row exists for `(player_uuid, chest_index)`. |
| `StorageBackend.java` | 113 | The narrow SQL contract the cache needs. Deliberately **no per-row writes** — all row semantics live in `CachedStorage`. |
| `AutosaveService.java` | 111 | Async repeating `flush()` + `evictIdle()` at `database.autosave-interval` (default 3m), plus the quit write-back (+5s). |
| `sql/SqliteStorage.java` | 102 | SQLite (WAL, 30s busy timeout, `VACUUM INTO` backup). Driver is `compileOnly` from the server classpath — **never relocated**. |
| `sql/MysqlStorage.java` | 87 | MySQL/MariaDB (`ON DUPLICATE KEY` upserts). |
| `sql/PostgresStorage.java` | 85 | PostgreSQL (`ON CONFLICT` upserts). |
| `StorageFactory.java` | 24 | Picks the dialect from config. |

---

## gui/ — dialogs, menus, icons

See [gui/CLAUDE.md](../src/main/java/com/enhancedechest/gui/CLAUDE.md).

| File | Lines | Role |
|---|---:|---|
| `dialog/ChestDialogs.java` | 730 | **All** Paper Dialog API usage, isolated so a Paper breaking change is a one-file edit. List → per-chest detail → rename, plus the config editor and import dialogs. Rendered eagerly with the viewer's `Locale`. |
| `dialog/IconCatalog.java` | 343 | The pickable icon catalog over the `Material` registry + rendering a chosen icon as an Adventure sprite object component (`minecraft:blocks` / `minecraft:items` atlases). Uses gson from the server classpath. |
| `ChestListMenu.java` | 190 | The simple `/eclist` inventory chooser (`enderchest.list-menu: inventory`): padding-border layout sized 27/36/45/54 to the chest count; >28 chests falls back to the dialog. |
| `dialog/DialogLinks.java` | 63 | The shared "open the documentation" button (book sprite, URL from `gui.yml`, address never shown on screen). |
| `EnderChestAnimator.java` | 61 | Vanilla lid animation + sound via the pure Paper `Lidded` API (no NMS). Must run on the block's region thread. |
| `ChestListHolder.java` | 52 | Marker holder for the chooser menu (read-only; every interaction cancelled). |
| `EnderChestHolder.java` | 51 | Marker holder on every custom chest `Inventory`: owner, index, size, `ChestKind`, optional source block. |

---

## command/ — Brigadier bodies

Registration lives in the bootstrap, not `plugin.yml`. See [command/CLAUDE.md](../src/main/java/com/enhancedechest/command/CLAUDE.md).

| File | Lines | Role |
|---|---:|---|
| `admin/ChestAdminCommand.java` | 352 | `/ee add\|resize\|delete <player>`. Add allocates the next free index; resize spills cut-off items; delete removes the newest N (spill by default, `force` to hard-delete) and always keeps the first chest. |
| `admin/MigratePlayerVaultsXCommand.java` | 130 | `/ee migrate playervaultsx [all\|<player>]` |
| `admin/MigrateCustomEnderChestCommand.java` | 130 | `/ee migrate customenderchest [all\|<player>]` |
| `admin/MigrateAxVaultsCommand.java` | 129 | `/ee migrate axvaults [all\|<player>]` |
| `admin/MigrateVanillaCommand.java` | 107 | `/ee migrate vanilla [all\|<player>]` — vanilla EC → chest #1. |
| `admin/ChestTransferCommand.java` | 77 | `/ee transfer <from> <to> <index\|name\|all> [override\|temp]`; target + flag parsed from one greedy argument so names may contain spaces. |
| `admin/PlayerResolver.java` | 64 | Name → UUID, offline-safe. Online exact match → the plugin's own `players` table → Paper usercache → Bukkit offline lookup. |
| `EnderChestOpenCommand.java` | 63 | `/ec` and `/eclist`. |
| `admin/ImportCommand.java` | 38 | `/ee import` — opens the DB→DB dialog (player-only). |
| `admin/ReloadCommand.java` | 23 | `/ee reload`. |

---

## listener/ — lifecycle and guards

See [listener/CLAUDE.md](../src/main/java/com/enhancedechest/listener/CLAUDE.md).

| File | Lines | Role |
|---|---:|---|
| `EnderChestGuiListener.java` | 213 | Click/drag guards, admin-edit permission check, deny-sound throttle, and the detach-on-close that triggers the save when the **last** viewer leaves. |
| `TempChestJoinNotifyListener.java` | 113 | On-join chat + action bar (+ sound) reminder that items are still sitting in temp chests. |
| `PlayerSettingsListener.java` | 66 | **Join:** pin in the storage cache, then preload settings — which also materializes the chest rows, so it doubles as the join prefetch. **Quit:** stamp `last_online`, evict, unpin, schedule the delayed write-back. |
| `JoinMigrationListener.java` | 64 | Auto-migrates un-migrated players on join. The `isMigrated` pre-check runs on `DbExecutor`, never the join thread. |
| `ChestListMenuListener.java` | 53 | Drives the chooser menu: cancels every click/drag, opens the clicked chest through `ChestOpener`. |
| `VanillaEnderChestListener.java` | 53 | Right-click interception on ender chest blocks (+ shift+right-click → chest list). |
| `PlayerQuitListener.java` | 38 | Backstop detach when a player disconnects with the GUI open; `detach` is idempotent. |

---

## Everything else

| Package | File | Lines | Role |
|---|---|---:|---|
| `lang` | `LanguageManager.java` | 423 | Loads language files as locale-free `Component.translatable`; parsed Components cached. Every player-facing string needs a key here in **every** bundled locale. |
| `lang` | `EnhancedEchestTranslator.java` | 93 | The Adventure `Translator` on `GlobalTranslator`, so every Component renders in the recipient's client locale. Fallback: exact → same language → configured locale → `en_US`. |
| `config` | `PluginConfig.java` | 373 | The typed snapshot of `config.yml`. Fields read from other threads are `volatile`. |
| `config` | `YamlMigrator.java` | 109 | On startup, for every config/language file: apply renames → add missing keys from the bundled default → write only if changed. Existing user values are never touched. |
| `config` | `ConfigMigrations.java` | 42 | The registry of rename rules. **A rename without a rule silently loses the setting.** |
| `crossserver` | `RedisCoordinator.java` | 392 | One TTL'd, heartbeat-extended lock key per owner + a pub/sub channel for fast handover. Never steals a live holder's lock. |
| `crossserver` | `CrossServerCoordinator.java` | 73 | The contract: resident here ⇒ this server holds the owner's lock. Release is split (`beginRelease` inside the cache lock, `finishRelease` after — never network I/O under the lock). |
| `crossserver` | `CrossServerLockException.java` | 15 | Acquire timed out. Propagates like a failed SQL read — fails loudly, never proceeds on stale data. |
| `migration` | `MigrationService.java` | 279 | Vanilla EC → chest #1, under the session manager's exclusivity, re-checking the migrated flag inside it. |
| `migration` | `AxVaultsReader.java` | 182 | `axvaults_data` + the AxAPI `ITEM_ARRAY` framing (int slot count, then per slot an unsigned-short length + gzip NBT bytes). |
| `migration` | `SourceDatabaseReader.java` | 176 | Opens an EnhancedEchest DB of any dialect read-only and reads rows verbatim for `/ee import`. |
| `migration` | `PlayerVaultsXReader.java` | 160 | `plugins/PlayerVaults/newvaults/<uuid>.yml`, Base64 `CardboardBoxSerialization` framing. |
| `migration` | `PlayerVaultsXMigrationService.java` | 156 | Vault #N → chest #N, never overwriting an occupied chest (safe to re-run). |
| `migration` | `AxVaultsMigrationService.java` | 149 | Same, for AxVaults. |
| `migration` | `CustomEnderChestMigrationService.java` | 134 | One chest per player → chest #1 only. |
| `migration` | `CustomEnderChestReader.java` | 128 | `plugins/CustomEnderChest/playerdata/<uuid>.yml`. |
| `migration` | `DatabaseImportService.java` | 70 | `/ee import`: old backend → **active** backend in one transaction, item bytes copied verbatim. |
| `migration` | `SourceSpec.java` | 69 | The import source's connection details, from the dialog. |
| `util` | `DurationFormat.java` | 181 | `s/m/h/d/w/mo/y` (month = 30d, year = 365d); `20s` or `1d_2h_30m_15s`. Also the "expires in" text. |
| `backup` | `BackupService.java` | 175 | Async repeating snapshot (SQLite `VACUUM INTO` — consistent while players save). SQLite only; remote backends warn once and idle. Prunes to the most recent `keep`. |
| `update` | `UpdateChecker.java` | 167 | Modrinth lookup with a GitHub release fallback. |
| `update` | `UpdateNotifyListener.java` | 56 | Notifies permitted players on join. |
| `serialization` | `ContainerCodec.java` | 122 | The stored blob format: `[1-byte version tag] + body`; `0x02` = `ItemStack.serializeItemsAsBytes`, which keeps slot positions and migrates across MC versions on read. Isolates the Data Component API. |
| `serialization` | `CodecException.java` | 12 | Decode failure. |
| `telemetry` | `FastStatsTelemetry.java` | 110 | The `dev.faststats` implementation; `create()` → `NOOP` on failure. Metric suppliers run on SDK threads — immutable/volatile `PluginConfig` reads only. |
| `telemetry` | `Telemetry.java` | 31 | The **only** telemetry type the rest of the plugin may depend on. `error(e, "site-label")` always accompanies a log line, never replaces it. |
| `expiry` | `ExpirySweeper.java` | 99 | Async sweep: a DB-side candidate query (the only way to see offline owners) re-verified against memory, plus a resident scan. NORMAL → spill to temp; TEMP → hard delete. Reuses the spill service, so it is as dupe-safe as a manual delete. |
| `scheduler` | `Scheduler.java` | 90 | Thin wrapper over Paper's `threadedregions.scheduler` API — safe on Paper *and* Folia with no branching. `runAsync`/`runAtEntity`/`runAtLocation`/`runNextTick` take a `Consumer<ScheduledTask>`; the `*Later`/`*Timer*` variants have `Runnable` overloads. |
| `model` | `PlayerSettings.java` | 58 | One row in `players`, loaded and saved whole. A new setting = record component + DDL column in all three dialects + a `SchemaMigrator` step. |
| `model` | `ChestKind.java` | 37 | `NORMAL` / `TEMP` (auto-created overflow, always expiring, vanishes when emptied) / `PERM` (permission-granted, untouched by admin commands). |
| `model` | `EnderChestData.java` | 28 | One chest as loaded for opening. |
| `model` | `ChestSummary.java` | 28 | The lightweight per-chest description behind the list dialog. |

---

## Invariants — do not break these

1. **No item duplication.** One shared `Inventory` per open chest, loaded fresh on first open, saved on **last** viewer close, with a pending-save wait on reopen. Every open funnels through `ChestSessionManager.open`. The encode on save stays **synchronous on the global thread**.
2. **Residency.** `CachedStorage` is authoritative for every resident owner. Per-owner work goes through `withOwner`; **dirty ⇒ resident**; eviction takes only clean owners. Cross-server adds: **resident ⇒ this server holds the owner's Redis lock**.
3. **Storage is synchronous; only `service/` dispatches it** onto `DbExecutor`. That convention is what keeps the dupe-safety ordering intact.
4. **Folia.** All scheduling via `Scheduler`. Never touch an entity or block off its region thread. `isFolia()` has exactly one legitimate use (`ChestSessionManager`'s single-viewer-on-Folia rule) — don't add more branches.
5. **No player-facing string literals.** `LanguageManager` keys only, present in every bundled locale; dialogs and item names rendered eagerly with the viewer's `Locale`.
6. **Commands register in the bootstrap**, gated by `.requires(...)`; admin permissions default to `op`.
7. **Config renames need a `ConfigMigrations` rule.**
8. **The main chest is never auto-assigned** — only the dialog's "Set as main" sets it.
9. **Telemetry is a facade**; metrics stay `storage_type` + `language` (action counters were removed on request — don't reintroduce without asking).
10. **API floor 1.21.11** — the jar must keep running on 1.21.11 → 26.2.
11. **Relocation**: shaded libs live under `com.enhancedechest.libs.*`; the SQLite driver and gson come from the server classpath and must **never** be relocated.

---

## Tests

`./gradlew test` (excludes `**/*Simulation*`) · `./gradlew stressTest` (300–500 player simulation, no server needed)

| File | Covers |
|---|---|
| `storage/ChestIndexAllocationTest.java` | Next-free-index allocation |
| `storage/TempReclaimTest.java` | Temp reclaim into a newly granted chest |
| `storage/SchemaMigratorUpgradeTest.java` | Old schema → `CURRENT_VERSION` |
| `storage/StoragePlayerLoadSimulationTest.java` | Concurrency / perf / leak (stress) |
| `service/ChestActivityLogTest.java` | The activity-log pipeline |
| `service/ChestActivityLogSimulationTest.java` | Activity-log load (stress) |

Coverage is a deliberately thin slice. **Every GUI, dialog, command and storage path is verified by
running on a Paper/Folia server** — assume nothing is checked automatically.

---

## Resources (`src/main/resources/`)

`config.yml` · `plugin.yml` / `paper-plugin.yml` (version templated in at build time) ·
`language/{en_US,vi_VN}/{messages,gui}.yml` · `icons/lang/{en_us,vi_vn}.json` ·
`icons/valid-icon-sprites.txt` (materials whose sprite renders correctly in a dialog button) ·
`faststats.properties` (build-time token slot; absent → `Telemetry.NOOP`).

---

## Gotchas

- `logging/`, `logging/texture/` and `logging/viewer/` are **empty leftover directories** from a
  reverted JSONL-log + HTML-viewer build. No classes live there; the working log is
  `service/ChestActivityLogger.java`. Safe to delete.
- Long files are normal — read the surrounding region before editing rather than pattern-matching on
  one method.
- Comments explain **why a choice is load-bearing**, not what a line does. Keep that style.
- A new service means three edits in `EnhancedEchestPlugin`: construct in `onEnable` at the right
  point, re-apply runtime-tunable settings in `reload()`, shut down at the right point in `onDisable`.
- `CHANGELOG.md`, `docs/` and the `config.yml` comments are end-user facing — write them with the
  `write-docs` skill, not ad hoc.
