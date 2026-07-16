# EnhancedEchest — Claude guide

A Paper plugin that replaces the vanilla ender chest with a larger, multi-chest, database-backed
storage system. Players get ender chests of up to **54 slots**, can own several, and manage them
from an in-game dialog. All contents are serialized to SQLite / MySQL / MariaDB / PostgreSQL.

For the full design, read [ARCHITECTURE.md](ARCHITECTURE.md). For user-facing docs, see `docs/`.

## Build & run

```bash
./gradlew build        # compiles + runs shadowJar (the deliverable)
./gradlew shadowJar    # build the relocated fat jar only
./gradlew test         # fast unit tests (excludes the heavy load simulation)
./gradlew stressTest   # 300–500 player concurrency/perf/leak simulation, no server needed
```

- Output jar: `EnhancedEchest-<version>.jar`. `build.gradle.kts` also copies it to a local
  `TestServer/plugins` directory (`shadowJar.destinationDirectory`) — adjust that path if your
  test server lives elsewhere.
- **Test suite is still thin** — most verification is still done by running on a Paper/Folia server.
  Pure-Java logic (storage cache, codec, merge logic) gets plain JUnit 5 tests. Bukkit/Paper-dependent
  code (listeners, scheduler, dialogs, commands) can be unit-tested with **MockBukkit**
  (`testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v1.21:...")` — pinned to the `v1.21`
  artifact line specifically, since it targets paper-api 1.21.11 on Java 21, matching this project;
  the newer `v26.x` artifact line needs Java 25). MockBukkit mocks the Bukkit singleton — sync work
  needs an explicit `server.getScheduler().performOneTick()`/`performTicks(n)`, async work needs
  `server.getScheduler().waitAsyncTasksFinished()`; nothing runs on its own like a real tick loop. See
  `src/test/java/com/enhancedechest/scheduler/SchedulerTest.java` for a working example.

## Stack & constraints

- **Java 21**, **Paper API 1.21.11** (`paper-api:1.21.11-R0.1-SNAPSHOT`; api-version `1.21`),
  Gradle Kotlin DSL, ShadowJar. Compiled against the lowest supported API (1.21.11) so the jar
  runs on **servers 1.21.11 through 26.2** — don't call APIs newer than 1.21.11.
- Paper-only APIs are used (`paper-plugin.yml` bootstrapper, Brigadier commands, Dialog API) —
  the plugin requires **Paper** (or a Paper-compatible fork such as Purpur / Folia) and does not run
  on CraftBukkit.
- All third-party libs are **shaded and relocated** under `com.enhancedechest.libs.*`
  (HikariCP, MariaDB driver, PostgreSQL driver). SQLite driver is `compileOnly`
  (Paper bundles it). Never reference these libs by their original package in new code without
  matching the relocation.
- Base package: `com.enhancedechest`.

## Conventions

- **Threading / Folia:** all scheduling goes through
  `com.enhancedechest.scheduler.Scheduler`, a thin wrapper over Paper's own
  `io.papermc.paper.threadedregions.scheduler.*` API (`Bukkit.getAsyncScheduler()` /
  `getGlobalRegionScheduler()` / `getRegionScheduler()` / `Entity#getScheduler()`), which Paper
  itself implements safely on both plain Paper (main thread) and Folia (region-owning thread) — no
  platform branching needed for dispatch. `runAsync` / `runAtEntity` / `runAtLocation` /
  `runNextTick` take a `Consumer<ScheduledTask>`, **not** a `Runnable`; the `*Later`/`*Timer*`
  variants have `Runnable` overloads. `Scheduler.isFolia()` (class-presence detection of
  `io.papermc.paper.threadedregions.RegionizedServer`) exists only for the one genuine behavioral
  branch (`ChestSessionManager`'s single-viewer-on-Folia vs concurrent-edit-on-Paper rule) — don't
  add new platform branches elsewhere; the scheduler dispatch itself is already cross-platform.
  Never touch entities/blocks off their region thread.
- **Storage is a lazy-load + write-back cache** — the `EnderChestStorage` everyone sees is
  `CachedStorage`: a player's rows are read from SQL once on first touch (join prefetch via the settings
  preload; any cache miss — e.g. an admin command on an offline player — loads on demand inside the
  storage call) and served from memory after that, with identical semantics to the old SQL. Dirty rows
  are flushed by `AutosaveService` (`database.autosave-interval`, default 3m, reload-safe), each quitter
  is written back + evicted ~5s after quit (`flushQuitterLater`), flushed offline owners are evicted
  after each autosave (`evictIdle`), and `CachedStorage.close()` does a final full flush at shutdown.
  Backup and `/ee import` flush first. The **residency invariant is load-bearing**: per-owner ops go
  through `withOwner` (residency re-check + op in one lock hold), dirty ⇒ resident, eviction takes only
  clean owners — don't bypass it. Consequence: **cross-server sharing is unsupported by default**, but
  supported when `cross-server.enabled` (see the cross-server bullet below). Storage methods stay
  synchronous; the
  `com.enhancedechest.service` layer is still the only place allowed to dispatch storage calls onto the
  async executor (shared `DbExecutor`, pool `EnhancedEchest-db`) — that convention is what keeps the
  dupe-safety ordering intact, don't bypass it.
