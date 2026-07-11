# Changelog

All notable changes to EnhancedEchest are recorded here, newest first.

## 1.0.9 - 2026-07-11

### Changed

- Icon picker names now show in your Minecraft client's own language instead of English, matching how item names look in your inventory. Searching by name also matches the localized name when your client is set to English or Vietnamese; other client languages currently fall back to matching the English name.

## 1.0.8 - 2026-07-07

This release changes how data is stored while the server runs: each player's ender chests are loaded into memory when they join and served from there, so opening and closing chests runs no database queries, no matter which backend you use.

### Changed

- **Ender chest data is now cached in memory per player.** A player's chests are loaded from the database once, when they join, and every operation after that is served from memory. Changes are written back automatically every 5 minutes (new `database.autosave-interval` setting, minimum 30s, applies on `/ee reload`), a few seconds after a player quits, and one final time at server shutdown. Memory use stays proportional to the number of online players, not the size of the database.
  - Admin commands on offline players (`/ee view`, `/ee resize`, `/ee transfer`, ...) still work exactly as before: that player's data is loaded on demand and released again after the next autosave.
  - Automatic backups and `/ee import` always write pending changes first, so they still see complete data.
- **Sharing one database between several servers is not supported.** Each server holds its own authoritative copy of an online player's data and writes it back on its own schedule, so two servers on the same database (for example behind a proxy with fast server switching) could overwrite each other. Run each server with its own database or SQLite file; cross-server ender chest support will not be available for now.

## 1.0.7 - 2026-07-05

**Critical bug fix: item duplication. Please update immediately, especially on Folia.**

### Fixed

- **Critical:** fixed an item duplication exploit triggered by re-opening an ender chest rapidly (spam right-clicking the block, or mixing `/ec` with a right-click while the chest was still opening). The overlapping opens could silently disconnect the on-screen chest from saving: items taken out afterwards were never removed from the database and reappeared on the next open.
  - Easiest to trigger on Folia, where opening a chest takes longer, but the window existed on Paper too.
  - Fixed at every layer: a single right-click can no longer start two opens, duplicate open requests are collapsed into one, and a chest window can no longer outlive its save tracking.
- Fixed spam right-clicking an ender chest playing the lid open/close sound over and over. A duplicate open request arriving while the chest is already open (or still loading) is now ignored instead of closing and re-opening the chest. This also removes the pointless save/load each of those cycles caused.
- Fixed the update notification's download line showing raw colour codes (for example `&#9B59B6EnhancedEchest &8» &r`) instead of the formatted plugin prefix. Messages that mix the `&`-code prefix with MiniMessage text (like the clickable download link) now format both parts correctly.

## 1.0.6 - 2026-07-04

This release adds a built-in tool to move all your data from one database backend to another, plus a set of performance improvements aimed at servers with many players online.

### Added

- Added coloured chest names: players can now format a chest's custom name with `&` colour codes, `&#RRGGBB` hex, and cosmetic MiniMessage tags such as `<red>`, `<gradient>`, and `<rainbow>`.
  - Interactive MiniMessage tags (`<click>`, `<hover>`, `<insertion>`, …) are always stripped, so a name can never run a command or forge a tooltip.
  - Controlled by the new `enderchest.features.rename-colors` toggle (on by default); turn it off to show names exactly as typed.
- Added a name blacklist for chest renaming: set `enderchest.features.rename-blacklist` in `config.yml` to a list of words players may not use in a chest's custom name (for example `server`, `admin`, `staff`, `owner`).
  - Matching is case-insensitive and by substring, so a banned word like `admin` also blocks names such as `iAmAdmin` or `ADMIN`.
  - The check runs against the visible text, so colour codes can't be used to hide a banned word.
  - A rename containing a banned word is rejected before it is saved, and the player is asked to choose a different name.
  - Leave the list empty to allow any name; clearing a chest's name is always allowed.
- Added `/ee import` to copy every player's chests from an old database backend into the one your server is currently using, for example when moving from SQLite to MySQL, or between MySQL and PostgreSQL.
  - Point `config.yml` at the new (empty) backend, restart, then run `/ee import` and fill in the old database's connection details in the dialog.
  - The copy is byte-for-byte, so item contents, sizes, names, icons, and settings all carry over exactly, and it stays fast even for large databases.
  - Safety checks before it runs: no other players may be online, the source cannot be the database you are already on, and the destination must be empty.
  - Everything is copied in a single transaction, so a failure part-way leaves the destination untouched: just fix the problem and run it again.
  - Gated by the new `enhancedechest.admin.import` permission.

### Fixed

- Fixed `/ee migrate` appearing in tab-completion for players without the `enhancedechest.admin.migrate` permission. The command still could not be run, but it should not have been suggested; the whole `/ee migrate` subtree is now hidden unless the player has the permission.
- Fixed a rare case where migrating a player's vanilla ender chest (on join with `migration.enabled`, or via `/ee migrate vanilla`) while that player had their ender chest open could lose the migrated items. An ender chest opened during a migration now simply waits for it and then shows the migrated items.
- Fixed vanilla migration replacing whatever was already stored in chest #1: it now merges the vanilla items into free slots, anything that does not fit is moved to a recoverable temporary chest (the chest is never resized), and running the migration twice can no longer duplicate or drop anything.

### Improved

