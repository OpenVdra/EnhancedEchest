# <img src="https://skillicons.dev/icons?i=sqlite" width="28" height="28" alt="SQLite" style="display:inline-block;vertical-align:middle;margin:0 6px 0 0" /> SQLite

SQLite requires no configuration. On first start the plugin creates the database file at `plugins/EnhancedEchest/enderchests.db`.

::: tip Browse your database in the docs
Use the built-in [SQLite Editor](/docs/sqlite-editor) to open an EnhancedEchest database and inspect or edit its tables, player metadata, and chest metadata directly in your browser. The file stays on your device; edits affect only an in-memory copy until you download it. Use an automatic backup or a copy made while the server is stopped rather than copying the live database file.
:::

**Good for:** solo servers and small-to-medium communities (roughly up to a few hundred concurrent players). One file, nothing extra to run or maintain.
**Not a fit if:** you run several servers behind a proxy that need to share player data, since a file can only belong to one server. Use [MySQL / MariaDB](/docs/database/mysql-mariadb) or [PostgreSQL](/docs/database/postgresql) for [Cross-Server](/docs/database/cross-server) support instead.

Docs: [sqlite.org](https://www.sqlite.org/docs.html)

```yaml
database:
  type: sqlite
  sqlite-file: enderchests.db
```

::: info Extra files next to the database
SQLite runs in write-ahead-logging (WAL) mode for better performance under load, so you may also see `enderchests.db-wal` and `enderchests.db-shm` next to the database file. They belong to SQLite: leave them alone, and never copy the `.db` file by hand while the server is running (use the built-in [auto-backup](/docs/configuration/) instead).
:::
