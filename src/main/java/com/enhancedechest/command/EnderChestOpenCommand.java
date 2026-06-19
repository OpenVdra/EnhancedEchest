package com.enhancedechest.command;

import com.enhancedechest.EnhancedEChestPlugin;
import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class EnderChestOpenCommand {

    private EnderChestOpenCommand() {}

    /**
     * Executor for /ec and /enderchest. Looks up the plugin at execution time
     * (safe — the plugin is fully loaded when commands can be run).
     */
    public static int execute(CommandSourceStack source) {
        if (!(source.getSender() instanceof Player player)) {
            source.getSender().sendMessage(Component.text("[EnhancedEChest] Only players can open the enderchest."));
            return 0;
        }

        EnhancedEChestPlugin plugin = (EnhancedEChestPlugin) Bukkit.getPluginManager().getPlugin("EnhancedEChest");
        if (plugin == null || !plugin.isEnabled()) {
            player.sendMessage(Component.text("[EnhancedEChest] Plugin is not available."));
            return 0;
        }

        plugin.getEnderChestService().open(player);
        return Command.SINGLE_SUCCESS;
    }
}
