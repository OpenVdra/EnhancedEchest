package com.enhancedechest.storage.sql;

import com.enhancedechest.config.PluginConfig;
import com.zaxxer.hikari.HikariConfig;

public final class MysqlStorage extends AbstractSqlStorage {

    // MEDIUMBLOB supports up to 16 MB — more than enough for 54 serialized ItemStacks.
    private static final String INIT_SQL = """
            CREATE TABLE IF NOT EXISTS enderchests (
                player_uuid    VARCHAR(36)  NOT NULL,
                container_data MEDIUMBLOB   NOT NULL,
                migrated       TINYINT(1)   NOT NULL DEFAULT 0,
                last_updated   BIGINT       NOT NULL DEFAULT 0,
                PRIMARY KEY (player_uuid)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """;

    // MySQL / MariaDB upsert syntax. Migrated is preserved on plain save.
    private static final String UPSERT_SQL = """
            INSERT INTO enderchests (player_uuid, container_data, migrated, last_updated)
            VALUES (?, ?, 0, ?)
            ON DUPLICATE KEY UPDATE
                container_data = VALUES(container_data),
                last_updated   = VALUES(last_updated)
            """;

    public MysqlStorage(PluginConfig config) {
        super(buildConfig(config), INIT_SQL, UPSERT_SQL);
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
        hc.setPoolName("EnhancedEChest-MySQL");
        hc.setConnectionTimeout(10_000);
        hc.setIdleTimeout(600_000);
        hc.setMaxLifetime(1_800_000);

        return hc;
    }
}
