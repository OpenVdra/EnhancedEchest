# Migration

EnhancedEchest can import existing ender chest data from four sources: the **vanilla** ender chest, **AxVaults**, **PlayerVaultsX**, and **CustomEnderChest**. Every `/ee migrate` command requires the `enhancedechest.admin.migrate` permission, runs in the background and reports how many players were imported when it finishes, and is safe to re-run: it never overwrites a chest that already holds items, it just skips it.

## From Vanilla Ender Chests

If your players already have items in their vanilla ender chests, EnhancedEchest imports that data into their **chest #1**. Each player is migrated only once and skipped on later joins.

### Automatic Migration on Join

Enable this in `config.yml` to import every un-migrated player's vanilla ender chest the moment they join:

```yaml
migration:
  enabled: true
```

Once everyone you care about has logged in, you can turn it back off.

### Manual Migration

| Command | Effect |
|---------|--------|
| `/ee migrate vanilla <player>` | Migrate a single online player |
| `/ee migrate vanilla all` | Migrate every player currently online |

::: warning Online players only
Migration reads the player's live vanilla ender chest, so it only works while they are **online**. Offline players are migrated automatically on their next join if `enabled` under `migration` is `true`.
:::

### Purpur (and Paper forks) {#purpur}

Purpur's enlarged ender chest (`ender-chest.six-rows` / `purpur.enderchest.rows.<n>`) is supported with no extra setup, capturing all rows the player had, not just the first 27.

## From AxVaults {#axvaults}

Imports vaults from [AxVaults](https://modrinth.com/plugin/axvaults), including custom names, lore, and enchantments. Tested against **AxVaults 2.15.0**. Vault #1 becomes chest #1, vault #2 becomes chest #2, and so on, each sized to fit its items.

### Before You Start

- **Save AxVaults first.** Run `/vaultadmin save` so every open vault is flushed to disk before migrating.
- **AxVaults must use SQLite.** EnhancedEchest reads its `data.db` file directly. If AxVaults is on a different database, set `type: sqlite` under `database` in `AxVaults/config.yml` and restart the source server first so it creates `data.db`.

### Running It

| Command | Effect |
|---------|--------|
| `/ee migrate axvaults` | Import vaults for every player in the AxVaults database |
| `/ee migrate axvaults <player>` | Import vaults for a single player (online or offline) |

## From CustomEnderChest {#customenderchest}

Imports ender chests from [CustomEnderChest](https://modrinth.com/plugin/custom-ender-chest), including custom names, lore, and enchantments, into the player's **chest #1**. Tested against **CustomEnderChest 2.1.2**.

### Before You Start

- **CustomEnderChest must use YAML storage.** EnhancedEchest reads the per-player files under `CustomEnderChest/playerdata/`, which only exist when `type: yml` is set under `storage` in `CustomEnderChest/config.yml` (it defaults to an embedded `h2` database instead). Switch it to `yml` and restart the source server first; the `mysql` backend isn't read either.

### Running It

| Command | Effect |
|---------|--------|
| `/ee migrate customenderchest` | Import ender chests for every player with CustomEnderChest data |
| `/ee migrate customenderchest <player>` | Import the ender chest for a single player (online or offline) |

## From PlayerVaultsX {#playervaultsx}

Imports vaults from [PlayerVaultsX](https://www.spigotmc.org/resources/playervaultsx.45741/), including custom names, lore, and enchantments. Tested against **PlayerVaultsX 4.4.13**. Vault #1 becomes chest #1, vault #2 becomes chest #2, and so on, each sized to fit its items.

### Before You Start

- **Vaults are saved when closed.** PlayerVaultsX writes a vault to disk when the player closes it, so make sure no one is sitting in an open vault during the migration (restarting the source server, or simply having players log out, flushes everything).
- **Run on a modern Paper server.** Vault data created on 1.20.6+ imports cleanly; data written long ago by an old Spigot server may not decode.

EnhancedEchest looks for the data in `plugins/PlayerVaults` (the plugin's actual jar name); a `plugins/PlayerVaultsX` folder also works as a fallback.

### Running It

| Command | Effect |
|---------|--------|
| `/ee migrate playervaultsx` | Import vaults for every player with PlayerVaultsX data |
| `/ee migrate playervaultsx <player>` | Import vaults for a single player (online or offline) |
