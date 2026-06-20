# EnhancedEChest

EnhancedEChest is a Paper plugin that gives every player a private **54-slot** ender chest instead of the default 27. All items are saved to a database, so storage persists across restarts and world resets.

## Features

- Double-size ender chest (54 slots) opened through the normal ender chest block or `/ec`
- Database-backed storage with support for SQLite (built-in, zero setup) and MySQL / MariaDB
- No item duplication: inventory is loaded fresh on open and saved immediately on close
- Migration system to import existing vanilla ender chest contents on first join
- No external dependencies required on the server; everything is bundled in the jar

## Requirements

- Paper 1.21.11 or newer
- Java 21 or newer

> Spigot and other forks are not supported.

## Installation

1. Download the latest `.jar` from the [Releases](../../releases) page.
2. Drop it into your server's `plugins/` folder.
3. Restart the server.

The plugin creates `plugins/EnhancedEChest/config.yml` and a SQLite database file automatically on first startup.

## Configuration

`plugins/EnhancedEChest/config.yml`:

```yaml
language: en_US              # Locale folder to load messages from

database:
  type: sqlite               # Use "sqlite" or "mysql"
  sqlite-file: enderchests.db

  # Fill these in only if you use MySQL / MariaDB
  host: localhost
  port: 3306
  database: enhancedechest
  username: root
  password: ""
  pool-size: 10

migration:
  enabled: false             # Set to true to auto-import vanilla ender chest data on first join
```

After changing the config, run `/enhancedechest reload` to apply without restarting.

## Commands

| Command | Description |
|---------|-------------|
| `/ec` | Open your ender chest |
| `/enhancedechest reload` | Reload the config |
| `/enhancedechest migrate run <player>` | Migrate a specific online player's vanilla ender chest |
| `/enhancedechest migrate run all` | Migrate all online players at once |

`/enderchest` is an alias for `/ec`. `/ee` is an alias for `/enhancedechest`.

## Permissions

All permissions default to operators only.

| Permission | Description |
|------------|-------------|
| `ee.use` | Open the ender chest via block or command |
| `ee.admin.reload` | Reload the config |
| `ee.admin.migrate.run` | Run migration for a player or all players |

## Language

Messages are loaded from `plugins/EnhancedEChest/language/<locale>/`.

| File | Contents |
|------|----------|
| `messages.yml` | All player-facing messages and the plugin prefix |
| `gui.yml` | Inventory title |

To create a new locale, copy the `en_US` folder, rename it, then set `language: <your-locale>` in `config.yml`.

## Credits

Plugin icon by [m11.dalp.sh](https://m11.dalp.sh/).

## License

This project is licensed under the GPLv3 License - see the [LICENSE](LICENSE) file for details.
