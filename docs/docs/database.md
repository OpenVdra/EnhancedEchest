# Database

EnhancedEchest stores every ender chest's contents in a database. You choose the backend with the `database.type` option in `config.yml`.

| Backend | `type` value | Best for |
|---------|--------------|----------|
| <img src="https://skillicons.dev/icons?i=sqlite" width="20" height="20" alt="SQLite" style="display:inline-block;vertical-align:middle;margin:0 4px 0 0" /> **SQLite** | `sqlite` | Single servers, zero setup, works out of the box |
| <img src="https://skillicons.dev/icons?i=mysql" width="20" height="20" alt="MySQL" style="display:inline-block;vertical-align:middle;margin:0 4px 0 0" /> **MySQL** | `mysql` | Networks sharing one database |
| <span style="display:inline-flex;align-items:center;justify-content:center;width:20px;height:20px;background:#242938;border-radius:6px;vertical-align:middle;margin:0 4px 0 0;box-sizing:border-box"><img src="https://cdn.simpleicons.org/mariadb/ffffff" width="14" height="14" alt="MariaDB" style="display:block" /></span> **MariaDB** | `mariadb` | Networks sharing one database |
| <img src="https://skillicons.dev/icons?i=postgres" width="20" height="20" alt="PostgreSQL" style="display:inline-block;vertical-align:middle;margin:0 4px 0 0" /> **PostgreSQL** | `postgres` | Setups already running Postgres |

::: tip No extra installations needed
All database drivers are bundled inside the plugin jar. You do not need to install anything on your server.
:::

## <img src="https://skillicons.dev/icons?i=sqlite" width="28" height="28" alt="SQLite" style="display:inline-block;vertical-align:middle;margin:0 6px 0 0" /> SQLite (default)

SQLite requires no configuration. On first start the plugin creates the database file at `plugins/EnhancedEchest/enderchests.db`.

```yaml
database:
  type: sqlite
  sqlite-file: enderchests.db
```

::: info Extra files next to the database
SQLite runs in write-ahead-logging (WAL) mode for better performance under load, so you may also see `enderchests.db-wal` and `enderchests.db-shm` next to the database file. They belong to SQLite — leave them alone, and never copy the `.db` file by hand while the server is running (use the built-in [auto-backup](/docs/configuration) instead).
:::

For busy servers — roughly 100+ concurrent players — or several servers sharing one database, MySQL/MariaDB is the better choice.

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

| Table | Stores |
|-------|--------|
| `enderchests` | Every chest's contents, size, name, and icon. |
| `players` | Each player's settings and their last known username (used for offline lookups, see below). |
| `schema_meta` | The database version, used for automatic upgrades. |

### Automatic upgrades

When you update the plugin, it upgrades your existing database automatically on startup. No manual steps, no data loss, existing chests and their contents are always preserved.

As always, keep a backup (the SQLite [auto-backup](/docs/configuration), or your own MySQL/PostgreSQL dump) before a major upgrade, just in case.

### Offline player lookups

`/ee view`, `/ee add`, `/ee resize`, `/ee delete`, and `/ee transfer` can find a player by name even while they are offline, including while you are still typing the name for tab completion. This works automatically once a player has opened their ender chest at least once. Brand new players are found through the server's own player list until then.

## Sharing Data Across Servers

Pointing several servers at the **same** MySQL/MariaDB/PostgreSQL database lets them share ender chest storage. Players see the same contents regardless of which server they log in to, as long as they are only on one server at a time.

## Switching Backends

You can move all of your existing data from one backend to another with the built-in `/ee import` command — for example from SQLite to MySQL, or from MySQL to PostgreSQL.

The idea is simple: you make the **new** backend the active one, then import the **old** one into it.

1. Stop the server and make sure **no players are online** during the whole process.
2. Edit `config.yml` so `database.type` (and the connection fields) point at the **new** backend — the one you want to move to. Leave the old backend's files/database untouched.
3. Start the server. The plugin creates its empty tables in the new backend.
4. Run `/ee import`. A form opens; fill in the connection details of the **old** backend you are copying *from*:
   - **Type** — click the button to toggle between **SQLite** (a file) and **Server**. The three server engines (MySQL, MariaDB, PostgreSQL) share the same form, so they are one choice; the engine is picked from the port — use **5432** for PostgreSQL, otherwise it connects as MySQL/MariaDB.
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
Nothing is written unless the whole copy succeeds (it runs in a single transaction). If it fails part-way, the destination is left empty — just fix the reported problem, drop the destination's tables (or delete the SQLite file) so it is fresh again, restart, and re-run `/ee import`.
:::

The [vanilla migration](/docs/migration) and the AxVaults / PlayerVaultsX imports are separate features for pulling data in from *other* plugins.
