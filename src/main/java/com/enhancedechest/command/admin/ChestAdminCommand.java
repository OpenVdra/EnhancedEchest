package com.enhancedechest.command.admin;

import com.enhancedechest.EnhancedEchestPlugin;
import com.enhancedechest.config.PluginConfig;
import com.enhancedechest.lang.LanguageManager;
import com.enhancedechest.model.ChestKind;
import com.enhancedechest.model.ChestSummary;
import com.enhancedechest.service.ChestOpener;
import com.enhancedechest.service.ChestSpillService;
import com.enhancedechest.service.StorageGateway;
import com.enhancedechest.util.DurationFormat;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.translation.Argument;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Admin chest management: /ee add|resize|delete &lt;player&gt; ... .
 *
 * Adding allocates the next free index (no index argument) and may grant an expiring chest.
 * Resizing spills cut-off items into a temp chest. {@code /ee delete &lt;player&gt; &lt;count&gt; [force]}
 * removes the {@code count} newest (highest-index) chests, spilling each by default or hard-deleting
 * with the literal {@code force}; the player's first chest is always kept. All DB work runs async on
 * the service executor (which serializes item-moving operations and force-closes open GUIs); the
 * result is reported to the sender.
 *
 * <p>The target player is resolved asynchronously via {@link PlayerResolver}, so an offline owner is
 * found from the plugin's own name index even when the server usercache does not know them.
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
        resolveAsync(source, playerName).thenAccept(ctx -> {
            if (ctx == null) return;

            if (!PluginConfig.isValidSize(size)) {
                ctx.sender.sendMessage(ctx.lang.get("admin.invalid-size"));
                return;
            }

            int total = Math.max(1, count);

            Long expiresAt = null;
            Component durationLabel = null;
            if (duration != null) {
                long durationMillis;
                try {
                    durationMillis = DurationFormat.parse(duration);
                } catch (IllegalArgumentException e) {
                    ctx.sender.sendMessage(ctx.lang.get("admin.invalid-duration", "duration", duration));
                    return;
                }
                expiresAt = System.currentTimeMillis() + durationMillis;
                durationLabel = ctx.lang.duration(durationMillis);
            }

            final Component label = durationLabel;
            createChests(ctx, total, size, expiresAt).thenAccept(indices -> {
                if (indices.size() == 1) {
                    int index = indices.get(0);
                    if (label == null) {
                        ctx.sender.sendMessage(ctx.lang.get("admin.chest-added",
                                "player", playerName,
                                "index", Integer.toString(index),
                                "size", Integer.toString(size)));
                    } else {
                        ctx.sender.sendMessage(ctx.lang.getArgs("admin.chest-added-expiring",
                                Argument.string("player", playerName),
                                Argument.string("index", Integer.toString(index)),
                                Argument.string("size", Integer.toString(size)),
                                Argument.component("duration", label)));
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
                    ctx.sender.sendMessage(ctx.lang.getArgs("admin.chests-added-expiring",
                            Argument.string("player", playerName),
                            Argument.string("count", Integer.toString(indices.size())),
                            Argument.string("range", range),
                            Argument.string("size", Integer.toString(size)),
                            Argument.component("duration", label)));
                }
            });
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
                    ctx.storageGateway.createChestAsync(ctx.target, size, expiresAt).thenApply(index -> {
                        indices.add(index);
                        return indices;
                    }));
        }
        return chain;
    }

    public static int resize(CommandSourceStack source, String playerName, int index, int size) {
        resolveAsync(source, playerName).thenAccept(ctx -> {
            if (ctx == null) return;

            if (!PluginConfig.isValidSize(size)) {
                ctx.sender.sendMessage(ctx.lang.get("admin.invalid-size"));
                return;
            }

            ctx.storageGateway.listChestsAsync(ctx.target).thenAccept(chests -> {
                ChestSummary target = chests.stream().filter(c -> c.index() == index).findFirst().orElse(null);
                if (target == null) {
                    ctx.sender.sendMessage(ctx.lang.get("admin.chest-not-found",
                            "player", playerName, "index", Integer.toString(index)));
                    return;
                }
                // Permission-granted chests are managed by permissions, not admin commands — leave them alone.
                if (target.kind() == ChestKind.PERM) {
                    ctx.sender.sendMessage(ctx.lang.get("admin.cannot-modify-perm",
                            "player", playerName, "index", Integer.toString(index)));
                    return;
                }
                // The base chest (lowest-indexed NORMAL chest) is off-limits while its size is dictated by a
                // default_size permission — its size is permission-managed, exactly like a PERM chest. The
                // baseline is read from storage so this holds even for an offline owner.
                int baseIndex = chests.stream().filter(c -> c.kind() == ChestKind.NORMAL)
                        .mapToInt(ChestSummary::index).min().orElse(-1);
                if (index == baseIndex) {
                    ctx.storageGateway.getAppliedDefaultSizeAsync(ctx.target).thenAccept(applied -> {
                        if (applied > 0) {
                            ctx.sender.sendMessage(ctx.lang.get("admin.cannot-modify-default-size",
                                    "player", playerName, "index", Integer.toString(index)));
                            return;
                        }
                        doResize(ctx, playerName, index, size);
                    });
                } else {
                    doResize(ctx, playerName, index, size);
                }
            });
        });
        return 1;
    }

    /** Routes a resize through the spill-aware path (a shrink below used slots overflows to a temp chest). */
    private static void doResize(Ctx ctx, String playerName, int index, int size) {
        ctx.spillService.resizeOrSpill(ctx.target, index, size).thenRun(() ->
                ctx.sender.sendMessage(ctx.lang.get("admin.chest-resized",
                        "player", playerName,
                        "index", Integer.toString(index),
                        "size", Integer.toString(size))));
    }

    public static int delete(CommandSourceStack source, String playerName, int count) {
        return doDelete(source, playerName, count, false);
    }

    public static int deleteForce(CommandSourceStack source, String playerName, int count) {
        return doDelete(source, playerName, count, true);
    }

    /**
     * Deletes the {@code count} newest (highest-index) chests a player owns. With {@code force} the rows
     * are hard-deleted (items lost); otherwise each chest's items spill into a temporary chest first.
     * The player's first chest (lowest index) is always kept, so when only it remains nothing is
     * deleted; otherwise the actual number removed (capped at the eligible count) is reported.
     */
    private static int doDelete(CommandSourceStack source, String playerName, int count, boolean force) {
        resolveAsync(source, playerName).thenAccept(ctx -> {
            if (ctx == null) return;

            ctx.spillService.removeNewestChests(ctx.target, count, force).thenAccept(deleted -> {
                if (deleted == 0) {
                    ctx.sender.sendMessage(ctx.lang.get("admin.no-chests-deletable", "player", playerName));
                } else if (deleted == 1) {
                    ctx.sender.sendMessage(ctx.lang.get(
                            force ? "admin.chest-deleted-newest" : "admin.chest-deleted-newest-spilled",
                            "player", playerName));
                } else {
                    ctx.sender.sendMessage(ctx.lang.get(
                            force ? "admin.chests-deleted-newest" : "admin.chests-deleted-newest-spilled",
                            "player", playerName, "count", Integer.toString(deleted)));
                }
            });
        });
        return 1;
    }

    // ---- view ----

    public static int view(CommandSourceStack source, String playerName) {
        return doView(source, playerName, null, false);
    }

    public static int viewList(CommandSourceStack source, String playerName) {
        return doView(source, playerName, null, true);
    }

    public static int view(CommandSourceStack source, String playerName, int index) {
        return doView(source, playerName, index, false);
    }

    /**
     * Opens another player's chest for the admin, sharing the live session so the admin sees (and, with
     * the edit permission, mutates) the very same inventory the owner has open — no dupe possible.
     * Routing (no explicit index):
     * <ul>
     *   <li><b>0 chests</b> → {@code admin.view-no-chests};</li>
     *   <li><b>1 chest</b> → open it directly (unless {@code list} forces the menu);</li>
     *   <li><b>2+ chests</b>, or the literal {@code list} → the admin chest-list dialog to pick one.</li>
     * </ul>
     * An explicit index opens that chest directly (verified to exist first). Offline owners are
     * supported (the admin becomes the sole viewer; the chest persists on close), and are resolved from
     * the plugin's own name index so an offline target is found even when the usercache does not know them.
     */
    private static int doView(CommandSourceStack source, String playerName,
                              @Nullable Integer index, boolean forceList) {
        CommandSender sender = source.getSender();
        if (!(sender instanceof Player admin)) {
            EnhancedEchestPlugin plugin =
                    (EnhancedEchestPlugin) Bukkit.getPluginManager().getPlugin("EnhancedEchest");
            sender.sendMessage(plugin != null && plugin.isEnabled()
                    ? plugin.getLanguageManager().get("command.not-player")
                    : Component.text("[EnhancedEchest] Plugin is not available."));
            return 0;
        }

        resolveAsync(source, playerName).thenAccept(ctx -> {
            if (ctx == null) return;

            if (index != null) {
                int idx = index;
                ctx.storageGateway.listChestsAsync(ctx.target).thenAccept(chests -> {
                    if (chests.stream().noneMatch(c -> c.index() == idx)) {
                        ctx.sender.sendMessage(ctx.lang.get("admin.chest-not-found",
                                "player", playerName, "index", Integer.toString(idx)));
                        return;
                    }
                    ctx.chestOpener.openAdminDetail(admin, playerName, ctx.target, idx);
                });
                return;
            }

            ctx.storageGateway.listChestsAsync(ctx.target).thenAccept(chests -> {
                if (chests.isEmpty()) {
                    ctx.sender.sendMessage(ctx.lang.get("admin.view-no-chests", "player", playerName));
                    return;
                }
                // A lone chest goes straight to its detail dialog unless 'list' was given; 2+ always show the
                // picker. The detail dialog (Open / Clear chest [Admin] / Back) is the hub for admin actions.
                if (!forceList && chests.size() == 1) {
                    ctx.chestOpener.openAdminDetail(admin, playerName, ctx.target, chests.get(0).index());
                } else {
                    ctx.chestOpener.showAdminViewList(admin, playerName, ctx.target, chests);
                }
            });
        });
        return 1;
    }

    // ---- helpers ----

    private record Ctx(CommandSender sender, ChestOpener chestOpener, ChestSpillService spillService,
                       StorageGateway storageGateway, LanguageManager lang, UUID target) {}

    /**
     * Resolves the plugin and target player asynchronously, completing with a ready-to-use {@link Ctx} or
     * {@code null} after reporting the reason to the sender (plugin unavailable, or player not found). The
     * name→UUID resolution runs off the command thread (see {@link PlayerResolver}).
     */
    private static CompletableFuture<Ctx> resolveAsync(CommandSourceStack source, String playerName) {
        CommandSender sender = source.getSender();
        EnhancedEchestPlugin plugin =
                (EnhancedEchestPlugin) Bukkit.getPluginManager().getPlugin("EnhancedEchest");
        if (plugin == null || !plugin.isEnabled()) {
            sender.sendMessage(Component.text("[EnhancedEchest] Plugin is not available."));
            return CompletableFuture.completedFuture(null);
        }
        LanguageManager lang = plugin.getLanguageManager();
        return PlayerResolver.resolveAsync(plugin.getStorageGateway(), plugin.getDbExecutor(), playerName)
                .thenApply(target -> {
                    if (target == null) {
                        sender.sendMessage(lang.get("admin.player-not-found", "player", playerName));
                        return null;
                    }
                    return new Ctx(sender, plugin.getChestOpener(), plugin.getSpillService(),
                            plugin.getStorageGateway(), lang, target);
                });
    }
}
