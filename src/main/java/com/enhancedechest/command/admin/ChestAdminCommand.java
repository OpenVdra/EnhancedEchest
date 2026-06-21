package com.enhancedechest.command.admin;

import com.enhancedechest.EnhancedEchestPlugin;
import com.enhancedechest.config.PluginConfig;
import com.enhancedechest.gui.EnderChestService;
import com.enhancedechest.lang.LanguageManager;
import com.enhancedechest.util.DurationFormat;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Admin chest management: /ee add|resize|delete &lt;player&gt; [index] [size] [duration|force].
 *
 * Adding allocates the next free index (no index argument) and may grant an expiring chest.
 * Resizing spills cut-off items into a temp chest; deleting spills by default, or hard-deletes
 * with the literal {@code force}. All DB work runs async on the service executor (which serializes
 * item-moving operations and force-closes open GUIs); the result is reported back to the sender.
 */
public final class ChestAdminCommand {

    private ChestAdminCommand() {}

    public static int add(CommandSourceStack source, String playerName, int size) {
        return doAdd(source, playerName, size, 1, null);
    }

    public static int add(CommandSourceStack source, String playerName, int size, int count) {
        return doAdd(source, playerName, size, count, null);
    }

    public static int add(CommandSourceStack source, String playerName, int size, int count, String duration) {
        return doAdd(source, playerName, size, count, duration);
    }

    private static int doAdd(CommandSourceStack source, String playerName, int size, int count,
                             @Nullable String duration) {
        Ctx ctx = resolve(source, playerName);
        if (ctx == null) return 0;

        if (!PluginConfig.isValidSize(size)) {
            ctx.sender.sendMessage(ctx.lang.get("admin.invalid-size"));
            return 0;
        }

        int total = Math.max(1, count);

        Long expiresAt = null;
        String durationLabel = null;
        if (duration != null) {
            long durationMillis;
            try {
                durationMillis = DurationFormat.parse(duration);
            } catch (IllegalArgumentException e) {
                ctx.sender.sendMessage(ctx.lang.get("admin.invalid-duration", "duration", duration));
                return 0;
            }
            expiresAt = System.currentTimeMillis() + durationMillis;
            durationLabel = DurationFormat.formatRemaining(durationMillis);
        }

        final String label = durationLabel;
        createChests(ctx, total, size, expiresAt).thenAccept(indices -> {
            if (indices.size() == 1) {
                int index = indices.get(0);
                if (label == null) {
                    ctx.sender.sendMessage(ctx.lang.get("admin.chest-added",
                            "player", playerName,
                            "index", Integer.toString(index),
                            "size", Integer.toString(size)));
                } else {
                    ctx.sender.sendMessage(ctx.lang.get("admin.chest-added-expiring",
                            "player", playerName,
                            "index", Integer.toString(index),
                            "size", Integer.toString(size),
                            "duration", label));
                }
                return;
            }
            String range = "#" + indices.get(0) + "–#" + indices.get(indices.size() - 1);
            if (label == null) {
                ctx.sender.sendMessage(ctx.lang.get("admin.chests-added",
                        "player", playerName,
                        "count", Integer.toString(indices.size()),
                        "range", range,
                        "size", Integer.toString(size)));
            } else {
                ctx.sender.sendMessage(ctx.lang.get("admin.chests-added-expiring",
                        "player", playerName,
                        "count", Integer.toString(indices.size()),
                        "range", range,
                        "size", Integer.toString(size),
                        "duration", label));
            }
        });
        return 1;
    }

    /**
     * Creates {@code count} chests one after another (not concurrently): each {@code createChest}
     * allocates the next free index as {@code max(index)+1}, so running them in parallel would race
     * and could collide on that index. Returns the assigned indices in creation order.
     */
    private static CompletableFuture<List<Integer>> createChests(Ctx ctx, int count, int size,
                                                                 @Nullable Long expiresAt) {
        CompletableFuture<List<Integer>> chain = CompletableFuture.completedFuture(new ArrayList<>());
        for (int i = 0; i < count; i++) {
            chain = chain.thenCompose(indices ->
                    ctx.service.createChestAsync(ctx.target, size, expiresAt).thenApply(index -> {
                        indices.add(index);
                        return indices;
                    }));
        }
        return chain;
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
            // Routes through the spill-aware path: a shrink below used slots overflows to a temp chest.
            ctx.service.resizeOrSpill(ctx.target, index, size).thenRun(() ->
                    ctx.sender.sendMessage(ctx.lang.get("admin.chest-resized",
                            "player", playerName,
                            "index", Integer.toString(index),
                            "size", Integer.toString(size))));
        });
        return 1;
    }

    public static int delete(CommandSourceStack source, String playerName, int index) {
        return doDelete(source, playerName, index, false);
    }

    public static int deleteForce(CommandSourceStack source, String playerName, int index) {
        return doDelete(source, playerName, index, true);
    }

    private static int doDelete(CommandSourceStack source, String playerName, int index, boolean force) {
        Ctx ctx = resolve(source, playerName);
        if (ctx == null) return 0;

        ctx.service.listChestsAsync(ctx.target).thenAccept(chests -> {
            if (chests.stream().noneMatch(c -> c.index() == index)) {
                ctx.sender.sendMessage(ctx.lang.get("admin.chest-not-found",
                        "player", playerName, "index", Integer.toString(index)));
                return;
            }
            ctx.service.removeChest(ctx.target, index, force).thenRun(() ->
                    ctx.sender.sendMessage(ctx.lang.get(
                            force ? "admin.chest-deleted" : "admin.chest-deleted-spilled",
                            "player", playerName, "index", Integer.toString(index))));
        });
        return 1;
    }

    // ---- helpers ----

    private record Ctx(CommandSender sender, EnderChestService service, LanguageManager lang, UUID target) {}

    private static Ctx resolve(CommandSourceStack source, String playerName) {
        CommandSender sender = source.getSender();
        EnhancedEchestPlugin plugin =
                (EnhancedEchestPlugin) Bukkit.getPluginManager().getPlugin("EnhancedEchest");
        if (plugin == null || !plugin.isEnabled()) {
            sender.sendMessage(Component.text("[EnhancedEchest] Plugin is not available."));
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
