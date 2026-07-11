# Database

EnhancedEchest stores every ender chest's contents in a database. You choose the backend with the `database.type` option in `config.yml`.

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
- Changes are written back to the database automatically every **5 minutes** by default (configurable
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
  autosave-interval: 5m
```

## <img src="https://skillicons.dev/icons?i=sqlite" width="28" height="28" alt="SQLite" style="display:inline-block;vertical-align:middle;margin:0 6px 0 0" /> SQLite (default)

SQLite requires no configuration. On first start the plugin creates the database file at `plugins/EnhancedEchest/enderchests.db`.

```yaml
database:
  type: sqlite
  sqlite-file: enderchests.db
```

::: info Extra files next to the database
SQLite runs in write-ahead-logging (WAL) mode for better performance under load, so you may also see `enderchests.db-wal` and `enderchests.db-shm` next to the database file. They belong to SQLite: leave them alone, and never copy the `.db` file by hand while the server is running (use the built-in [auto-backup](/docs/configuration) instead).
:::

SQLite is a great fit for almost every server: since all gameplay is served from memory, the backend only matters for how the data is stored at rest. Pick MySQL/MariaDB/PostgreSQL if you prefer keeping your data in an external database server (central backups, existing tooling).

## <img src="https://skillicons.dev/icons?i=mysql" width="28" height="28" alt="MySQL" style="display:inline-block;vertical-align:middle;margin:0 6px 0 0" /><span style="display:inline-flex;align-items:center;justify-content:center;width:28px;height:28px;background:#242938;border-radius:8px;vertical-align:middle;margin:0 6px 0 0;box-sizing:border-box"><img src="https://cdn.simpleicons.org/mariadb/ffffff" width="20" height="20" alt="MariaDB" style="display:block" /></span> MySQL / MariaDB

Point the plugin at an existing MySQL or MariaDB database:

```yaml
database:
  type: mysql          # or: mariadb
  host: localhost
  port: 3306
  database: enhancedechest
  username: root
  password: "your-password"
  pool-size: 10
```

- Create the database (schema) beforehand, for example `CREATE DATABASE enhancedechest;`
- The plugin creates and manages its own tables automatically

## <img src="https://skillicons.dev/icons?i=postgres" width="28" height="28" alt="PostgreSQL" style="display:inline-block;vertical-align:middle;margin:0 6px 0 0" /> PostgreSQL

```yaml
database:
  type: postgres
  host: localhost
  port: 5432
  database: enhancedechest
  username: postgres
  password: "your-password"
  pool-size: 10
```

The default PostgreSQL port is **5432**, so remember to change `port` from the MySQL default.

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
starts — no data is lost. Only letters, digits and underscore are used; anything else is stripped.

### Automatic upgrades

When you update the plugin, it upgrades your existing database automatically on startup. No manual steps, no data loss, existing chests and their contents are always preserved.

As always, keep a backup (the SQLite [auto-backup](/docs/configuration), or your own MySQL/PostgreSQL dump) before a major upgrade, just in case.

### Offline player lookups

`/ee view`, `/ee add`, `/ee resize`, `/ee delete`, and `/ee transfer` can find a player by name even while they are offline, including while you are still typing the name for tab completion. This works automatically once a player has opened their ender chest at least once. Brand new players are found through the server's own player list until then.

## Cross-Server Support

**Sharing one database between several servers is not supported.** Each server keeps its own copy of an online player's data in memory and writes it back on its own schedule, so two servers pointed at the same database (for example behind a proxy with fast server switching) could overwrite each other's changes and lose or duplicate items.

Run each server with its own database (or its own SQLite file). Cross-server ender chest support will not be available for now.

## Switching Backends

You can move all of your existing data from one backend to another with the built-in `/ee import` command, for example from SQLite to MySQL, or from MySQL to PostgreSQL.

The idea is simple: you make the **new** backend the active one, then import the **old** one into it.

1. Stop the server and make sure **no players are online** during the whole process.
2. Edit `config.yml` so `database.type` (and the connection fields) point at the **new** backend, the one you want to move to. Leave the old backend's files/database untouched.
3. Start the server. The plugin creates its empty tables in the new backend.
4. Run `/ee import`. A form opens; fill in the connection details of the **old** backend you are copying *from*:
   - **Type**: click the button to toggle between **SQLite** (a file) and **Server**. The three server engines (MySQL, MariaDB, PostgreSQL) share the same form, so they are one choice; the engine is picked from the port (use **5432** for PostgreSQL, otherwise it connects as MySQL/MariaDB).
   - For SQLite, the **SQLite file** (a path, absolute or relative to `plugins/EnhancedEchest`).
   - For Server, the **host** (as `host` or `host:port`), **database name**, **username**, and **password**.
5. Press **Start import**. When it finishes, the number of players and chests copied is reported in chat.

The copy is byte-for-byte, so it is fast and every chest's contents, size, name, icon, and settings carry over exactly.

::: warning Before you import, read these
- **Update the old backend first.** Load the old database with *this* version of the plugin once before importing, so its schema is current. Importing from an outdated schema fails with a "source schema outdated" error and copies nothing.
- **No players online.** The import refuses to run while anyone other than you is connected. Contents can only be copied safely on a quiet server.
- **The destination must be empty.** Import only works into a fresh database with no chests yet. This prevents accidentally merging or overwriting existing data.
:::

::: tip If an import fails
Nothing is written unless the whole copy succeeds (it runs in a single transaction). If it fails part-way, the destination is left empty: just fix the reported problem, drop the destination's tables (or delete the SQLite file) so it is fresh again, restart, and re-run `/ee import`.
:::

The [vanilla migration](/docs/migration) and the AxVaults / PlayerVaultsX imports are separate features for pulling data in from *other* plugins.