- Improved join performance when `migration.enabled` is on: the already-migrated check no longer runs on the main server thread, so mass reconnects after a restart no longer cause lag.
- Improved chest opening on busy servers: a chest's contents are now read and prepared fully in the background with fewer database queries, so opening chests adds less load to the server tick.
- Improved SQLite write performance by switching the database to write-ahead logging. You may see new `enderchests.db-wal` and `enderchests.db-shm` files next to the database file; they belong to SQLite and should be left in place.
- Improved SQLite reliability during automatic backups: a save that arrives while a backup is being written now waits for it to finish instead of failing.

## 1.0.5 - 2026-07-03

### Fixed

- Fixed an error that could prevent the plugin from starting when `database.type` is set to `mysql`, `mariadb`, or `postgres`, failing with `No suitable driver` even with a correct `host`, `port`, `database`, `username`, and `password`.

## 1.0.4 - 2026-07-02

This release lets you set a player's base ender chest size by rank, makes `/ee view` reliable for offline players, and adds an automatic, versioned database upgrader.

### Added

- Added the `enhancedechest.default_size.<size>` permission to set a player's base ender chest size by rank, separate from `enderchest.default-size`. For example, `enhancedechest.default_size.54` gives that player a 54-slot base chest.
  - If a player holds more than one, the largest size wins.
  - The chest resizes itself as soon as the permission changes. Growing it keeps every item; shrinking it moves anything that no longer fits into a recoverable temporary chest.
  - While set this way, `/ee resize` cannot change that chest, the same as a permission-granted chest. Works for offline players too.
  - Applies the next time the player opens their chest, no relog needed, with nothing to configure.
- `/ee view`, `/ee add`, `/ee resize`, `/ee delete`, and `/ee transfer` now find offline players reliably, including while typing a name for tab completion, instead of depending on the server's own player list.

### Changed

- Upgrading the plugin now updates your database automatically. Your existing chests and settings are always kept, and no manual steps are needed.

## 1.0.3 - 2026-06-29

This release adds one-click chest sorting, lets you turn the chest customization buttons on or off server-wide, and gives admins the full chest menu while viewing another player.

### Added

- Added a **Sort** button to a chest's management menu that tidies it in one click: identical items are merged into full stacks and the whole chest is reordered by item type.
  - Off by default. Turn it on with `enderchest.features.sort: true` in `config.yml`.
  - Has a per-player cooldown (default `10s`, configurable with `enderchest.features.sort-cooldown`) so it can't be spammed.
- Added server-wide on/off switches under `enderchest.features` in `config.yml` for the chest customization buttons. Each switch applies to every player the same way.
  - `rename` (default on): show or hide the **Rename** button.
  - `icon` (default on): show or hide the **Choose icon** button.
  - `sort` (default off): show or hide the **Sort** button.

### Changed

- `/ee view <player>` now opens the **same** management menu the chest's owner sees, instead of a stripped-down view-only one:
  - Admins with `enhancedechest.admin.edit` can now **Rename**, **Choose icon**, and **Sort** the chest they are viewing (these follow the same `enderchest.features` switches).
  - The **Clear chest** button now lives in that same menu, so everything is in one place.
  - A view-only admin (`enhancedechest.admin.view` only) still sees just Open and Back.

## 1.0.2 - 2026-06-27

This release focuses on importing existing data from other vault plugins, and adds an account-transfer command plus an admin tool to empty a chest.

### Added

- Added migration from the `AxVaults` plugin. Items keep their custom names, lore, and enchantments, and each vault maps to the EnhancedEchest chest with the same number.
  - `/ee migrate axvaults` imports every player in the AxVaults database.
  - `/ee migrate axvaults <player>` imports a single player, online or offline.
- Added migration from the `PlayerVaultsX` plugin, read directly from its vault files. Works for offline players.
  - `/ee migrate playervaultsx` imports every player with PlayerVaultsX data.
  - `/ee migrate playervaultsx <player>` imports a single player, online or offline.
- Added `/ee transfer <from> <to> <#index | name | all>` to move a player's ender chests onto another account, for when someone switches accounts.
  - Use `all` to move every chest, or a `#index` or chest name to move a single one.
  - The destination ends up with exactly the source's chests: nothing from the destination account is stacked on top, and the source's chests are removed so items are never duplicated.
  - If the destination already holds items in a chest the transfer would replace, add `override` to discard them or `temp` to move them to recoverable temporary storage.
  - Gated by the new `enhancedechest.admin.transfer` permission.
- Added a **Clear chest** button to the `/ee view` menu that empties a chest's contents. It is visible only to admins with the new `enhancedechest.admin.clear` permission, is tagged with a red `(Admin)` label, and asks for confirmation before wiping anything.

### Changed

- **Breaking:** `/ee migrate run` is now `/ee migrate vanilla`, and `/ee migrate run all` is now `/ee migrate vanilla all`. Update any console scripts or command blocks that call the old name.
- **Breaking:** the migration permission node `enhancedechest.admin.migrate.run` is now `enhancedechest.admin.migrate`. Re-grant this node to your staff.
- `/ee view <player>` and `/ee view <player> <index>` now open a per-chest menu (with Open, and Clear chest for admins who have the permission) instead of opening the inventory immediately. Click Open in the menu to open the chest.

### Improved

- Improved vanilla migration on `Purpur` and Paper forks: enlarged ender chests are now imported in full, up to all 54 slots, instead of only the first 27.
- Improved the update checker so it falls back to GitHub releases when Modrinth cannot be reached, pointing players to the GitHub download instead.
- Improved how chest contents are stored so saved data is more compact.
