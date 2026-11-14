package com.enhancedechest.command.admin;

import com.enhancedechest.EnhancedEchestPlugin;
import com.enhancedechest.migration.SourceSpec;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * {@code /enhancedechest import} — copies all EnhancedEchest data from an old database backend into the
 * plugin's active backend. Bare, it opens the import dialog for a player (the console gets the usage
 * line instead); {@code import sqlite <file>} and {@code import <mysql|mariadb|postgres> <host> <port>
 * <database> <username> [password]} take the source on the command line and work from the console too.
 * The guards (no other players online, no open chest, source ≠ active destination, no destination chest
 * holding items) and the actual copy live in
 * {@link com.enhancedechest.service.ChestOpener#performImport} /
 * {@link com.enhancedechest.migration.DatabaseImportService}.
 */
public final class ImportCommand {

    private ImportCommand() {}

    public static int execute(CommandSourceStack source) {
        CommandSender sender = source.getSender();
        EnhancedEchestPlugin plugin = plugin(sender);
        if (plugin == null) return 0;
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getLanguageManager().get("import.usage"));
            return 0;
        }
        plugin.getChestOpener().showImportDialog(player);
        return 1;
    }

    public static int executeSqlite(CommandSourceStack source, String file) {
        return run(source.getSender(), new SourceSpec("sqlite", file.trim(), "", 0, "", "", ""));
    }

    public static int executeServer(CommandSourceStack source, String type, String host, int port,
                                    String database, String username, String password) {
        return run(source.getSender(), new SourceSpec(type, "", host, port, database, username, password));
    }

    private static int run(CommandSender sender, SourceSpec spec) {
        EnhancedEchestPlugin plugin = plugin(sender);
        if (plugin == null) return 0;
        plugin.getChestOpener().performImport(sender, spec);
        return 1;
    }

    private static @Nullable EnhancedEchestPlugin plugin(CommandSender sender) {
        EnhancedEchestPlugin plugin =
                (EnhancedEchestPlugin) Bukkit.getPluginManager().getPlugin("EnhancedEchest");
        if (plugin == null || !plugin.isEnabled()) {
            sender.sendMessage(Component.text("[EnhancedEchest] Plugin is not available."));
            return null;
        }
        return plugin;
    }
}
