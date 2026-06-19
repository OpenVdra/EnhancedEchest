package com.enhancedechest.config;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.configuration.file.FileConfiguration;

@Getter
public final class PluginConfig {

    // GUI
    private String guiTitle;

    // Database — common
    private String databaseType;

    // SQLite
    private String sqliteFile;

    // MySQL / MariaDB (Phase 4)
    private String dbHost;
    private int dbPort;
    private String dbName;
    private String dbUsername;
    private String dbPassword;
    private int dbPoolSize;

    // Migration
    @Setter
    private boolean migrationEnabled;

    public PluginConfig(FileConfiguration config) {
        reload(config);
    }

    public void reload(FileConfiguration config) {
        guiTitle = config.getString("gui.title", "Ender Chest");

        databaseType = config.getString("database.type", "sqlite");
        sqliteFile   = config.getString("database.sqlite-file", "enderchests.db");

        dbHost     = config.getString("database.host", "localhost");
        dbPort     = config.getInt("database.port", 3306);
        dbName     = config.getString("database.database", "enhancedechest");
        dbUsername = config.getString("database.username", "root");
        dbPassword = config.getString("database.password", "");
        dbPoolSize = config.getInt("database.pool-size", 10);

        migrationEnabled = config.getBoolean("migration.enabled", false);
    }
}
