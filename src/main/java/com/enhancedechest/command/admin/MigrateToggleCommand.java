package com.enhancedechest.command.admin;

import com.enhancedechest.EnhancedEChestPlugin;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;

public final class MigrateToggleCommand {

    private MigrateToggleCommand() {}

    public static int execute(CommandSourceStack source, boolean enable) {
        EnhancedEChestPlugin plugin = (EnhancedEChestPlugin) Bukkit.getPluginManager().getPlugin("EnhancedEChest");
        if (plugin == null || !plugin.isEnabled()) return 0;

        plugin.getPluginConfig().setMigrationEnabled(enable);
        plugin.getConfig().set("migration.enabled", enable);
        plugin.saveConfig();

        String state = enable ? "ON" : "OFF";
        source.getSender().sendMessage(Component.text("[EnhancedEChest] Migration mode: ", NamedTextColor.GRAY)
                .append(Component.text(state, enable ? NamedTextColor.GREEN : NamedTextColor.RED)));
        return 1;
    }
}
