# Database

EnhancedEchest stores every ender chest's contents in a database. You choose the backend with the `database.type` option in `config.yml`.

| Backend | `type` value | Best for |
|---------|--------------|----------|
| **SQLite** | `sqlite` | Single servers, zero setup, works out of the box |
| **MySQL** | `mysql` | Networks sharing one database |
| **MariaDB** | `mariadb` | Networks sharing one database |
| **PostgreSQL** | `postgres` | Setups already running Postgres |

::: tip No extra installations needed
All database drivers are bundled inside the plugin jar. You do not need to install anything on your server.
:::

## SQLite (default)

SQLite requires no configuration. On first start the plugin creates the database file at `plugins/EnhancedEchest/enderchests.db`.

```yaml
database:
  type: sqlite
  sqlite-file: enderchests.db
```

## MySQL / MariaDB

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

## PostgreSQL

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

## Tables & Schema

The plugin creates and manages its own tables — you never write any SQL yourself. On the current version these are:

| Table | Purpose |
|-------|---------|
| `enderchests` | One row per chest (contents, size, name, icon, kind, expiry) — the core storage. |
| `players` | One row per player: preferences (edit-mode), the permission-managed base-chest size baseline, and their most recent in-game name (for resolving **offline** players — see below). |
| `schema_meta` | Records the schema version the database is on, used by the automatic upgrader below. |

### Automatic, versioned upgrades

When you update the plugin, its database schema can change (new columns, renamed/merged tables). EnhancedEchest upgrades an existing database **automatically and safely** on startup:

- A fresh database is created directly at the latest schema.
- An existing database is compared against the version recorded in `schema_meta`, and only the newer migration steps are applied. Each step first checks whether its change is already present (column exists, old table is gone, etc.), so a re-run — or a partially-upgraded database — never errors.
- Every migration is additive — existing rows and their contents are preserved. (Upgrading from a version before 1.0.4, the old `player_settings` table is merged into `players` and then dropped.)

No manual migration or `ALTER TABLE` is ever required. As always, keep a backup (the SQLite [auto-backup](/docs/configuration), or your own MySQL/Postgres dump) before a major upgrade, just in case.

### Offline player lookups

`/ee view`, `/ee add`, `/ee resize`, `/ee delete`, and `/ee transfer` can find a player by name while they are offline, including while you are still typing the name for tab completion. This uses the plugin's own record of player names, kept up to date automatically the first time each player opens their ender chest. A brand new player is found this way after their first login and chest open; before that, the server's own player list is used instead.

## Sharing Data Across Servers

Pointing several servers at the **same** MySQL/MariaDB/PostgreSQL database lets them share ender chest storage. Players see the same contents regardless of which server they log in to, as long as they are only on one server at a time.

## Switching Backends

To move to a different backend, change `database.type` (and the connection fields) and restart the server. Existing data is not copied automatically between backends; only the [vanilla migration](/docs/migration) import is automated.
