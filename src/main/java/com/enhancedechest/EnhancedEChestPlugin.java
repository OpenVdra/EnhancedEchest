package com.enhancedechest;

import com.enhancedechest.config.PluginConfig;
import com.enhancedechest.gui.EnderChestService;
import com.enhancedechest.listener.EnderChestGuiListener;
import com.enhancedechest.listener.JoinMigrationListener;
import com.enhancedechest.listener.PlayerQuitListener;
import com.enhancedechest.listener.VanillaEnderChestListener;
import com.enhancedechest.migration.MigrationService;
import com.enhancedechest.serialization.ContainerCodec;
import com.enhancedechest.storage.EnderChestStorage;
import com.enhancedechest.storage.StorageFactory;
import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
public final class EnhancedEChestPlugin extends JavaPlugin {

    private PluginConfig pluginConfig;
    private ContainerCodec codec;
    private EnderChestStorage storage;
    private EnderChestService enderChestService;
    private MigrationService migrationService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        pluginConfig = new PluginConfig(getConfig());
        codec        = new ContainerCodec();
        storage      = StorageFactory.create(pluginConfig, getDataFolder().toPath());

        try {
            storage.init();
        } catch (Exception e) {
            getSLF4JLogger().error("Failed to initialize storage, disabling plugin", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        enderChestService = new EnderChestService(pluginConfig, codec, storage, getSLF4JLogger());
        migrationService  = new MigrationService(storage, codec, getSLF4JLogger());

        var pm = getServer().getPluginManager();
        pm.registerEvents(new VanillaEnderChestListener(enderChestService), this);
        pm.registerEvents(new EnderChestGuiListener(enderChestService), this);
        pm.registerEvents(new PlayerQuitListener(enderChestService), this);
        pm.registerEvents(new JoinMigrationListener(pluginConfig, migrationService), this);

        getSLF4JLogger().info("EnhancedEChest enabled (storage: {}, migration: {})",
                pluginConfig.getDatabaseType(),
                pluginConfig.isMigrationEnabled() ? "ON" : "OFF");
    }

    @Override
    public void onDisable() {
        if (storage != null) {
            storage.close();
        }
        getSLF4JLogger().info("EnhancedEChest disabled");
    }

    public void reload() {
        reloadConfig();
        pluginConfig.reload(getConfig());
        getSLF4JLogger().info("Configuration reloaded");
    }
}
