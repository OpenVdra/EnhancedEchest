package com.enhancedechest.command.admin;

import com.enhancedechest.EnhancedEchestPlugin;
import com.enhancedechest.lang.LanguageManager;
import com.enhancedechest.service.ChestTransferService.ConflictPolicy;
import com.enhancedechest.service.DbExecutor;
import com.enhancedechest.service.StorageGateway;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.util.Locale;

/**
 * {@code /ee transfer <from> <to> <index|name|all> [override|temp]} — moves a player's ender chests onto
 * another account when someone switches accounts. The conflict flag and the target are parsed out of a
 * single greedy argument so the target may be {@code all}, a {@code #index} or a custom chest name (which
 * can contain spaces), with the optional {@code override}/{@code temp} flag as the last word.
 *
 * <p>Both players are resolved asynchronously via {@link PlayerResolver}, so offline accounts are found
 * from the plugin's own name index even when the server usercache does not know them.
 */
public final class ChestTransferCommand {

    private ChestTransferCommand() {}

    public static int transfer(CommandSourceStack source, String fromName, String toName, String rest) {
        CommandSender sender = source.getSender();
        EnhancedEchestPlugin plugin =
                (EnhancedEchestPlugin) Bukkit.getPluginManager().getPlugin("EnhancedEchest");
        if (plugin == null || !plugin.isEnabled()) {
            sender.sendMessage(Component.text("[EnhancedEchest] Plugin is not available."));
            return 0;
        }
        LanguageManager lang = plugin.getLanguageManager();

        // Split a trailing override/temp flag off the greedy "rest" argument; the remainder is the target.
        String trimmed = rest.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        ConflictPolicy policy = ConflictPolicy.ASK;
        String target = trimmed;
        if (lower.endsWith(" override")) {
            policy = ConflictPolicy.OVERRIDE;
            target = trimmed.substring(0, trimmed.length() - " override".length()).trim();
        } else if (lower.endsWith(" temp")) {
            policy = ConflictPolicy.TEMP;
            target = trimmed.substring(0, trimmed.length() - " temp".length()).trim();
        }
        if (target.isEmpty()) {
            sender.sendMessage(lang.get("admin.transfer-usage"));
            return 0;
        }

        final ConflictPolicy finalPolicy = policy;
        final String finalTarget = target;
        StorageGateway gateway = plugin.getStorageGateway();
        DbExecutor db = plugin.getDbExecutor();

        // Resolve both accounts off the command thread (offline lookup can hit the network), then transfer.
        PlayerResolver.resolveAsync(gateway, db, fromName).thenAccept(from -> {
            if (from == null) {
                sender.sendMessage(lang.get("admin.player-not-found", "player", fromName));
                return;
            }
            PlayerResolver.resolveAsync(gateway, db, toName).thenAccept(to -> {
                if (to == null) {
                    sender.sendMessage(lang.get("admin.player-not-found", "player", toName));
                    return;
                }
                plugin.getChestTransferService()
                        .transfer(sender, fromName, from, toName, to, finalTarget, finalPolicy);
            });
        });
        return 1;
    }
}
