package com.enhancedechest.command;

import com.enhancedechest.EnhancedEChestPlugin;
import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class EnderChestOpenCommand {

    private EnderChestOpenCommand() {}

    public static int execute(CommandSourceStack source) {
        EnhancedEChestPlugin plugin = (EnhancedEChestPlugin) Bukkit.getPluginManager().getPlugin("EnhancedEChest");
        if (plugin == null || !plugin.isEnabled()) {
            source.getSender().sendMessage(Component.text("[EnhancedEChest] Plugin is not available."));
            return 0;
        }

        if (!(source.getSender() instanceof Player player)) {
            source.getSender().sendMessage(plugin.getLanguageManager().get("command.not-player"));
            return 0;
        }

        plugin.getEnderChestService().open(player, null);
        return Command.SINGLE_SUCCESS;
    }
}
