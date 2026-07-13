package com.enhancedechest.storage.sql;

import com.enhancedechest.config.PluginConfig;
import com.zaxxer.hikari.HikariConfig;

public final class PostgresStorage extends AbstractSqlStorage {

    // BYTEA maps directly to byte[] via JDBC — no size cap unlike MySQL's BLOB tiers.
    private static final String INIT_SQL = """
            CREATE TABLE IF NOT EXISTS %1$senderchests (
                player_uuid    VARCHAR(36)  NOT NULL,
                chest_index    INTEGER      NOT NULL,
                size           INTEGER      NOT NULL,
                custom_name    VARCHAR(64),
                is_primary     SMALLINT     NOT NULL DEFAULT 0,
                container_data BYTEA,
                migrated       SMALLINT     NOT NULL DEFAULT 0,
                last_updated   BIGINT       NOT NULL DEFAULT 0,
                kind           SMALLINT     NOT NULL DEFAULT 0,
                expires_at     BIGINT,
                icon           VARCHAR(128),
                PRIMARY KEY (player_uuid, chest_index)
            )
            """;

    // Per-player row: settings plus the name index for offline /ee view resolution (name -> UUID),
    // written lazily by ChestOpener's open prelude the first time a player opens their ender chest after
    // a rename (or ever) — not on join. username is nullable — a row can exist (e.g. from an offline
    // admin resize) before any name has ever been recorded.
    private static final String INIT_SETTINGS_SQL = """
            CREATE TABLE IF NOT EXISTS %1$splayers (
                player_uuid          VARCHAR(36) NOT NULL,
                username             VARCHAR(48),
                edit_mode            SMALLINT    NOT NULL DEFAULT 0,
                applied_default_size INTEGER     NOT NULL DEFAULT 0,
                PRIMARY KEY (player_uuid)
            )
            """;

    public PostgresStorage(PluginConfig config) {
        super(buildConfig(config), config.getTablePrefix(),
                INIT_SQL.formatted(config.getTablePrefix()), INIT_SETTINGS_SQL.formatted(config.getTablePrefix()));
    }

    private static HikariConfig buildConfig(PluginConfig config) {
        HikariConfig hc = new HikariConfig();
        // See MysqlStorage.buildConfig for why this is required under Paper's plugin classloader.
        hc.setDriverClassName("com.enhancedechest.libs.postgresql.Driver");
        hc.setJdbcUrl(buildJdbcUrl(config));
        hc.setUsername(config.getDbUsername());
        hc.setPassword(config.getDbPassword());
        hc.setMaximumPoolSize(config.getDbPoolSize());
        hc.setMinimumIdle(2);
        hc.setConnectionTestQuery("SELECT 1");
        hc.setPoolName("EnhancedEchest-Postgres");
        hc.setConnectionTimeout(10_000);
        hc.setIdleTimeout(600_000);
        hc.setMaxLifetime(1_800_000);
        return hc;
    }

    /** Adds {@code sslmode=require} only when TLS is requested; otherwise preserves pgJDBC defaults. */
    static String buildJdbcUrl(PluginConfig config) {
        String url = "jdbc:postgresql://" + config.getDbHost() + ":" + config.getDbPort()
                + "/" + config.getDbName();
        return config.isDbSsl() ? url + "?sslmode=require" : url;
    }
}
