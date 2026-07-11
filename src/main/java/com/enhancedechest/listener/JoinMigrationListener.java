package com.enhancedechest.listener;

import com.enhancedechest.config.PluginConfig;
import com.enhancedechest.migration.MigrationService;
import com.enhancedechest.service.DbExecutor;
import com.enhancedechest.storage.EnderChestStorage;
import com.enhancedechest.telemetry.Telemetry;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.slf4j.Logger;

import java.util.UUID;

/**
 * Auto-migrates un-migrated players on join when migration mode is ON.
 * This is the primary path for online player migration; admin commands trigger the same service.
 *
 * <p>The {@code isMigrated} pre-check runs on the {@link DbExecutor} — never on the join thread — so
 * on a server where (almost) everyone is already migrated, a join costs zero main-thread DB time even
 * during a mass reconnect after a restart. Only the rare not-yet-migrated player proceeds into
 * {@link MigrationService#migrateOnline}, which does its own thread hops and runs its DB write phase
 * under the session manager's per-chest exclusivity — so a player racing a {@code /ec} open against
 * this check simply waits behind the migration instead of clobbering it (see the service's Javadoc).
 * The service also re-checks the flag inside that exclusive phase, so racing this pre-check (double
 * join, concurrent admin {@code /ee migrate}) can never migrate twice.
 */
@RequiredArgsConstructor
public final class JoinMigrationListener implements Listener {

    private final PluginConfig config;
    private final MigrationService migrationService;
    private final EnderChestStorage storage;
    private final DbExecutor db;
    private final Logger logger;
    private final Telemetry telemetry;

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!config.isMigrationEnabled()) return;
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        db.supply(() -> storage.isMigrated(uuid))
                .thenAccept(migrated -> {
                    if (migrated) return;
                    migrationService.migrateOnline(player)
                            .exceptionally(e -> {
                                Throwable cause = e.getCause() != null ? e.getCause() : e;
                                logger.error("Join migration failed for {}", player.getName(), cause);
                                telemetry.error(cause, "migrate.join");
                                return false;
                            });
                })
                .exceptionally(e -> {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    logger.error("Join migration pre-check failed for {}", uuid, cause);
                    telemetry.error(cause, "migrate.join-precheck");
                    return null;
                });
    }
}
