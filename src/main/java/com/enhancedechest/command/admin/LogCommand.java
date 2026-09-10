package com.enhancedechest.command.admin;

import com.enhancedechest.EnhancedEchestPlugin;
import com.enhancedechest.lang.LanguageManager;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /ee log <player>} — opens the in-game chest-log viewer for a player's ender chests, showing
 * their OPEN/CLOSE history as clickable snapshot panes. Player-only (it opens a GUI). The target is
 * resolved off the command thread via {@link PlayerResolver}, so an offline owner is found from the
 * plugin's own name index.
 */
public final class LogCommand {

    private LogCommand() {}

    public static int log(CommandSourceStack source, String playerName) {
        CommandSender sender = source.getSender();
        EnhancedEchestPlugin plugin =
                (EnhancedEchestPlugin) Bukkit.getPluginManager().getPlugin("EnhancedEchest");
        if (plugin == null || !plugin.isEnabled()) {
            sender.sendMessage(Component.text("[EnhancedEchest] Plugin is not available."));
            return 0;
        }
        LanguageManager lang = plugin.getLanguageManager();
        if (!(sender instanceof Player admin)) {
            sender.sendMessage(lang.get("command.not-player"));
            return 0;
        }
        PlayerResolver.resolveAsync(plugin.getStorageGateway(), plugin.getDbExecutor(), playerName)
                .thenAccept(uuid -> {
                    if (uuid == null) {
                        admin.sendMessage(lang.get("admin.player-not-found", "player", playerName));
                        return;
                    }
                    plugin.getLogViewer().openLog(admin, uuid, playerName, 0);
                });
        return 1;
    }
}
