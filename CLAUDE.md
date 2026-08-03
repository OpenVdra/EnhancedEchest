# EnhancedEchest

Paper/Folia plugin. Replaces the vanilla ender chest with a database-backed one: a player owns
several chests of up to 54 slots, each with its own size, custom name and icon, managed from an
in-game dialog. Contents are serialized to SQLite / MySQL / MariaDB / PostgreSQL. Java 21, Gradle
Kotlin DSL, single module, ~15k LOC across 90 files.

## Build and run

```bash
./gradlew build
```

Produces `EnhancedEchest-<version>.jar` via `shadowJar` (the plain `jar` is not the deliverable).
`build.gradle.kts` also copies the jar into a local `TestServer/plugins` directory
(`shadowJar.destinationDirectory`) — adjust that path if your test server lives elsewhere.

```bash
./gradlew test        # unit tests; excludes **/*Simulation*
./gradlew stressTest  # 300–500 player concurrency/perf/leak simulation, no server needed
```

`src/test` covers a deliberately thin slice: chest index allocation, temp
reclaim, the activity-log pipeline, and the load simulations. Everything else — every GUI, dialog,
command and storage path — is **verified by running on a Paper/Folia server**. Assume nothing is
covered automatically: check a change by running it.

## Stack and constraints

- **Java 21**, **Paper API 1.21.11** (`paper-api:1.21.11-R0.1-SNAPSHOT`, api-version `1.21`).
  Compiled against the lowest supported API so the jar runs on servers **1.21.11 through 26.2** —
  do not call APIs newer than 1.21.11.
- Paper-only APIs throughout (`paper-plugin.yml` bootstrapper, Brigadier commands, Dialog API), so
  the plugin needs **Paper** or a Paper fork (Purpur / Folia) and does not run on CraftBukkit.
- Third-party libs are **shaded and relocated** under `com.enhancedechest.libs.*` (HikariCP, MariaDB
  driver, PostgreSQL driver + `com.ongres`, Jedis + commons-pool2 + org.json, bStats, FastStats).
  Never reference them by their original package without matching the relocation.
- Two libs are `compileOnly` and come from the **server classpath** instead: the SQLite driver
  (`org.xerial:sqlite-jdbc`) and **gson** (used by `IconCatalog` and the shaded Jedis). Neither may
  be relocated — relocation would rewrite the references to a package that is not in the jar.
  Annotation-only artifacts (`org.checkerframework`, `com.google.errorprone`) are excluded from the
  shadow jar.
- Lombok is `compileOnly` and used sparingly (`@Getter` on `EnhancedEchestPlugin`); most classes are
  hand-written constructors and records.
- Base package `com.enhancedechest`. One Gradle module, no separate API artifact.

## Architecture in one pass

[EnhancedEchestBootstrap.java](src/main/java/com/enhancedechest/EnhancedEchestBootstrap.java) is a
`PluginBootstrap` that registers the `/enderchest`, `/eclist` and `/enhancedechest` trees on the
`LifecycleEvents.COMMANDS` lifecycle, before enable.
[EnhancedEchestPlugin.java](src/main/java/com/enhancedechest/EnhancedEchestPlugin.java) is the wiring
hub: every service is a field on it, constructed by hand. There is no DI container, so **the order in
`onEnable` is load-bearing**:

