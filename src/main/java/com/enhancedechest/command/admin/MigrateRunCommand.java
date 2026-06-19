package com.enhancedechest.command.admin;

import com.enhancedechest.EnhancedEChestPlugin;
import com.enhancedechest.migration.MigrationService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collection;

public final class MigrateRunCommand {

    private MigrateRunCommand() {}

    /**
     * Migrates all currently-online, un-migrated players.
     *
     * Offline players are not touched here — they are auto-migrated by JoinMigrationListener
     * when migration mode is ON and they next log in. Running this command with mode OFF
     * still force-migrates all online players regardless of the mode flag.
     */
    public static int executeAll(CommandSourceStack source) {
        EnhancedEChestPlugin plugin = resolve(source);
        if (plugin == null) return 0;

        MigrationService service = plugin.getMigrationService();
        Collection<? extends Player> online = Bukkit.getOnlinePlayers();

        if (online.isEmpty()) {
            source.getSender().sendMessage(Component.text("[EnhancedEChest] No players online.", NamedTextColor.YELLOW));
            return 0;
        }

        int migrated = 0;
        int skipped = 0;
        for (Player player : online) {
            // Sequential sync — no background threads, no concurrent DB writes
            boolean ran = service.migrateOnline(player);
            if (ran) migrated++; else skipped++;
        }

        source.getSender().sendMessage(
                Component.text("[EnhancedEChest] Migration complete: ", NamedTextColor.GRAY)
                        .append(Component.text(migrated + " migrated", NamedTextColor.GREEN))
                        .append(Component.text(", ", NamedTextColor.GRAY))
                        .append(Component.text(skipped + " already done", NamedTextColor.YELLOW)));
        return 1;
    }

    /**
     * Migrates a single named player. The player must be online; if offline,
     * advise the admin to wait for the player's next login (JoinMigrationListener handles it).
     */
    public static int executePlayer(CommandSourceStack source, String playerName) {
        EnhancedEChestPlugin plugin = resolve(source);
        if (plugin == null) return 0;

        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            source.getSender().sendMessage(
                    Component.text("[EnhancedEChest] '" + playerName + "' is not online. ", NamedTextColor.YELLOW)
                            .append(Component.text("Offline players are auto-migrated on next login when migration mode is ON.",
                                    NamedTextColor.GRAY)));
            return 0;
        }

        boolean ran = plugin.getMigrationService().migrateOnline(target);
        if (ran) {
            source.getSender().sendMessage(
                    Component.text("[EnhancedEChest] Migrated " + target.getName() + " successfully.", NamedTextColor.GREEN));
        } else {
            source.getSender().sendMessage(
                    Component.text("[EnhancedEChest] " + target.getName() + " was already migrated.", NamedTextColor.YELLOW));
        }
        return 1;
    }

    private static EnhancedEChestPlugin resolve(CommandSourceStack source) {
        EnhancedEChestPlugin plugin = (EnhancedEChestPlugin) Bukkit.getPluginManager().getPlugin("EnhancedEChest");
        if (plugin == null || !plugin.isEnabled()) {
            source.getSender().sendMessage(Component.text("[EnhancedEChest] Plugin is not available.", NamedTextColor.RED));
            return null;
        }
        return plugin;
    }
}
