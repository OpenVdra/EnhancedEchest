package com.enhancedechest.storage;

import com.enhancedechest.config.PluginConfig;
import com.enhancedechest.storage.sql.MysqlStorage;
import com.enhancedechest.storage.sql.PostgresStorage;
import com.enhancedechest.storage.sql.SqliteStorage;

import java.nio.file.Path;

public final class StorageFactory {

    private StorageFactory() {}

    public static StorageBackend create(PluginConfig config, Path dataFolder) {
        return switch (config.getDatabaseType().toLowerCase()) {
            case "sqlite"                -> new SqliteStorage(dataFolder, config.getSqliteFile(), config.getTablePrefix());
            case "mysql", "mariadb"      -> new MysqlStorage(config);
            case "postgres", "postgresql" -> new PostgresStorage(config);
            default -> throw new IllegalArgumentException(
                    "Unsupported database type: " + config.getDatabaseType()
                    + " — valid options: sqlite, mysql, postgres");
        };
    }
}
