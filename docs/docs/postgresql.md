# <img src="https://skillicons.dev/icons?i=postgres" width="28" height="28" alt="PostgreSQL" style="display:inline-block;vertical-align:middle;margin:0 6px 0 0" /> PostgreSQL

**Good for:** the same cases as [MySQL / MariaDB](/docs/mysql-mariadb), larger servers and networks needing [Cross-Server](/docs/cross-server), if Postgres is already your database of choice.
**Not a fit if:** you just want something that works with zero setup, [SQLite](/docs/sqlite) is simpler for that.

Docs: [postgresql.org](https://www.postgresql.org/docs/)

```yaml
database:
  type: postgres
  host: localhost
  port: 5432
  database: enhancedechest
  username: postgres
  password: "your-password"
  ssl: disable
  pool-size: 10
```

- **Create the empty database first**, for example `CREATE DATABASE enhancedechest;`. The plugin creates and manages its own tables inside it automatically, but the database itself has to exist before it can connect.
- The default PostgreSQL port is **5432**, so remember to change `port` from the MySQL default.
- Set `ssl` to `require` or `verify-full` to encrypt the connection, see [SSL / TLS](/docs/ssl-tls).
