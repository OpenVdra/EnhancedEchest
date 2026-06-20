package com.enhancedechest.command.admin;

import com.enhancedechest.EnhancedEChestPlugin;
import com.enhancedechest.config.PluginConfig;
import com.enhancedechest.gui.EnderChestService;
import com.enhancedechest.lang.LanguageManager;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Admin chest management: /ee add|resize|delete &lt;player&gt; [index] [size].
 *
 * Adding allocates the next free index (no index argument). Deleting takes no size.
 * All DB work runs async on the service executor; the result is reported back to the sender.
 */
public final class ChestAdminCommand {

    private ChestAdminCommand() {}

    public static int add(CommandSourceStack source, String playerName, int size) {
        Ctx ctx = resolve(source, playerName);
        if (ctx == null) return 0;

        if (!PluginConfig.isValidSize(size)) {
            ctx.sender.sendMessage(ctx.lang.get("admin.invalid-size"));
            return 0;
        }

        ctx.service.createChestAsync(ctx.target, size).thenAccept(index ->
                ctx.sender.sendMessage(ctx.lang.get("admin.chest-added",
                        "player", playerName,
                        "index", Integer.toString(index),
                        "size", Integer.toString(size))));
        return 1;
    }

    public static int resize(CommandSourceStack source, String playerName, int index, int size) {
        Ctx ctx = resolve(source, playerName);
        if (ctx == null) return 0;

        if (!PluginConfig.isValidSize(size)) {
            ctx.sender.sendMessage(ctx.lang.get("admin.invalid-size"));
            return 0;
        }

        ctx.service.listChestsAsync(ctx.target).thenAccept(chests -> {
            if (chests.stream().noneMatch(c -> c.index() == index)) {
                ctx.sender.sendMessage(ctx.lang.get("admin.chest-not-found",
                        "player", playerName, "index", Integer.toString(index)));
                return;
            }
            ctx.service.resizeAsync(ctx.target, index, size).thenRun(() ->
                    ctx.sender.sendMessage(ctx.lang.get("admin.chest-resized",
                            "player", playerName,
                            "index", Integer.toString(index),
                            "size", Integer.toString(size))));
        });
        return 1;
    }

    public static int delete(CommandSourceStack source, String playerName, int index) {
        Ctx ctx = resolve(source, playerName);
        if (ctx == null) return 0;

        ctx.service.listChestsAsync(ctx.target).thenAccept(chests -> {
            if (chests.stream().noneMatch(c -> c.index() == index)) {
                ctx.sender.sendMessage(ctx.lang.get("admin.chest-not-found",
                        "player", playerName, "index", Integer.toString(index)));
                return;
            }
            ctx.service.deleteAsync(ctx.target, index).thenRun(() ->
                    ctx.sender.sendMessage(ctx.lang.get("admin.chest-deleted",
                            "player", playerName, "index", Integer.toString(index))));
        });
        return 1;
    }

    // ---- helpers ----

    private record Ctx(CommandSender sender, EnderChestService service, LanguageManager lang, UUID target) {}

    private static Ctx resolve(CommandSourceStack source, String playerName) {
        CommandSender sender = source.getSender();
        EnhancedEChestPlugin plugin =
                (EnhancedEChestPlugin) Bukkit.getPluginManager().getPlugin("EnhancedEChest");
        if (plugin == null || !plugin.isEnabled()) {
            sender.sendMessage(Component.text("[EnhancedEChest] Plugin is not available."));
            return null;
        }
        LanguageManager lang = plugin.getLanguageManager();

        UUID target = resolveUuid(playerName);
        if (target == null) {
            sender.sendMessage(lang.get("admin.player-not-found", "player", playerName));
            return null;
        }
        return new Ctx(sender, plugin.getEnderChestService(), lang, target);
    }

    @SuppressWarnings("deprecation")
    private static UUID resolveUuid(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        return offline.hasPlayedBefore() ? offline.getUniqueId() : null;
    }
}
