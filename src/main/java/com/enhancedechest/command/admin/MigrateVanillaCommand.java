package com.enhancedechest.command.admin;

import com.enhancedechest.EnhancedEchestPlugin;
import com.enhancedechest.lang.LanguageManager;
import com.enhancedechest.migration.MigrationService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * {@code /enhancedechest migrate vanilla [all|<player>]} — migrates online players' vanilla
 * ender chests into their EnhancedEchest chest #1. For importing from the AxVaults plugin instead,
 * see {@link MigrateAxVaultsCommand}.
 *
 * <p>{@link MigrationService#migrateOnline} is asynchronous (its DB phase runs exclusively per chest,
 * so it cannot clobber a chest a player has open), so both commands aggregate futures and report the
 * result back on a scheduler tick once every migration has settled.
 */
public final class MigrateVanillaCommand {

    private MigrateVanillaCommand() {}

    public static int executeAll(CommandSourceStack source) {
        EnhancedEchestPlugin plugin = resolve(source);
        if (plugin == null) return 0;

        LanguageManager lang = plugin.getLanguageManager();
        MigrationService service = plugin.getMigrationService();
        Collection<? extends Player> online = Bukkit.getOnlinePlayers();

        if (online.isEmpty()) {
            source.getSender().sendMessage(lang.get("migrate.no-players"));
            return 0;
        }

        List<CompletableFuture<Boolean>> futures = new ArrayList<>(online.size());
        for (Player player : online) {
            futures.add(service.migrateOnline(player));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .whenComplete((v, err) -> {
                    int migrated = 0;
                    int skipped = 0;
                    for (CompletableFuture<Boolean> f : futures) {
                        boolean ran;
                        try {
                            ran = f.join();
                        } catch (Exception e) {
                            plugin.getSLF4JLogger().error("Vanilla migration failed for one player", e);
                            ran = false;
                        }
                        if (ran) migrated++; else skipped++;
                    }
                    int migratedCount = migrated;
                    int skippedCount = skipped;
                    plugin.getFoliaLib().getScheduler().runNextTick(t ->
                            source.getSender().sendMessage(lang.get("migrate.complete",
                                    "migrated", String.valueOf(migratedCount),
                                    "skipped", String.valueOf(skippedCount))));
                });
        return 1;
    }

    public static int executePlayer(CommandSourceStack source, String playerName) {
        EnhancedEchestPlugin plugin = resolve(source);
        if (plugin == null) return 0;

        LanguageManager lang = plugin.getLanguageManager();
        Player target = Bukkit.getPlayerExact(playerName);

        if (target == null) {
            source.getSender().sendMessage(lang.get("migrate.player-offline", "player", playerName));
            return 0;
        }

        plugin.getMigrationService().migrateOnline(target).whenComplete((ran, err) ->
                plugin.getFoliaLib().getScheduler().runNextTick(t -> {
                    if (err != null) {
                        plugin.getSLF4JLogger().error("Vanilla migration failed for {}", target.getName(),
                                err.getCause() != null ? err.getCause() : err);
                        source.getSender().sendMessage(lang.get("migrate.failed", "player", target.getName()));
                        return;
                    }
                    source.getSender().sendMessage(ran
                            ? lang.get("migrate.success", "player", target.getName())
                            : lang.get("migrate.already-done", "player", target.getName()));
                }));
        return 1;
    }

    private static EnhancedEchestPlugin resolve(CommandSourceStack source) {
        EnhancedEchestPlugin plugin = (EnhancedEchestPlugin) Bukkit.getPluginManager().getPlugin("EnhancedEchest");
        if (plugin == null || !plugin.isEnabled()) {
            source.getSender().sendMessage(Component.text("[EnhancedEchest] Plugin is not available."));
            return null;
        }
        return plugin;
    }
}
