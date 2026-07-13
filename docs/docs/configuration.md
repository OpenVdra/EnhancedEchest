# Main Configuration

The `config.yml` file lives in `plugins/EnhancedEchest/`. It controls language, chest size, temporary chests, the storage backend, cross-server mode, automatic backups, and migration behavior.

::: tip Apply changes without a restart
After editing `config.yml`, run `/ee reload` in-game or from the console to apply your changes.
:::

<div style="background-color: var(--vp-c-bg-alt); padding: 20px; border-radius: 12px; margin-top: 20px;">

<ConfigProperty name="language" value="en_US" type="string">

Language folder to load from <code>plugins/EnhancedEchest/language/</code>. The plugin ships with <code>en_US</code> (English). To add a translation, copy the <code>en_US</code> folder, rename it, translate the files inside, and set this option to the new folder name.<br><br>
See the <a href="/docs/language">Language</a> page for the full list of message keys.

</ConfigProperty>

<ConfigGroup name="enderchest">
<template #info>
Controls the ender chests themselves.
</template>

<ConfigProperty name="default-size" value="54" type="number">
Slot count of the chest that is auto-created the first time a player ever opens their ender chest. Must be a multiple of <code>9</code>, between <code>9</code> and <code>54</code>. Invalid values are rounded to the nearest valid size.<br><br>

| Value | Rows |
|-------|------|
| <code>9</code> | 1 |
| <code>18</code> | 2 |
| <code>27</code> | 3 (vanilla size) |
| <code>36</code> | 4 |
| <code>45</code> | 5 |
| <code>54</code> | 6 (double chest) |

You can also override the base chest size <strong>per player</strong> with the <code>enhancedechest.default_size.&lt;size&gt;</code> permission (always available, no config needed). See the <a href="/docs/permissions#default-size-permission">Permissions</a> page.

</ConfigProperty>

<ConfigProperty name="features.rename" value="true" type="boolean">
Whether players may give a chest a custom display name from the <strong>Edit mode</strong> menu. Turning this off hides the <strong>Rename</strong> button; chests that already have a name keep it. This is a <strong>global</strong> switch, it applies to every player the same way.
</ConfigProperty>

<ConfigProperty name="features.icon" value="true" type="boolean">
Whether players may pick an item to show as a chest's icon in the list. Turning this off hides the <strong>Choose icon</strong> button; chests that already have an icon keep it. Global switch.
</ConfigProperty>

<ConfigProperty name="features.sort" value="false" type="boolean">
Whether players may auto-sort a chest from the <strong>Edit mode</strong> menu. When on, a <strong>Sort</strong> button appears that merges identical items into full stacks and reorders the whole chest by item type. Off by default. Global switch.
</ConfigProperty>

<ConfigProperty name="features.sort-cooldown" value="10s" type="string">
Smallest gap between two sorts by the same player, so the <strong>Sort</strong> button can't be spammed (each sort re-reads and re-writes the chest). Time format: <code>20s</code>, <code>5m</code>, <code>1h</code>, … Set to <code>0s</code> to remove the cooldown. Only used when <code>features.sort</code> is on.
</ConfigProperty>

<ConfigProperty name="features.rename-blacklist" :value="['server', 'admin', 'staff', 'owner']" type="list">
Words players may not use in a chest's custom name. Matching is <strong>case-insensitive</strong> and by <strong>substring</strong>, so <code>admin</code> also blocks <code>iAmAdmin</code> and <code>ADMIN</code>. A rename containing any listed word is rejected before it is saved and the player is asked to choose another. The check runs against the <em>visible</em> text, so colour codes can't be used to hide a banned word. Leave the list empty to allow any name; clearing a chest's name (saving it blank) is always allowed.
</ConfigProperty>

<ConfigProperty name="features.rename-colors" value="true" type="boolean">
Whether players may colour their chest names. When <code>true</code>, names accept legacy <code>&amp;</code> colour codes, <code>&amp;#RRGGBB</code> hex, and cosmetic <a href="https://docs.advntr.dev/minimessage/format.html" target="_blank">MiniMessage</a> tags such as <code>&lt;red&gt;</code>, <code>&lt;gradient&gt;</code>, <code>&lt;rainbow&gt;</code>, and <code>&lt;bold&gt;</code>. Interactive tags (<code>&lt;click&gt;</code>, <code>&lt;hover&gt;</code>, <code>&lt;insertion&gt;</code>, …) are <strong>always stripped</strong>, so a name can never run a command or forge a tooltip. When <code>false</code>, names are shown exactly as typed and any codes appear as plain text. Global switch.
</ConfigProperty>

</ConfigGroup>

<ConfigGroup name="permission-chests">
<template #info>
Controls ender chests granted automatically from permissions. See the <a href="/docs/permissions#permission-granted-chests">Permissions</a> page for the node format and behavior.
</template>

