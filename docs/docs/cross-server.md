# Cross-Server Support

Several servers behind a proxy (Velocity, BungeeCord) can share one database, so a player's ender chests follow them between servers. This is off by default. Turn it on with `cross-server.enabled` in `config.yml`.

Every server of the network needs two things:

1. **A shared MySQL, MariaDB, or PostgreSQL database**: the same `database` settings everywhere, including `table-prefix`. SQLite cannot be shared, and the plugin refuses to start in cross-server mode on it.
2. **A shared Redis server.** Redis stores no ender chest data. It only tracks which server currently holds a player's data, so two servers can never overwrite each other's changes.

```yaml
cross-server:
  enabled: true
  # A unique name per server ("survival", "skyblock", ...). Leave empty to generate
  # one automatically at every startup. Never give two servers the same name.
  server-id: "survival"
  redis:
    host: redis.example.com
    port: 6379
    password: ""
    ssl: false
    database: 0
    # Only change this when several separate networks share one Redis server.
    key-prefix: "echest:"
```

While a player is online, their server is the only one allowed to touch their data. When they switch servers, the old server saves their chests to the database and hands them over before the new server reads them, so the new server always sees their latest items. Fast server switching can never lose or duplicate anything. If a server crashes, its claims expire on their own within about 30 seconds.

Notes and limits:

- Changes to the `cross-server` section need a **full server restart**, like the `database` connection settings.
- If Redis is unreachable at startup, the plugin disables itself rather than run unsafely on the shared database.
- Admin commands that target a player who is online on **another** server fail with an error. View or edit them on the server they are playing on instead.
- Run `/ee import` with only one server of the network online.