- **Cross-server (`cross-server.enabled`, default off):** a Redis-backed
  `com.enhancedechest.crossserver.CrossServerCoordinator` extends the residency invariant with a
  distributed leg — **resident ⇒ this server holds the owner's Redis lock** (`RedisCoordinator`:
  `SET NX PX`, 30s TTL + heartbeat; acquired in `loadOwner` before the backend read with an `isHeld`
  re-check under the cache lock; released only by the two eviction paths, after the owner flushed
  clean — `beginRelease` under the cache lock / `finishRelease` outside it, keep that split). Handover
  is pub/sub: a waiting server publishes `req` every poll round; the holder flushes+evicts+releases via
  the handler wired in `EnhancedEchestPlugin`, skipped while the player is pinned there or
  `ChestSessionManager.hasActivity(owner)` (the requester just re-asks). Locks are **never stolen** —
  a timed-out acquire throws `CrossServerLockException` (fails like a bad SQL read); a crashed holder's
  locks expire via TTL. Requires mysql/mariadb/postgres + reachable Redis, else the plugin disables
  itself at startup. Single-server mode is `CrossServerCoordinator.NOOP` (never null-check — same
  pattern as `Telemetry`). Jedis is shaded/relocated (`libs.jedis` + `libs.commonspool2` / `libs.json`
  / `libs.gson`). Unit tests: `storage/CrossServerCacheTest`.
