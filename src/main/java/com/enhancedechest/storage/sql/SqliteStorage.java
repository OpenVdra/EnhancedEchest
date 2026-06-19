package com.enhancedechest.storage.sql;

import com.zaxxer.hikari.HikariConfig;

import java.nio.file.Path;

public final class SqliteStorage extends AbstractSqlStorage {

    private static final String INIT_SQL = """
            CREATE TABLE IF NOT EXISTS enderchests (
                player_uuid    TEXT    PRIMARY KEY,
                container_data BLOB    NOT NULL,
                migrated       INTEGER NOT NULL DEFAULT 0,
                last_updated   INTEGER NOT NULL DEFAULT 0
            )
            """;

    // ON CONFLICT ... DO UPDATE is SQLite 3.24+ (available in all Paper-bundled versions)
    // and is also valid Postgres syntax. Migrated flag is intentionally not overwritten on save.
    private static final String UPSERT_SQL = """
            INSERT INTO enderchests (player_uuid, container_data, migrated, last_updated)
            VALUES (?, ?, 0, ?)
            ON CONFLICT(player_uuid) DO UPDATE SET
                container_data = excluded.container_data,
                last_updated   = excluded.last_updated
            """;

    public SqliteStorage(Path dataFolder, String fileName) {
        super(buildConfig(dataFolder, fileName), INIT_SQL, UPSERT_SQL);
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

        config.setConnectionTestQuery("SELECT 1");
        config.setPoolName("EnhancedEChest-SQLite");
        config.setConnectionTimeout(5_000);
        config.setIdleTimeout(0);
        config.setMaxLifetime(0);

        return config;
    }
}
