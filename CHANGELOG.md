# Changelog

All notable changes to EnhancedEchest are recorded here, newest first.

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
