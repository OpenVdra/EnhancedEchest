# Database

EnhancedEchest stores every ender chest's contents in a database. You choose the backend with the `database.type` option in `config.yml`: [SQLite](/docs/sqlite), [MySQL / MariaDB](/docs/mysql-mariadb), or [PostgreSQL](/docs/postgresql).

| Backend | `type` value | Best for |
|---------|--------------|----------|
| <img src="https://skillicons.dev/icons?i=sqlite" width="20" height="20" alt="SQLite" style="display:inline-block;vertical-align:middle;margin:0 4px 0 0" /> **SQLite** | `sqlite` | Most servers, zero setup, works out of the box |
| <img src="https://skillicons.dev/icons?i=mysql" width="20" height="20" alt="MySQL" style="display:inline-block;vertical-align:middle;margin:0 4px 0 0" /> **MySQL** | `mysql` | Setups that keep their data in an external MySQL server |
| <span style="display:inline-flex;align-items:center;justify-content:center;width:20px;height:20px;background:#242938;border-radius:6px;vertical-align:middle;margin:0 4px 0 0;box-sizing:border-box"><img src="https://cdn.simpleicons.org/mariadb/ffffff" width="14" height="14" alt="MariaDB" style="display:block" /></span> **MariaDB** | `mariadb` | Setups that keep their data in an external MariaDB server |
| <img src="https://skillicons.dev/icons?i=postgres" width="20" height="20" alt="PostgreSQL" style="display:inline-block;vertical-align:middle;margin:0 4px 0 0" /> **PostgreSQL** | `postgres` | Setups already running Postgres |

::: tip No extra installations needed
All database drivers are bundled inside the plugin jar. You do not need to install anything on your server.
:::

## How data is saved

The plugin keeps each **online player's** ender chest data in memory:

- When a player joins, their chests are loaded from the database once.
- Opening and closing chests works entirely from memory, with no database queries during gameplay.
- Changes are written back to the database automatically every **3 minutes** by default (configurable
  with `database.autosave-interval` in `config.yml`), a few seconds after a player quits, and one
  final time when the server shuts down.
- After a player quits and their changes are saved, their data is removed from memory, so memory use
  grows with how many players are online, not with how big your database is.

This makes the plugin very fast regardless of which backend you pick. The trade-off: if the server
process is killed hard (crash, power loss), changes made after the last write-back are lost. That is
at most one autosave interval, and only for players who stayed online the whole time; players who
quit are saved within seconds. Lower `autosave-interval` if you want a smaller window.

```yaml
database:
  # How often in-memory changes are written back to the database. Minimum 30s.
  autosave-interval: 3m
```

## Tables

The plugin creates and manages its own database tables automatically. You never need to write any SQL yourself.

Every table name is prefixed (`echest_` by default) so the plugin's data is easy to tell apart from other
plugins' tables, and safe to keep in a database you share with them:

| Table | Stores |
|-------|--------|
| `echest_enderchests` | Every chest's contents, size, name, and icon. |
| `echest_players` | Each player's settings and their last known username (used for offline lookups, see below). |
| `echest_schema_meta` | The database version, used for automatic upgrades. |

You can change the prefix with `database.table-prefix` in `config.yml`, for example if you run several
servers sharing one database and want a different prefix per server:

```yaml
database:
  table-prefix: echest_
```

If you change it on an existing install, the plugin renames its existing tables to match the next time it
starts, with no data loss. Only letters, digits and underscore are used; anything else is stripped.

### Automatic upgrades

When you update the plugin, it upgrades your existing database automatically on startup. No manual steps, no data loss, existing chests and their contents are always preserved.

As always, keep a backup (the SQLite [auto-backup](/docs/configuration), or your own MySQL/PostgreSQL dump) before a major upgrade, just in case.

### Offline player lookups

`/ee view`, `/ee add`, `/ee resize`, `/ee delete`, and `/ee transfer` can find a player by name even while they are offline, including while you are still typing the name for tab completion. This works automatically once a player has opened their ender chest at least once. Brand new players are found through the server's own player list until then.