<ConfigProperty name="enabled" value="true" type="boolean">
When <code>true</code>, players are granted ender chests from <code>enhancedechest.additional_amount.&lt;count&gt;.slot.&lt;size&gt;</code> permissions. Grants are synced each time a player opens their ender chest; losing a node removes those chests, spilling any items into a recoverable temporary chest. Players always keep their base chest. Setting this to <code>false</code> stops syncing but leaves already-granted chests in place.<br><br>
See the <a href="/docs/permissions#permission-granted-chests">Permissions</a> page for full details.
</ConfigProperty>

</ConfigGroup>

<ConfigGroup name="temp-enderchest">
<template #info>
Controls temporary chests. When a chest is shrunk, deleted (without <code>force</code>), or expires with items still inside, those items move into a temporary chest that disappears once emptied or once it expires. Temporary chests are take-only: players can take items out but not put new ones in.
</template>

<ConfigProperty name="expiry" value="24h" type="string">
How long a temporary chest lasts before it expires, along with any items still inside it. Time format: <code>20s</code>, <code>5m</code>, <code>1h</code>, or combined like <code>1d_2h_30m</code>. Units: <code>s m h d w mo y</code>.
</ConfigProperty>

<ConfigProperty name="check-interval" value="5m" type="string">
How often the plugin scans for expired chests. Lower values remove expired chests sooner; the default is fine for almost every server.
</ConfigProperty>

<ConfigProperty name="deny-sound.enabled" value="true" type="boolean">
Whether to play a sound when a player tries to put an item into a take-only temporary chest. Set to <code>false</code> for no sound.
</ConfigProperty>

<ConfigProperty name="deny-sound.key" value="minecraft:entity.villager.no" type="string">
Which sound to play. Accepts any Minecraft sound id; the default is the villager "no" grunt.
</ConfigProperty>

</ConfigGroup>

<ConfigGroup name="database">
<template #info>
Configures where ender chest contents are stored. SQLite works out of the box with no setup. See the <a href="/docs/database">Database</a> page for MySQL, MariaDB, and PostgreSQL setup.
</template>

<ConfigProperty name="type" value="sqlite" type="string">
Storage backend. Supported values: <code>sqlite</code>, <code>mysql</code>, <code>mariadb</code>, <code>postgres</code>.
</ConfigProperty>

<ConfigProperty name="table-prefix" value="echest_" type="string">
Prepended to every table this plugin creates (e.g. <code>echest_enderchests</code>), so its data is easy to tell apart from other plugins' tables and safe to keep in a database shared with them. Letters, digits and underscore only. Changing it on an existing install renames its existing tables to match on the next startup. See <a href="/docs/database#tables">Tables</a>.
</ConfigProperty>

