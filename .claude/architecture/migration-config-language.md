# Migration, config, language & updates

## Migration (`migration/MigrationService`) — vanilla

`migrateOnline(player)` imports a player's 27-slot vanilla ender chest into their EnhancedEchest chest #1,
in a single main-thread tick: ensure chest #1 exists at full size → copy vanilla contents into its head
slots → save to DB → clear the vanilla EC → set the `migrated` flag. There is never a window where the
items exist in both places. Each player migrates once (`isMigrated` guard). Triggered automatically on
join when `migration.enabled` (`JoinMigrationListener`), or manually with `/ee migrate vanilla <player>|all`.

## Migration (`migration/AxVaultsReader` + `AxVaultsMigrationService`) — AxVaults

Offline-capable import from the AxVaults plugin: `/ee migrate axvaults [<player>]` (`MigrateAxVaultsCommand`,
runs on `DbExecutor`). `AxVaultsReader` opens the AxVaults DB in `plugins/AxVaults` directly — SQLite
`data.db` (read-only, readable while the source server runs) or H2 `data.mv.db` (file-locked while AxVaults
runs, so the source must be stopped or switched to SQLite); the shaded+relocated H2 driver
(`com.enhancedechest.libs.h2`) handles the latter. The `axvaults_data.storage` blob is big-endian
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
