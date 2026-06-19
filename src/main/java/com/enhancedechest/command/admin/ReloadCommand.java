package com.enhancedechest.command.admin;

import com.enhancedechest.EnhancedEChestPlugin;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;

public final class ReloadCommand {

    private ReloadCommand() {}

    public static int execute(CommandSourceStack source) {
        EnhancedEChestPlugin plugin = (EnhancedEChestPlugin) Bukkit.getPluginManager().getPlugin("EnhancedEChest");
        if (plugin == null || !plugin.isEnabled()) {
            source.getSender().sendMessage(Component.text("[EnhancedEChest] Plugin is not available.", NamedTextColor.RED));
            return 0;
        }

        plugin.reload();
        source.getSender().sendMessage(Component.text("[EnhancedEChest] Configuration reloaded.", NamedTextColor.GREEN));
        return 1;
    }
}
