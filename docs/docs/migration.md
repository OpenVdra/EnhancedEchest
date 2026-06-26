# Migration

If your players already have items in their **vanilla** ender chests, EnhancedEchest can import that data into its own storage so nothing is lost when you install the plugin.

## How It Works

When a player is migrated, their vanilla ender chest slots are copied into their EnhancedEchest **chest #1**, and the vanilla ender chest is cleared. Each player is migrated **only once** and is skipped on subsequent joins.

## Automatic Migration on Join

To migrate players automatically the first time they join after the plugin is installed, enable it in `config.yml`:

```yaml
migration:
  enabled: true
```

With this on, any un-migrated player has their vanilla ender chest imported the moment they join. Once everyone you care about has logged in, you can turn it back off.

## Manual Migration

Admins can trigger migration on demand for players who are online:

| Command | Effect |
|---------|--------|
| `/ee migrate run <player>` | Migrate a single online player |
| `/ee migrate run all` | Migrate every player currently online |

Both require the `enhancedechest.admin.migrate.run` permission. Players who are already migrated are reported as skipped.

::: warning Online players only
Migration reads the player's live vanilla ender chest, so it only works for players who are **currently online**. Offline players are migrated automatically on their next join if `migration.enabled` is `true`.
:::

## Purpur (and Paper forks) {#purpur}

Purpur's enlarged ender chest (`ender-chest.six-rows` / per-permission rows `purpur.enderchest.rows.<n>`) is supported with no extra setup. Purpur keeps it in the standard ender chest data, so migration captures all rows the player had (up to the full 54 slots), not just the first 27. Just run the migration as above while the server runs on Purpur.
