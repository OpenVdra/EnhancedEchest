# <img src="https://skillicons.dev/icons?i=mysql" width="28" height="28" alt="MySQL" style="display:inline-block;vertical-align:middle;margin:0 6px 0 0" /><span style="display:inline-flex;align-items:center;justify-content:center;width:28px;height:28px;background:#242938;border-radius:8px;vertical-align:middle;margin:0 6px 0 0;box-sizing:border-box"><img src="https://cdn.simpleicons.org/mariadb/ffffff" width="20" height="20" alt="MariaDB" style="display:block" /></span> MySQL / MariaDB

Point the plugin at an existing MySQL or MariaDB database:

**Good for:** larger servers and multi-server networks, especially if you need [Cross-Server](/docs/database/cross-server) support or already run MySQL/MariaDB for other plugins. Handles many concurrent connections comfortably.
**Not a fit if:** you just want something that works with zero setup, [SQLite](/docs/database/sqlite) is simpler for that.

Docs: [MySQL](https://dev.mysql.com/doc/) / [MariaDB](https://mariadb.com/kb/en/documentation/)

```yaml
database:
  type: mysql          # or: mariadb
  host: localhost
  port: 3306
  database: enhancedechest
  username: root
  password: "your-password"
  ssl: disable
  pool-size: 10
```

- **Create the empty database first**, for example `CREATE DATABASE enhancedechest;`. The plugin creates and manages its own tables inside it automatically, but the database itself has to exist before it can connect.
- Set `ssl` to `require` to encrypt the connection (fails if the server does not support TLS), or to `verify-full` to also verify the server certificate and hostname. See [SSL / TLS](/docs/database/ssl-tls).
