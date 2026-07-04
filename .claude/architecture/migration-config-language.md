# Migration, config, language & updates

## Migration (`migration/MigrationService`) — vanilla

`migrateOnline(player)` imports a player's vanilla ender chest (27 slots; up to 54 on Purpur-likes) into
their EnhancedEchest chest #1. It is **asynchronous** (returns `CompletableFuture<Boolean>`) and runs in
three phases:

1. **Entity thread** — snapshot the vanilla EC contents (cloned stacks, safe to cross threads).
2. **Exclusive DB phase** — `sessions.forceCloseAll(uuid, 1)` then `sessions.runExclusive(uuid, 1, ...)`:
   re-check `isMigrated`, ensure chest #1, **merge** the snapshot into the chest's current contents, and
   commit contents + overflow temp chest + `migrated` flag in **one atomic transaction**
   (`EnderChestStorage.completeMigration`). Three load-bearing choices here:
   - **Exclusivity**: chest #1 may have a live shared session at that moment (player raced `/ec` against
     the join pre-check, or an admin migrated a player mid-edit); a naked write under it would be
     silently undone by that session's close-save. Force-close flushes the session (so the merge reads
     the freshest contents); `runExclusive` makes a concurrent open **wait**, then load the migrated
     contents. Do not replace this with a bespoke lock — it is the same per-(owner, index) serialization
     every other chest mutation uses.
   - **Merge, not overwrite — and never resize**: chest #1 can legitimately hold items by now (deposited
     during the pre-check window, or a pre-existing un-flagged row), and its size is the admin /
     permission domain — migration must not hand out a bigger chest. Fresh chest big enough → vanilla
     layout kept positionally (a migration-created chest is 54 slots, so this is the common case).
     Otherwise → existing items stay put, vanilla stacks fill free slots, and whatever does not fit
     **spills into a temp chest** (same recoverable-overflow UX as shrink/delete/revoke; lifetime =
     `temp-enderchest` config, reload-tunable via `setTempExpiry`). Nothing is ever truncated and the
     merge always succeeds — no retry loop.
   - **Flag atomic with the write**: the merge is **not idempotent** (a re-run would duplicate the
     vanilla items), so "merged but not flagged" must be unobservable — to a crash *and* to a queued
     second migration whose `isMigrated` re-check runs behind this op. The chest UPDATE (contents +
     flag, one row) and the overflow temp-chest INSERT commit in one transaction; never split them
     back into separate `saveChest` + `setMigrated` statements.
3. **Entity thread again** — clear the vanilla EC. Cosmetic-critical only: the DB copy is already
   authoritative and flagged, so a player who logged out mid-way just keeps a stale vanilla copy that is
   unreachable in-game (the plugin intercepts every ender chest open) and is never re-merged.

Triggered automatically on join when `migration.enabled` (`JoinMigrationListener`), or manually with
`/ee migrate vanilla <player>|all` (the command aggregates the futures and reports once all settle;
failure key `migrate.failed`).

The join trigger pre-checks `isMigrated` on the `DbExecutor` first — never on the join thread — so an
already-migrated player (the steady state) costs zero main-thread DB per join, even during a mass
reconnect. Once every player is migrated, turn `migration.enabled` off.

## Migration (`migration/AxVaultsReader` + `AxVaultsMigrationService`) — AxVaults

Offline-capable import from the AxVaults plugin: `/ee migrate axvaults [<player>]` (`MigrateAxVaultsCommand`,
runs on `DbExecutor`). `AxVaultsReader` opens the AxVaults SQLite DB `data.db` in `plugins/AxVaults`
directly (read-only, readable while the source server runs). AxVaults' default H2 backend (`data.mv.db`)
is **not** supported — admins must switch AxVaults to `database.type: sqlite` first; if only `data.mv.db`
is present the reader throws a clear "switch to SQLite" error (no H2 driver is shaded). The
`axvaults_data.storage` blob is big-endian
`[int slotCount]` then per slot `[ushort len][len bytes]`; each item's bytes are gzip-NBT **identical to
Paper `ItemStack.serializeAsBytes`**, so each decodes via `ItemStack.deserializeBytes`. Each vault is
written into the EE chest of the **same index** (`ensureChest`/`resize`+`saveChest`), sized up to a multiple
of 9 (cap 54). **Skip-guard:** a chest that already has `container_data` is never overwritten (reported as
skipped), so the import is idempotent and dupe-safe. AxVaults flushes to its DB only on autosave/quit/
`/vaultadmin save`, so save before importing. Tested against AxVaults 2.15.0.

## Config (`config/PluginConfig`)

Reads `config.yml`: language, `enderchest.default-size`, the `temp-enderchest` block (parsed via
`DurationFormat`), the database block, and the migration flag. Provides `isValidSize` / `sanitizeSize`
(multiple of 9, clamped 9–54).

`ConfigMigrations` defines key-rename rules applied by `YamlMigrator` on load, so existing config/language
files upgrade without manual edits. **When renaming a config/language key, add a rename rule here** rather
than silently changing the key.

Runtime-tunable values are re-applied live on `/ee reload`: `default-size` via
`ChestOpener.setDefaultSize` and temp expiry via `ChestSpillService.setTempExpiry` — they only affect
work started after the call, so it is dupe-safe to reload while saves are in flight. Database-pool settings are bound at startup and require a restart
(a live reload warns if they changed).

## Language (`lang/LanguageManager`)

Loads `language/<locale>/{messages,gui}.yml`, falling back to `en_US` if the locale is missing. `parse()`
auto-detects MiniMessage (string contains `<`) vs legacy `&` codes (with `&#RRGGBB` hex). Default locale
files use legacy `&` codes; the clickable update link stays MiniMessage. Chest titles: custom name shown
verbatim as plain text; otherwise chest #1 uses the un-numbered `enderchest.title` and chests 2+ use
`enderchest.title-numbered` with `{index}`.

`messages.yml` holds chat/action-bar strings; `gui.yml` holds dialog labels. New keys added for the admin
shared-view feature: `chest.in-use`, `chest.view-only`, `admin.view-no-chests` (en_US).

## Updates (`update/`)

`UpdateChecker.checkAsync` runs on a FoliaLib async task at startup; `UpdateNotifyListener` notifies
admins shortly after they join (with a clickable MiniMessage download link).
