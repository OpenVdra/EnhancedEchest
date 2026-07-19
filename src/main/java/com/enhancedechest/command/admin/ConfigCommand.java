package com.enhancedechest.command.admin;

import com.enhancedechest.EnhancedEchestPlugin;
import com.enhancedechest.lang.LanguageManager;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /enhancedechest config} — opens the in-game {@code config.yml} editor. Player-only (it shows a
 * dialog); the section pages, validation and the write-then-reload live in
 * {@link com.enhancedechest.gui.dialog.ConfigDialogs} / {@link com.enhancedechest.config.ConfigEditor}.
 */
public final class ConfigCommand {

    private ConfigCommand() {}

    public static int execute(CommandSourceStack source) {
        CommandSender sender = source.getSender();
        EnhancedEchestPlugin plugin =
                (EnhancedEchestPlugin) Bukkit.getPluginManager().getPlugin("EnhancedEchest");
        if (plugin == null || !plugin.isEnabled()) {
            sender.sendMessage(Component.text("[EnhancedEchest] Plugin is not available."));
            return 0;
        }
        LanguageManager lang = plugin.getLanguageManager();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(lang.get("command.not-player"));
            return 0;
        }
        plugin.getConfigDialogs().openRoot(player);
        return 1;
    }
}