<ConfigProperty name="autosave-interval" value="5m" type="string">
How often in-memory changes are written back to the database (each online player's data is kept in memory; it is also saved a few seconds after they quit and once at shutdown). Minimum <code>30s</code>. See <a href="/docs/database#how-data-is-saved">How data is saved</a>.
</ConfigProperty>

<ConfigProperty name="sqlite-file" value="enderchests.db" type="string">
SQLite database file, relative to the plugin's data folder. Only used when <code>type</code> is <code>sqlite</code>.
</ConfigProperty>

<ConfigProperty name="host" value="localhost" type="string">
Database host. Used by <code>mysql</code>, <code>mariadb</code>, and <code>postgres</code>.
</ConfigProperty>

<ConfigProperty name="port" value="3306" type="number">
Database port. Default <code>3306</code> for MySQL/MariaDB, <code>5432</code> for PostgreSQL.
</ConfigProperty>

<ConfigProperty name="database" value="enhancedechest" type="string">
Name of the database (schema) to connect to.
</ConfigProperty>

<ConfigProperty name="username" value="root" type="string">
Database username.
</ConfigProperty>

<ConfigProperty name="password" value="" type="string">
Database password. Leave empty for no password.
</ConfigProperty>

<ConfigProperty name="ssl" value="disable" type="string">
TLS mode for a remote MySQL, MariaDB, or PostgreSQL connection. One of:

- **`disable`** — no encryption (default).
- **`require`** — encrypt the connection, but do **not** verify the server certificate or hostname. Stops passive snooping, but not an active man-in-the-middle.
- **`verify-full`** — encrypt **and** verify the certificate chain and hostname. The only mode that defends against a man-in-the-middle; the database server's CA must be trusted by the server's JVM (its truststore).

Requires a full server restart.
</ConfigProperty>

<ConfigProperty name="pool-size" value="10" type="number">
Maximum number of pooled database connections. Only applies to MySQL, MariaDB, and PostgreSQL.
</ConfigProperty>

</ConfigGroup>

<ConfigGroup name="cross-server">
<template #info>
Lets several servers behind a proxy share one database, so a player's ender chests follow them between servers. Needs a shared MySQL/MariaDB/PostgreSQL database and a shared Redis server. See <a href="/docs/database#cross-server">Cross-Server Support</a>. Changes in this section require a full server restart.
</template>

<ConfigProperty name="enabled" value="false" type="boolean">
Turns cross-server mode on. Requires <code>database.type</code> <code>mysql</code>, <code>mariadb</code>, or <code>postgres</code>; the plugin refuses to start in cross-server mode on SQLite.
</ConfigProperty>

<ConfigProperty name="server-id" value="" type="string">
A name for this server, unique across your network (for example <code>survival</code>). Leave empty to generate one automatically at every startup. Never give two servers the same name.
</ConfigProperty>

<ConfigProperty name="redis.host" value="localhost" type="string">
Redis server address, reachable from every server of the network.
</ConfigProperty>

<ConfigProperty name="redis.port" value="6379" type="number">
Redis port.
</ConfigProperty>

<ConfigProperty name="redis.password" value="" type="string">
Redis password. Leave empty when Redis has no password.
</ConfigProperty>

<ConfigProperty name="redis.ssl" value="false" type="boolean">
Connect to Redis over TLS. When enabled, the connection is encrypted and the server's certificate chain **and** hostname are verified against the JVM truststore — equivalent to the database `verify-full` mode. A self-signed or private-CA certificate must be trusted by the JVM first, otherwise the connection fails at startup.
</ConfigProperty>

<ConfigProperty name="redis.database" value="0" type="number">
Redis database number (0 to 15 on a default Redis install).
</ConfigProperty>

<ConfigProperty name="redis.key-prefix" value="echest:" type="string">
Prefix for the Redis keys this plugin uses. Only change it when several separate networks share one Redis server.
</ConfigProperty>

</ConfigGroup>

<ConfigGroup name="backup">
<template #info>
Automatically saves a copy of all ender chest data on a timer. <strong>SQLite only</strong>: if you use MySQL/MariaDB/PostgreSQL, use your database server's own backup tools instead.
</template>

<ConfigProperty name="enabled" value="true" type="boolean">
Turn automatic backups on or off.
</ConfigProperty>

<ConfigProperty name="interval" value="6h" type="string">
How often to make a backup. Examples: <code>30m</code> (every 30 minutes), <code>6h</code> (every 6 hours), <code>1d</code> (once a day). Units: <code>s m h d w mo y</code>.
</ConfigProperty>

<ConfigProperty name="keep" value="10" type="number">
How many backups to keep. When there are more than this, the oldest ones are deleted automatically. Use <code>0</code> to keep every backup and never delete any.
</ConfigProperty>

<ConfigProperty name="on-startup" value="false" type="boolean">
When <code>true</code>, makes one extra backup right when the server starts, in addition to the normal timer.
</ConfigProperty>

<ConfigProperty name="folder" value="backups" type="string">
Folder (inside <code>plugins/EnhancedEchest/</code>) where backup files are saved. Each file is named with the date and time it was made.
</ConfigProperty>

</ConfigGroup>

<ConfigGroup name="migration">
<template #info>
Controls automatic import of existing vanilla ender chest data. See the <a href="/docs/migration">Migration</a> page for the full workflow.
</template>

<ConfigProperty name="enabled" value="false" type="boolean">
When <code>true</code>, any player who has not yet been migrated has their vanilla ender chest contents imported automatically when they join. Migration runs only once per player.
</ConfigProperty>

</ConfigGroup>

</div>

## Full Example

```yaml
# EnhancedEchest configuration

language: en_US

enderchest:
  # Slot count of the chest auto-created the first time a player opens their ender chest.
  # Must be a multiple of 9, between 9 and 54.
  # Per-player override is available via the enhancedechest.default_size.<size> permission (no config).
  default-size: 54

  # Server-wide switches for the "Edit mode" buttons (Rename / Choose icon / Sort).
  features:
    rename: true
    icon: true
    sort: false
    sort-cooldown: 10s
    rename-blacklist:
      - server
      - admin
      - staff
      - owner
    rename-colors: true

permission-chests:
  enabled: true

temp-enderchest:
  # Lifetime of a temporary chest created on shrink/delete/expire-with-items.
  expiry: 24h
  # How often the plugin scans for expired chests.
  check-interval: 5m
  # Sound played to a player who tries to deposit into a take-only temporary chest.
  deny-sound:
    enabled: true
    key: minecraft:entity.villager.no

database:
  # Storage backend: sqlite | mysql | mariadb | postgres
  type: sqlite
  # Prepended to every table this plugin creates. Letters, digits and underscore only.
  table-prefix: echest_
  # How often in-memory changes are written back to the database. Minimum 30s.
  autosave-interval: 5m
  sqlite-file: enderchests.db
  host: localhost
  port: 3306
  database: enhancedechest
  username: root
  password: ""
  # Require an encrypted TLS connection for a remote database. Requires a restart.
  ssl: false
  pool-size: 10

cross-server:
  # Share one database between several servers behind a proxy. Needs mysql/mariadb/postgres
  # plus a shared Redis server. See the Database page for the full setup.
  enabled: false
  # Unique name per server. Leave empty to generate one at every startup.
  server-id: ""
  redis:
    host: localhost
    port: 6379
    password: ""
    ssl: false
    database: 0
    key-prefix: "echest:"

backup:
  enabled: true
  interval: 6h
  keep: 10
  on-startup: false
  folder: backups

migration:
  enabled: false
```
