package com.enhancedechest.storage.sql;

import com.zaxxer.hikari.HikariConfig;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;

public final class SqliteStorage extends AbstractSqlStorage {

    private static final String INIT_SQL = """
            CREATE TABLE IF NOT EXISTS %1$senderchests (
                player_uuid    TEXT    NOT NULL,
                chest_index    INTEGER NOT NULL,
                size           INTEGER NOT NULL,
                custom_name    TEXT,
                is_primary     INTEGER NOT NULL DEFAULT 0,
                container_data BLOB,
                migrated       INTEGER NOT NULL DEFAULT 0,
                last_updated   INTEGER NOT NULL DEFAULT 0,
                kind           INTEGER NOT NULL DEFAULT 0,
                expires_at     INTEGER,
                icon           TEXT,
                PRIMARY KEY (player_uuid, chest_index)
            )
            """;

    // Per-player row: settings plus the name index for offline /ee view resolution (name -> UUID),
    // written lazily by ChestOpener's open prelude the first time a player opens their ender chest after
    // a rename (or ever) — not on join. username is nullable — a row can exist (e.g. from an offline
    // admin resize) before any name has ever been recorded.
    private static final String INIT_SETTINGS_SQL = """
            CREATE TABLE IF NOT EXISTS %1$splayers (
                player_uuid          TEXT    NOT NULL,
                username             TEXT,
                edit_mode            INTEGER NOT NULL DEFAULT 0,
                applied_default_size INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (player_uuid)
            )
            """;

    public SqliteStorage(Path dataFolder, String fileName, String tablePrefix) {
        super(buildConfig(dataFolder, fileName), tablePrefix,
                INIT_SQL.formatted(tablePrefix), INIT_SETTINGS_SQL.formatted(tablePrefix));
    }

    @Override
    public boolean supportsBackup() {
        return true;
    }

    /**
     * Snapshots the database with {@code VACUUM INTO}. Unlike copying the .db file, this produces a
     * defragmented, transactionally-consistent copy even while saves are happening, so there is no
     * need to pause the plugin or risk a torn read. The target must not already exist (the caller
     * supplies a unique timestamped name).
     */
    @Override
    public void backup(Path target) throws Exception {
        // VACUUM INTO takes a string-literal path, not a bind parameter; escape embedded quotes.
        // Backslashes in Windows paths are literal inside a SQLite string, so no further escaping.
        String escaped = target.toAbsolutePath().toString().replace("'", "''");
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("VACUUM INTO '" + escaped + "'");
        }
    }

    private static HikariConfig buildConfig(Path dataFolder, String fileName) {
        Path dbFile = dataFolder.resolve(fileName).toAbsolutePath();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + dbFile);
        config.setDriverClassName("org.sqlite.JDBC");

        // SQLite is a single-writer file; one connection is both sufficient and correct.
        // Additional connections would contend on the file lock and produce SQLITE_BUSY.
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(1);

        // WAL keeps readers un-blocked by the writer and turns most commits into sequential WAL
        // appends; synchronous=NORMAL is the recommended pairing (fsync on checkpoint, not on every
        // commit — a power cut can lose the last moments of play, never corrupt the DB). Both are
        // passed as driver properties, which the (Paper-bundled) Xerial driver applies as PRAGMAs on
        // each new connection. journal_mode is also persistent in the DB file itself.
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("synchronous", "NORMAL");

        config.setConnectionTestQuery("SELECT 1");
        config.setPoolName("EnhancedEchest-SQLite");
        // Generous: the backup's VACUUM INTO holds the single connection for the whole snapshot, and
        // on a big database that can exceed several seconds. Waiters should ride it out and then
        // succeed — with a short timeout an autosave flush landing mid-backup fails instead, leaving
        // its rows dirty for a retry. 30s comfortably covers a large vacuum without masking a hang.
        config.setConnectionTimeout(30_000);
        config.setIdleTimeout(0);
        config.setMaxLifetime(0);

        return config;
    }
}
