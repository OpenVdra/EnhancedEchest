package com.enhancedechest.storage.sql;

import com.enhancedechest.config.PluginConfig;
import com.zaxxer.hikari.HikariConfig;

public final class MysqlStorage extends AbstractSqlStorage {

    // MEDIUMBLOB supports up to 16 MB — more than enough for 54 serialized ItemStacks.
    private static final String INIT_SQL = """
            CREATE TABLE IF NOT EXISTS enderchests (
                player_uuid    VARCHAR(36)  NOT NULL,
                chest_index    INT          NOT NULL,
                size           INT          NOT NULL,
                custom_name    VARCHAR(64),
                is_primary     TINYINT(1)   NOT NULL DEFAULT 0,
                container_data MEDIUMBLOB,
                migrated       TINYINT(1)   NOT NULL DEFAULT 0,
                last_updated   BIGINT       NOT NULL DEFAULT 0,
                kind           SMALLINT     NOT NULL DEFAULT 0,
                expires_at     BIGINT,
                icon           VARCHAR(128),
                PRIMARY KEY (player_uuid, chest_index)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """;

    // Per-player row: settings plus the name index for offline /ee view resolution (name -> UUID),
    // written lazily by ChestOpener's open prelude the first time a player opens their ender chest after
    // a rename (or ever) — not on join. username is VARCHAR(48) (nullable — a row can exist, e.g. from an
    // offline admin resize, before any name is recorded) to comfortably fit Java (16), Bedrock/Floodgate-
    // prefixed names and the like.
    private static final String INIT_SETTINGS_SQL = """
            CREATE TABLE IF NOT EXISTS players (
                player_uuid          VARCHAR(36) NOT NULL,
                username             VARCHAR(48),
                edit_mode            TINYINT(1)  NOT NULL DEFAULT 0,
                applied_default_size INT         NOT NULL DEFAULT 0,
                PRIMARY KEY (player_uuid)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """;

    public MysqlStorage(PluginConfig config) {
        super(buildConfig(config), INIT_SQL, INIT_SETTINGS_SQL);
    }

    private static HikariConfig buildConfig(PluginConfig config) {
        HikariConfig hc = new HikariConfig();

        // MariaDB Connector/J is compatible with both MySQL 5.7+ and 8.x.
        // It is shaded into the plugin jar, so no server-side driver is needed.
        hc.setJdbcUrl("jdbc:mariadb://" + config.getDbHost() + ":" + config.getDbPort()
                + "/" + config.getDbName()
                + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8");
        hc.setUsername(config.getDbUsername());
        hc.setPassword(config.getDbPassword());
        hc.setMaximumPoolSize(config.getDbPoolSize());
        hc.setMinimumIdle(2);

        hc.setConnectionTestQuery("SELECT 1");
        hc.setPoolName("EnhancedEchest-MySQL");
        hc.setConnectionTimeout(10_000);
        hc.setIdleTimeout(600_000);
        hc.setMaxLifetime(1_800_000);

        return hc;
    }
}
