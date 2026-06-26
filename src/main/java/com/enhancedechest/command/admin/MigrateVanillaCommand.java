package com.enhancedechest.command.admin;

import com.enhancedechest.EnhancedEchestPlugin;
import com.enhancedechest.lang.LanguageManager;
import com.enhancedechest.migration.MigrationService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collection;

/**
 * {@code /enhancedechest migrate vanilla [all|<player>]} — migrates online players' vanilla
 * ender chests into their EnhancedEchest chest #1. For importing from the AxVaults plugin instead,
 * see {@link MigrateAxVaultsCommand}.
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

        int migrated = 0;
        int skipped = 0;
        for (Player player : online) {
            boolean ran = service.migrateOnline(player);
            if (ran) migrated++; else skipped++;
        }

        source.getSender().sendMessage(lang.get("migrate.complete",
                "migrated", String.valueOf(migrated),
                "skipped", String.valueOf(skipped)));
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

        boolean ran = plugin.getMigrationService().migrateOnline(target);
        source.getSender().sendMessage(ran
                ? lang.get("migrate.success", "player", target.getName())
                : lang.get("migrate.already-done", "player", target.getName()));
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