1. config (`YamlMigrator` first, then `PluginConfig`), `Scheduler`, `ContainerCodec`, `IconCatalog.setExternalLangDir`
2. `Telemetry` — before storage, so every later layer (including the cache's shutdown flush) can report errors
3. the cross-server coordinator when `cross-server.enabled` (a misconfiguration **disables the plugin**, it never degrades silently)
4. `StorageFactory.create` → wrapped in `CachedStorage` → `init()` (DDL + `SchemaMigrator`; no bulk load)
5. `LanguageManager`, then its translator registered once on Adventure's `GlobalTranslator`
6. service layer bottom-up: `DbExecutor` → `StorageGateway` / `PlayerNameIndex` / `PlayerSettingsCache` → `ChestActivityLogger` → `ChestSessionManager` → `ChestSpillService` / `ChestTransferService` / `PermissionChestService` / `DatabaseImportService` → `ChestOpener`
7. the migration services, `ExpirySweeper`, `BackupService`, `AutosaveService`
8. listeners, pin + preload already-online players, update check, bStats, startup banner

Data flow of an open: a listener or command calls `ChestOpener`, which reconciles permission chests
and decides *what* to show, then funnels into `ChestSessionManager.open` — the row is read and decoded
on the DB executor, the shared `Inventory` is built on the global thread, and viewers attach to it.
When the **last** viewer closes, the contents are encoded and written back into `CachedStorage`, which
keeps the rows in memory until an autosave, a quit write-back or the shutdown flush pushes them to SQL.

`onDisable` order matters as much: detach the translator, stop the timers, `sessionManager.shutdown()`
(persists every open session, blocks ≤30s for pending saves), telemetry, settings cache,
`dbExecutor.shutdown()`, then `storage.close()` (the final full flush), then Redis, then cancel tasks.

## Where to look

| Task | Location |
|---|---|
| Opening a chest, sessions, dupe-safety, spill/sort/transfer, permission chests, activity log | `service/` (see [service/CLAUDE.md](src/main/java/com/enhancedechest/service/CLAUDE.md)) |
| The cache, the SQL backends, the schema, autosave, backup | `storage/` (see [storage/CLAUDE.md](src/main/java/com/enhancedechest/storage/CLAUDE.md)) |
| Any dialog or inventory menu, the icon picker | `gui/` (see [gui/CLAUDE.md](src/main/java/com/enhancedechest/gui/CLAUDE.md)) |
| Adding or changing a command, permission nodes | `command/` (see [command/CLAUDE.md](src/main/java/com/enhancedechest/command/CLAUDE.md)) |
| Messages, GUI text, per-viewer localization | `lang/` (see [lang/CLAUDE.md](src/main/java/com/enhancedechest/lang/CLAUDE.md)) |
| A config key, renaming a key safely | `config/` (see [config/CLAUDE.md](src/main/java/com/enhancedechest/config/CLAUDE.md)) |
| Redis owner locks, running several servers on one database | `crossserver/` (see [crossserver/CLAUDE.md](src/main/java/com/enhancedechest/crossserver/CLAUDE.md)) |
| Importing from vanilla, AxVaults, PlayerVaultsX, CustomEnderChest, another database | `migration/` (see [migration/CLAUDE.md](src/main/java/com/enhancedechest/migration/CLAUDE.md)) |
| Join/quit lifecycle, click and drag guards, right-click interception | `listener/` (see [listener/CLAUDE.md](src/main/java/com/enhancedechest/listener/CLAUDE.md)) |
| Folia-safe scheduling | [Scheduler.java](src/main/java/com/enhancedechest/scheduler/Scheduler.java) |
| `ItemStack[] ⇄ byte[]`, the stored blob format | [ContainerCodec.java](src/main/java/com/enhancedechest/serialization/ContainerCodec.java) |
| Expiry sweeps of temp/expiring chests | [ExpirySweeper.java](src/main/java/com/enhancedechest/expiry/ExpirySweeper.java) |
| Scheduled database snapshots | [BackupService.java](src/main/java/com/enhancedechest/backup/BackupService.java) |
| Error reporting and custom metrics | [Telemetry.java](src/main/java/com/enhancedechest/telemetry/Telemetry.java) |
| Duration parsing, "expires in" text | [DurationFormat.java](src/main/java/com/enhancedechest/util/DurationFormat.java) |
| Records shared across layers (`EnderChestData`, `ChestSummary`, `PlayerSettings`, `ChestKind`) | `model/` |
| Shipped defaults (config.yml, language, icon tables) | `src/main/resources/` |
| End-user docs (VitePress) | `docs/` (see the `write-docs` skill) |

## Invariants

**No item duplication.** A chest's contents exist in exactly one place at a time: one **shared
`Inventory`** per open chest, loaded fresh on first open, saved on **last** viewer close, with a
pending-save wait on reopen. Every open path funnels through `ChestSessionManager.open`; a second,
independently loaded `Inventory` reintroduces duping. The encode on save is synchronous on the global
thread — do not move it off-thread. Details in
[service/CLAUDE.md](src/main/java/com/enhancedechest/service/CLAUDE.md).

**Residency.** The `EnderChestStorage` everyone sees is `CachedStorage`, authoritative for every
resident owner. Per-owner operations go through `withOwner` (residency re-check + operation in one
lock hold), **dirty ⇒ resident**, and eviction takes only clean owners. Do not bypass it. In
cross-server mode the same invariant grows a distributed leg: **resident ⇒ this server holds the
owner's Redis lock**.

**Storage methods are synchronous; only `service/` dispatches them.** `com.enhancedechest.service` is
the one layer allowed to put storage calls on the async pool (the shared `DbExecutor`, thread pool
`EnhancedEchest-db`). That convention is what keeps the dupe-safety ordering intact.

**Folia.** All scheduling goes through `com.enhancedechest.scheduler.Scheduler`, a thin wrapper over
Paper's own `io.papermc.paper.threadedregions.scheduler.*` API, which Paper implements safely on both
plain Paper and Folia — no platform branching needed for dispatch. `runAsync` / `runAtEntity` /
`runAtLocation` / `runNextTick` take a `Consumer<ScheduledTask>`, **not** a `Runnable`; the
`*Later` / `*Timer*` variants have `Runnable` overloads. `Scheduler.isFolia()` exists for exactly one
genuine behavioural branch (`ChestSessionManager`'s single-viewer-on-Folia vs concurrent-edit-on-Paper
rule) — don't add new platform branches elsewhere. Never touch an entity or block off its region thread.

**Player-facing strings are never literals.** Everything goes through `LanguageManager` with a key
that must exist in every bundled locale. Dialogs and inventory item names must be rendered **eagerly
with the viewer's `Locale`**; only chat and inventory window titles are auto-rendered by Paper. See
[lang/CLAUDE.md](src/main/java/com/enhancedechest/lang/CLAUDE.md).

**Commands are registered in the bootstrap, not `plugin.yml`.** Paper Brigadier on
`LifecycleEvents.COMMANDS`, each node gated by `.requires(...)`. Admin permissions default to `op`.

**Config keys are renamed through a migration.** `ConfigMigrations` + `YamlMigrator` rewrite keys on
load so existing installs upgrade cleanly. Renaming a key without a rule silently loses the setting.

**The main chest is never auto-assigned.** `createChest` / `ensureChest` insert with `is_primary = 0`
and a delete does not promote a survivor; only the dialog's "Set as main" sets it. Don't reintroduce
auto-primary — it breaks the "the user explicitly chooses their main" model.

**Telemetry is a facade.** `com.enhancedechest.telemetry.Telemetry` is the only telemetry type the
rest of the plugin may depend on; `telemetry.error(e, "site-label")` always accompanies a log line,
never replaces it. Everything `dev.faststats` stays inside `FastStatsTelemetry`, which resolves to
`Telemetry.NOOP` when no token was baked in — call sites never null-check. Custom metrics are
deliberately just `storage_type` + `language` on both bStats and FastStats; action counters were tried
and removed on request, don't reintroduce them without asking. Metric suppliers run on SDK threads:
keep them pure (immutable/volatile `PluginConfig` reads only, never platform or DB state).

## Conventions

- One class per file; package layout mirrors feature boundaries. Records for data, `final` classes for services.
- Constructor injection by hand, from `EnhancedEchestPlugin`. A new service means: construct it in `onEnable` in the right place in the chain, re-apply its runtime-tunable settings in `reload()`, and shut it down in `onDisable` in the right place in the chain.
- `getSLF4JLogger()` (SLF4J, `{}` placeholders), not `System.out` and not `java.util.logging`.
- Long files are normal here (`ChestDialogs` 730, `ChestActivityLogger` 807, `ChestOpener` 688, `ChestSessionManager` 664, `CachedStorage` 635). Read the surrounding region before editing rather than pattern-matching on one method.
- Comments explain *why a choice is load-bearing*, not what the line does. That is the house style — keep it when editing.

## Documentation and releases

- `CHANGELOG.md` and the VitePress site in `docs/` are end-user facing and follow a deliberately
  plain, non-technical style. Use the `write-docs` skill instead of writing them ad hoc.
- `docs/` deploys to GitHub Pages via `.github/workflows/deploy-docs.yml`; `config.mts` sets
  `base: '/EnhancedEchest/'` — change it (and add `public/CNAME`) if a custom domain is set up.
  Build locally with `cd docs && npm install && npm run docs:build`.
- Version lives in `build.gradle.kts` and is templated into `plugin.yml` / `paper-plugin.yml` at build time.
- `local-docs/` holds internal runbooks (the docker DB test rig, the cross-server two-server rig) and
  is not published.
