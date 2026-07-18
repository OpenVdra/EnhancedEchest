# Switching Backends

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
