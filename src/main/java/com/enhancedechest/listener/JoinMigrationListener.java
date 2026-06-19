package com.enhancedechest.listener;

import com.enhancedechest.config.PluginConfig;
import com.enhancedechest.migration.MigrationService;
import lombok.RequiredArgsConstructor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Auto-migrates un-migrated players on join when migration mode is ON.
 * This is the primary path for online player migration; admin commands trigger the same service.
 */
@RequiredArgsConstructor
public final class JoinMigrationListener implements Listener {

    private final PluginConfig config;
    private final MigrationService migrationService;

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!config.isMigrationEnabled()) return;
        migrationService.migrateOnline(event.getPlayer());
    }
}
