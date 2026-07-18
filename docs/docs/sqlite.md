# <img src="https://skillicons.dev/icons?i=sqlite" width="28" height="28" alt="SQLite" style="display:inline-block;vertical-align:middle;margin:0 6px 0 0" /> SQLite

SQLite requires no configuration. On first start the plugin creates the database file at `plugins/EnhancedEchest/enderchests.db`.

**Good for:** solo servers and small-to-medium communities (roughly up to a few hundred concurrent players). One file, nothing extra to run or maintain.
**Not a fit if:** you run several servers behind a proxy that need to share player data, since a file can only belong to one server. Use [MySQL / MariaDB](/docs/mysql-mariadb) or [PostgreSQL](/docs/postgresql) for [Cross-Server](/docs/cross-server) support instead.

Docs: [sqlite.org](https://www.sqlite.org/docs.html)

```yaml
database:
  type: sqlite
  sqlite-file: enderchests.db
```

::: info Extra files next to the database
SQLite runs in write-ahead-logging (WAL) mode for better performance under load, so you may also see `enderchests.db-wal` and `enderchests.db-shm` next to the database file. They belong to SQLite: leave them alone, and never copy the `.db` file by hand while the server is running (use the built-in [auto-backup](/docs/configuration) instead).
:::