- **Dupe-safety is load-bearing** — do not "optimize" away the model: one **shared `Inventory` per open
  chest** (so concurrent viewers can't dupe), load-fresh on first open, save on **last** viewer close,
  pending-save-wait on reopen. All open paths must funnel through `ChestSessionManager.open`; session
  bookkeeping is single-threaded via `onGlobal`. The whole dupe-safety core (the `sessions` registry,
  `runExclusive`, `forceCloseAll`) lives in the one closed class `ChestSessionManager` — keep it there.
  Encoding on save happens sync on the global thread (load-bearing — don't move it off-thread); the DB
  read **and decode** on open both run on the async executor, the global thread only builds the
  inventory. Full detail:
  [architecture/concurrency-and-dupe-safety.md](architecture/concurrency-and-dupe-safety.md).
- **Commands** are registered with Paper Brigadier in `EnhancedEchestBootstrap` (LifecycleEvents.COMMANDS),
  not in `plugin.yml`. Permissions default to `op`.
- **Messages / per-viewer localization:** `LanguageManager` loads **every** bundled locale
  (`en_US`, `vi_VN`) plus any operator-added `language/<locale>/` folder, and at load normalizes each
  string to one MiniMessage string (format auto-detected per string — `<` → MiniMessage, else legacy `&`
  with `&#RRGGBB` hex; `{prefix}` inlined; `{placeholder}` → `<placeholder>` argument tags). `get()`/
  `getGui()`/`getChestTitle()`/`getChestLabel()` return locale-free `Component.translatable(...)` (keys
  `enhancedechest.msg.*` / `enhancedechest.gui.*`); the actual text is resolved by
  `EnhancedEchestTranslator` (a `MiniMessageTranslator` registered **once** on Adventure's
  `GlobalTranslator` in `EnhancedEchestPlugin` enable/disable) against **each recipient client's own
  locale** at send time. Fallback chain: exact → same-language → `language:` (config) → `en_US`. Gated by
  `language-auto-detect` (default on); off ⇒ everyone sees `language:` (legacy single-locale behavior).
  Substitutions pass as `Argument.string`/`Argument.component` (inserted literally, **not** re-parsed —
  a chest name can't inject formatting). Values needing a click/format (the update link) use
  `getRich(...)` — a placeholder inside a `<click:...>` attribute is **not** substituted per-viewer.
  Keys live in `language/<locale>/{messages,gui}.yml`; add a bundled locale to `BUNDLED_LOCALES`.
  - **Which surfaces auto-render (load-bearing):** Paper runs the `GlobalTranslator` per-viewer only for
    **chat** (`sendMessage`) and **inventory window titles** (`createInventory` title) — those keep the
    deferred `Component.translatable` and need no `Locale`. Paper does **NOT** render the **Dialog API**
    (`ChestDialogs`) or inventory **item** names/lore (`ChestListMenu`), so a raw translatable there
    reaches the client as its literal key. For those, render eagerly with the viewer's locale via the
    `get(Locale,…)`/`getGui(Locale,…)`/`getChestLabel(Locale,…)` overloads (which wrap
    `GlobalTranslator.render`); every `ChestDialogs`/`ChestListMenu` builder threads `player.locale()`
    (the detail/rename/icon dialogs get it from `DetailContext.locale()`). Don't drop those `Locale`
    args back to the deferred form — it reintroduces the raw-key bug.
- **Config / language migrations:** `ConfigMigrations` + `YamlMigrator` rename keys on load so existing
  installs upgrade cleanly. Add a rename rule there rather than silently changing a key name.
- **Telemetry (FastStats):** `com.enhancedechest.telemetry.Telemetry` is the only telemetry type the rest
  of the plugin may depend on — handled-error reports via `telemetry.error(e, "site-label")` (rate-limited
  per (site, exception class), always **alongside** the log line, never instead). Everything
  `dev.faststats` stays inside `FastStatsTelemetry`; when no token is baked into `faststats.properties` at
  build time the facade resolves to `Telemetry.NOOP`, so call sites never null-check or branch. Custom
  metrics are deliberately just `storage_type` + `language` (matching bStats; the data source ids must
  exist in the FastStats project dashboard) — action counters were tried and removed on request, don't
  reintroduce without asking. Metric suppliers run on SDK threads: keep them pure and thread-safe
  (immutable/volatile `PluginConfig` reads only, never platform/DB state).
- **Open routing & the "main" chest** (`ChestOpener.open`): `/ec` and right-click decide between
  opening a chest directly vs. showing the `/eclist` management dialog —
  - **0–1 chest** → open it directly (bootstrapping chest #1 if none).
  - **2+ chests + an explicit main flagged + caller has `enhancedechest.command.open`** → open the main directly.
  - **2+ chests otherwise** (no main set, or no permission) → management dialog.
  The main chest is **never auto-assigned**: `createChest`/`ensureChest` insert with `is_primary = 0` and
  deletes do not promote a survivor — it is set only by the dialog's "Set as main" (`setPrimary`). So
  `is_primary` is zero-or-one per player; `getPrimaryIndex` filters non-TEMP (`kind != TEMP`)
  and falls back to the lowest such index when none is flagged (keeps single-chest `/ec` working — and
  lets a PERM chest be opened/set as main). "Real chest" counting in the router is `kind != TEMP` (NORMAL
  **and** PERM). The list marks the main chest with a gold `★` appended to its label (`gui.yml
  dialog.main-tag`); dialog buttons themselves are plain text. Don't reintroduce auto-primary — it breaks
  the "user explicitly chooses their main" model.
- **Per-chest detail dialog & feature toggles** (`ChestDialogs.detailDialog` + `DetailContext`): one
  dialog serves both the owner (`/eclist` edit mode) and an admin (`/ee view`); the `DetailContext` record
  decides the button set and *which owner* every mutation targets (an admin's clicks edit the **target's**
  chest). Appearance edits are gated by **global** config toggles `enderchest.features.{rename,icon,sort}`
  (sort off by default; read live from the shared `PluginConfig`, fields `volatile`) **and** by edit rights
  (owner always; admin needs `enhancedechest.admin.edit`). **Sort** (`ChestSpillService.sortChest`) is
  dupe-safe like `clearChest` (force-close + `runExclusive`, merge-similar then reorder by material key) and
  is per-clicker rate-limited by `enderchest.features.sort-cooldown` in `ChestOpener`. Don't split the admin
  detail back into a separate dialog — it's intentionally the same path. See
  [architecture/ui-dialogs.md](architecture/ui-dialogs.md).
- **Permission-granted chests** (`ChestKind.PERM`, `kind = 2`): players are granted chests from
  `enhancedechest.additional_amount.<count>.slot.<size>` permissions (stacking, summed per size), gated by
  `permission-chests.enabled`. `PermissionChestService.reconcile` runs **on open** (via `ChestOpener`,
  reusing the already-fetched list) to grant/resize/revoke PERM chests against the player's permissions;
  revoked chests spill items to a temp chest. The base NORMAL chest is inviolable (reconcile bootstraps it
  first; never deleted/overridden). To players a PERM chest behaves **exactly** like NORMAL (no tag, no
  hidden buttons); admin commands skip it (`/ee resize` → `admin.cannot-modify-perm`, `/ee delete` is
  NORMAL-only). Reuses the existing `kind` column — no schema change. See
  [architecture/commands-and-permissions.md](architecture/commands-and-permissions.md#permission-granted-chests).

## Docs site

`docs/` is a VitePress site deployed to GitHub Pages by `.github/workflows/deploy-docs.yml`.
`config.mts` sets `base: '/EnhancedEchest/'` for the project page — change it (and add `public/CNAME`)
if a custom domain is set up. Build locally with `cd docs && npm install && npm run docs:build`.
