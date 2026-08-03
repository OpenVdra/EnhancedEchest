package com.enhancedechest;

import com.enhancedechest.command.EnderChestOpenCommand;
import com.enhancedechest.command.admin.ChestAdminCommand;
import com.enhancedechest.command.admin.MigrateAxVaultsCommand;
import com.enhancedechest.command.admin.MigrateCustomEnderChestCommand;
import com.enhancedechest.command.admin.MigratePlayerVaultsXCommand;
import com.enhancedechest.command.admin.ChestTransferCommand;
import com.enhancedechest.command.admin.ImportCommand;
import com.enhancedechest.command.admin.MigrateVanillaCommand;
import com.enhancedechest.command.admin.ReloadCommand;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@SuppressWarnings("UnstableApiUsage")
public final class EnhancedEchestBootstrap implements PluginBootstrap {

    /** Permission to open the ender chest GUI via command ({@code /enderchest}, {@code /eclist}). */
    private static final String OPEN_GUI_PERMISSION = "enhancedechest.command.open";
    // Admin subcommands each gate on their own permission node; there is no separate base-command
    // permission on the root literal (a player with any one node can run that subcommand).
    private static final String ADMIN_RELOAD_PERMISSION = "enhancedechest.admin.reload";
    private static final String ADMIN_MIGRATE_PERMISSION = "enhancedechest.admin.migrate";
    private static final String ADMIN_ADD_PERMISSION = "enhancedechest.admin.add";
    private static final String ADMIN_RESIZE_PERMISSION = "enhancedechest.admin.resize";
    private static final String ADMIN_DELETE_PERMISSION = "enhancedechest.admin.delete";
    private static final String ADMIN_TRANSFER_PERMISSION = "enhancedechest.admin.transfer";
    private static final String ADMIN_IMPORT_PERMISSION = "enhancedechest.admin.import";
    // /ee view requires this; modifying (take/add) further requires enhancedechest.admin.edit,
    // checked per-click in EnderChestGuiListener so a view-only admin can look but not touch.
    private static final String ADMIN_VIEW_PERMISSION = "enhancedechest.admin.view";

    /**
     * Every node the {@code /enhancedechest} tree can gate on. The root literal itself carries no
     * permission of its own, but it still needs a {@code .requires} that is the <b>union</b> of its
     * children's: Brigadier only strips a node from the tree it sends a client when that node's own
     * requirement fails, so a root with no requirement stays visible in tab-completion for everyone —
     * even a player for whom every subcommand underneath it is hidden. Keep this list in sync when a
     * subcommand is added, or its holders lose the ability to see the command at all.
     */
    private static final String[] ADMIN_PERMISSIONS = {
            ADMIN_RELOAD_PERMISSION,
            ADMIN_MIGRATE_PERMISSION,
            ADMIN_ADD_PERMISSION,
            ADMIN_RESIZE_PERMISSION,
            ADMIN_DELETE_PERMISSION,
            ADMIN_TRANSFER_PERMISSION,
            ADMIN_IMPORT_PERMISSION,
            ADMIN_VIEW_PERMISSION,
    };

    /** True when the sender holds at least one admin node, i.e. {@code /ee} has something to offer them. */
    private static boolean hasAnyAdminPermission(CommandSourceStack src) {
        for (String permission : ADMIN_PERMISSIONS) {
            if (src.getSender().hasPermission(permission)) return true;
        }
        return false;
    }

    // Suggestion tooltips and value tables are precomputed once: suggestion providers run on every
    // keystroke, so building Messages/arrays inside them would allocate on the command's hot path.
    // Each suggestion carries a tooltip naming what the value is, shown beside the entry. Nothing is
    // ever suggested that is not a real value — what the argument *is* comes from the client's own
    // usage hint above the chat bar (e.g. "<player> [<index>]"), which Minecraft renders from the
    // argument names in the tree below, so it stays correct without a fake entry in the dropdown.
    private static final Message PLAYER_TOOLTIP = new LiteralMessage("Player");
    private static final Message OFFLINE_PLAYER_TOOLTIP = new LiteralMessage("Player (offline)");

    /** Cap on how many player names a suggestion lists, so a huge offline roster can't flood the client. */
    private static final int MAX_PLAYER_SUGGESTIONS = 50;

    private static final int[] SIZE_VALUES = {9, 18, 27, 36, 45, 54};
    private static final Message[] SIZE_TOOLTIPS = sizeTooltips();

    private static final int[] COUNT_VALUES = {1, 2, 3, 5, 10};
    private static final Message[] COUNT_TOOLTIPS = countTooltips();

    private static final String[] DURATION_VALUES = {"1h", "12h", "1d", "7d", "30d"};
    private static final Message[] DURATION_TOOLTIPS = {
            new LiteralMessage("Duration — 1 hour"),
            new LiteralMessage("Duration — 12 hours"),
            new LiteralMessage("Duration — 1 day"),
            new LiteralMessage("Duration — 7 days"),
            new LiteralMessage("Duration — 30 days"),
    };

    private static Message[] sizeTooltips() {
        Message[] tips = new Message[SIZE_VALUES.length];
        for (int i = 0; i < SIZE_VALUES.length; i++) {
            tips[i] = new LiteralMessage("Chest size — " + SIZE_VALUES[i] + " slots");
        }
        return tips;
    }

    private static Message[] countTooltips() {
        Message[] tips = new Message[COUNT_VALUES.length];
        for (int i = 0; i < COUNT_VALUES.length; i++) {
            tips[i] = new LiteralMessage(COUNT_VALUES[i] == 1 ? "1 chest" : COUNT_VALUES[i] + " chests");
        }
        return tips;
    }

    /** Suggests names of currently online players for the <player> argument (online-only commands). */
    private static final SuggestionProvider<CommandSourceStack> ONLINE_PLAYERS = (ctx, builder) -> {
        String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (Player p : Bukkit.getOnlinePlayers()) {
            String name = p.getName();
            if (name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                builder.suggest(name, PLAYER_TOOLTIP);
            }
        }
        return builder.buildFuture();
    };

    /**
     * Suggests known players for the <player> argument of commands that accept <b>offline</b> targets
     * (add / resize / delete / view). Online players are listed first; once at least one character has
     * been typed, offline names are appended from the plugin's own
     * {@link com.enhancedechest.service.PlayerNameIndex} (filtered by the typed prefix and capped at
     * {@link #MAX_PLAYER_SUGGESTIONS}, so a large roster can't flood the client). The empty state shows
     * only online names, keeping it tidy — type to search the offline roster.
     *
     * <p>The name index is an in-memory, name-sorted map of every player the <b>database</b> has a name
     * for. Looking a prefix up in it is a {@code ConcurrentSkipListMap.subMap} range scan (O(log n + k)),
     * so a keystroke costs no disk and no DB query — which is the whole point: this provider runs on a
     * server thread on <b>every</b> keystroke.
     *
     * <p><b>Never call {@code Bukkit.getOfflinePlayers()} here, or anywhere else.</b> It builds one
     * {@code OfflinePlayer} per file in the playerdata folder, and reading a name off one whose profile
     * is not in the usercache loads and decompresses that player's {@code .dat} file. This provider used
     * to do exactly that, and on a server with thousands of past players it turned every keypress in
     * {@code /ee view <name>} into thousands of NBT reads on a region thread — a TPS collapse, and enough
     * transient garbage to get the server OOM-killed. A name the DB has never seen is simply not
     * completable; typing it in full still resolves.
     */
    private static final SuggestionProvider<CommandSourceStack> KNOWN_PLAYERS = (ctx, builder) -> {
        String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
        Set<String> seen = new HashSet<>();
        int added = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            String name = p.getName();
            if (name.toLowerCase(Locale.ROOT).startsWith(prefix) && seen.add(name.toLowerCase(Locale.ROOT))) {
                builder.suggest(name, PLAYER_TOOLTIP);
                if (++added >= MAX_PLAYER_SUGGESTIONS) return builder.buildFuture();
            }
        }
        // Search the offline names only once the admin starts typing, to keep the empty state tidy.
        if (!prefix.isEmpty()) {
            EnhancedEchestPlugin plugin =
                    (EnhancedEchestPlugin) Bukkit.getPluginManager().getPlugin("EnhancedEchest");
            if (plugin != null && plugin.isEnabled()) {
                for (var entry : plugin.getPlayerNameIndex().prefixMatches(prefix, MAX_PLAYER_SUGGESTIONS - added)) {
                    if (seen.add(entry.displayName().toLowerCase(Locale.ROOT))) {
                        builder.suggest(entry.displayName(), OFFLINE_PLAYER_TOOLTIP);
                        if (++added >= MAX_PLAYER_SUGGESTIONS) break;
                    }
                }
            }
        }
        return builder.buildFuture();
    };

    /**
     * Resolves a player name to a UUID from in-memory data only — online players, then the plugin's own
     * {@link com.enhancedechest.service.PlayerNameIndex}, then Paper's usercache
     * ({@link Bukkit#getOfflinePlayerIfCached(String)}) — <b>without</b> the blocking {@code
     * getOfflinePlayer(String)} web lookup <b>and without</b> {@code getOfflinePlayers()}, whose per-entry
     * name resolution reads playerdata off disk. Both would otherwise land on a server thread, since this
     * sits on the suggestion hot path. The name index step is what lets this resolve players imported
     * straight into the plugin's DB (e.g. via a migration) who have never connected to this server, so
     * they are in no server-side cache at all. Returns null if nothing matches.
     */
    private static UUID knownPlayerUuid(String name, EnhancedEchestPlugin plugin) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();
        UUID indexed = plugin != null ? plugin.getPlayerNameIndex().findUuid(name) : null;
        if (indexed != null) return indexed;
        // Memory-only usercache hit; unlike getOfflinePlayer(String) it never falls back to a Mojang
        // lookup, and unlike getOfflinePlayers() it never touches the playerdata folder.
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        return cached != null ? cached.getUniqueId() : null;
    }

    /** Suggests the valid chest sizes (multiples of 9, from 9 to 54) for the {@code <size>} argument. */
    private static final SuggestionProvider<CommandSourceStack> CHEST_SIZES = (ctx, builder) -> {
        for (int i = 0; i < SIZE_VALUES.length; i++) {
            builder.suggest(SIZE_VALUES[i], SIZE_TOOLTIPS[i]);
        }
        return builder.buildFuture();
    };

    /** Suggests common chest counts for the optional {@code <count>} argument of /ee add. */
    private static final SuggestionProvider<CommandSourceStack> CHEST_COUNTS = (ctx, builder) -> {
        for (int i = 0; i < COUNT_VALUES.length; i++) {
            builder.suggest(COUNT_VALUES[i], COUNT_TOOLTIPS[i]);
        }
        return builder.buildFuture();
    };

    /** Suggests a few common durations for the optional {@code <duration>} argument of /ee add. */
    private static final SuggestionProvider<CommandSourceStack> DURATIONS = (ctx, builder) -> {
        String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (int i = 0; i < DURATION_VALUES.length; i++) {
            if (DURATION_VALUES[i].startsWith(prefix)) {
                builder.suggest(DURATION_VALUES[i], DURATION_TOOLTIPS[i]);
            }
        }
        return builder.buildFuture();
    };

    /** Suggests the sender's own chests as {@code #index} and custom-name completions for /ec. */
    private static final SuggestionProvider<CommandSourceStack> OWN_CHESTS = (ctx, builder) -> {
        if (!(ctx.getSource().getSender() instanceof Player player)) {
            return builder.buildFuture();
        }
        EnhancedEchestPlugin plugin =
                (EnhancedEchestPlugin) Bukkit.getPluginManager().getPlugin("EnhancedEchest");
        if (plugin == null || !plugin.isEnabled()) {
            return builder.buildFuture();
        }
        String prefix = builder.getRemaining().toLowerCase();
        return plugin.getStorageGateway().listChestsAsync(player.getUniqueId())
                .thenApply(chests -> {
                    for (var chest : chests) {
                        String name = chest.customName();
                        boolean named = name != null && !name.isBlank();
                        String idx = "#" + chest.index();
                        if (idx.toLowerCase().startsWith(prefix)) {
                            builder.suggest(idx, new LiteralMessage(
                                    named ? name : "Ender chest " + chest.index()));
                        }
                        if (named && name.toLowerCase().startsWith(prefix)) {
                            builder.suggest(name, new LiteralMessage("Ender chest " + chest.index()));
                        }
                    }
                    return builder.build();
                });
    };

    /**
     * Suggests the target player's chests as {@code <index>} completions for {@code /ee view <player>}.
     * Reads the already-typed {@code player} argument and resolves it from cached data (online or the
     * offline roster), so the index list also works for offline owners.
     */
    private static final SuggestionProvider<CommandSourceStack> TARGET_CHESTS = (ctx, builder) -> {
        String playerName;
        try {
            playerName = StringArgumentType.getString(ctx, "player");
        } catch (IllegalArgumentException e) {
            return builder.buildFuture();
        }
        EnhancedEchestPlugin plugin =
                (EnhancedEchestPlugin) Bukkit.getPluginManager().getPlugin("EnhancedEchest");
        if (plugin == null || !plugin.isEnabled()) {
            return builder.buildFuture();
        }
        UUID target = knownPlayerUuid(playerName, plugin);
        if (target == null) {
            return builder.buildFuture();
        }
        return plugin.getStorageGateway().listChestsAsync(target)
                .thenApply(chests -> {
                    for (var chest : chests) {
                        String name = chest.customName();
                        boolean named = name != null && !name.isBlank();
                        builder.suggest(chest.index(), new LiteralMessage(
                                named ? name : "Ender chest " + chest.index()));
                    }
                    return builder.build();
                });
    };

    /**
     * Suggests transfer targets for {@code /ee transfer <from> <to> <target>}: the literal {@code all},
     * plus the <i>source</i> player's chests as {@code #index} and custom-name completions. The source is
     * read from the already-typed {@code from} argument and resolved from cached data (online or the
     * offline roster), so it also works for offline source players. {@code target} is a greedy string, so
     * suggestions are filtered by the typed prefix.
     */
    private static final SuggestionProvider<CommandSourceStack> TRANSFER_TARGETS = (ctx, builder) -> {
        String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
        if ("all".startsWith(prefix)) {
            builder.suggest("all", new LiteralMessage("Every chest (full account transfer)"));
        }
        String fromName;
        try {
            fromName = StringArgumentType.getString(ctx, "from");
        } catch (IllegalArgumentException e) {
            return builder.buildFuture();
        }
        EnhancedEchestPlugin plugin =
                (EnhancedEchestPlugin) Bukkit.getPluginManager().getPlugin("EnhancedEchest");
        if (plugin == null || !plugin.isEnabled()) {
            return builder.buildFuture();
        }
        UUID source = knownPlayerUuid(fromName, plugin);
        if (source == null) {
            return builder.buildFuture();
        }
        return plugin.getStorageGateway().listChestsAsync(source)
                .thenApply(chests -> {
                    for (var chest : chests) {
                        if (chest.kind() == com.enhancedechest.model.ChestKind.TEMP) continue;
                        String name = chest.customName();
                        boolean named = name != null && !name.isBlank();
                        String idx = "#" + chest.index();
                        if (idx.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                            builder.suggest(idx, new LiteralMessage(named ? name : "Ender chest " + chest.index()));
                        }
                        if (named && name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                            builder.suggest(name, new LiteralMessage("Ender chest " + chest.index()));
                        }
                    }
                    return builder.build();
                });
    };

    @Override
    public void bootstrap(@NotNull BootstrapContext context) {
        context.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            registerPlayerCommands(commands);
            registerAdminCommands(commands);
        });
    }

    private void registerPlayerCommands(Commands commands) {
        commands.register(
                Commands.literal("enderchest")
                        .requires(src -> src.getSender().hasPermission(OPEN_GUI_PERMISSION))
                        .executes(ctx -> EnderChestOpenCommand.execute(ctx.getSource()))
                        // /enderchest <#index | name> — open a specific chest by index or custom name
                        .then(Commands.argument("chest", StringArgumentType.greedyString())
                                .suggests(OWN_CHESTS)
                                .executes(ctx -> EnderChestOpenCommand.executeOpenTarget(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "chest"))))
                        .build(),
                "Open your enhanced enderchest",
                List.of("ec")
        );

        // /eclist — open the chest management menu
        commands.register(
                Commands.literal("eclist")
                        .requires(src -> src.getSender().hasPermission(OPEN_GUI_PERMISSION))
                        .executes(ctx -> EnderChestOpenCommand.executeList(ctx.getSource()))
                        .build(),
                "Open your enhanced enderchest management menu"
        );
    }

    /**
     * The {@code <player> [list | <index>]} subtree shared by {@code /ee view} and its standalone
     * shorthand {@code /endersee}. Built fresh per call rather than cached in a constant: Brigadier
     * builders are mutable and {@code build()} produces one node instance, so handing the same object to
     * two parents would splice one command node into two trees.
     */
    private static RequiredArgumentBuilder<CommandSourceStack, String> viewPlayerArgument() {
        return Commands.argument("player", StringArgumentType.word())
                .suggests(KNOWN_PLAYERS)
                .executes(ctx -> ChestAdminCommand.view(
                        ctx.getSource(),
                        StringArgumentType.getString(ctx, "player")))
                // Literal 'list' → always open the chest picker for the target.
                .then(Commands.literal("list")
                        .executes(ctx -> ChestAdminCommand.viewList(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "player"))))
                .then(Commands.argument("index", IntegerArgumentType.integer(1))
                        .suggests(TARGET_CHESTS)
                        .executes(ctx -> ChestAdminCommand.view(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "player"),
                                IntegerArgumentType.getInteger(ctx, "index"))));
    }

    private void registerAdminCommands(Commands commands) {
        commands.register(
                Commands.literal("enhancedechest")
                        // The root has no permission of its own — this gate is only about visibility:
                        // without it Brigadier keeps the root (and the /ee + namespaced aliases) in the
                        // tree it sends every client, so /ee tab-completes for players who can run none
                        // of its subcommands. Each subcommand still gates on its own node below.
                        .requires(EnhancedEchestBootstrap::hasAnyAdminPermission)
                        .then(Commands.literal("migrate")
                                // All three migration sources share one permission node; gate the whole
                                // subtree on the "migrate" literal itself (not just its children) so
                                // /ee migrate never shows up in tab-completion for players without it.
                                .requires(src -> src.getSender().hasPermission(ADMIN_MIGRATE_PERMISSION))
                                // /ee migrate vanilla [all|<player>] — import vanilla ender chests
                                .then(Commands.literal("vanilla")
                                        .then(Commands.literal("all")
                                                .executes(ctx -> MigrateVanillaCommand.executeAll(ctx.getSource())))
                                        .then(Commands.argument("player", StringArgumentType.word())
                                                .suggests(ONLINE_PLAYERS)
                                                .executes(ctx -> MigrateVanillaCommand.executePlayer(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "player")))))
                                // /ee migrate axvaults [all|<player>] — import vaults from AxVaults
                                .then(Commands.literal("axvaults")
                                        .executes(ctx -> MigrateAxVaultsCommand.executeAll(ctx.getSource()))
                                        .then(Commands.literal("all")
                                                .executes(ctx -> MigrateAxVaultsCommand.executeAll(ctx.getSource())))
                                        .then(Commands.argument("player", StringArgumentType.word())
                                                .suggests(KNOWN_PLAYERS)
                                                .executes(ctx -> MigrateAxVaultsCommand.executePlayer(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "player")))))
                                // /ee migrate playervaultsx [all|<player>] — import vaults from PlayerVaultsX
                                .then(Commands.literal("playervaultsx")
                                        .executes(ctx -> MigratePlayerVaultsXCommand.executeAll(ctx.getSource()))
                                        .then(Commands.literal("all")
                                                .executes(ctx -> MigratePlayerVaultsXCommand.executeAll(ctx.getSource())))
                                        .then(Commands.argument("player", StringArgumentType.word())
                                                .suggests(KNOWN_PLAYERS)
                                                .executes(ctx -> MigratePlayerVaultsXCommand.executePlayer(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "player")))))
                                // /ee migrate customenderchest [all|<player>] — import from CustomEnderChest
                                .then(Commands.literal("customenderchest")
                                        .executes(ctx -> MigrateCustomEnderChestCommand.executeAll(ctx.getSource()))
                                        .then(Commands.literal("all")
                                                .executes(ctx -> MigrateCustomEnderChestCommand.executeAll(ctx.getSource())))
                                        .then(Commands.argument("player", StringArgumentType.word())
                                                .suggests(KNOWN_PLAYERS)
                                                .executes(ctx -> MigrateCustomEnderChestCommand.executePlayer(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "player"))))))
                        .then(Commands.literal("reload")
                                .requires(src -> src.getSender().hasPermission(ADMIN_RELOAD_PERMISSION))
                                .executes(ctx -> ReloadCommand.execute(ctx.getSource())))
                        // /ee import — open the DB→DB import dialog (copy an old backend into the active one).
                        .then(Commands.literal("import")
                                .requires(src -> src.getSender().hasPermission(ADMIN_IMPORT_PERMISSION))
                                .executes(ctx -> ImportCommand.execute(ctx.getSource())))
                        // /ee add <player> <size> [count] [duration] — a single linear chain so each
                        // node has one argument child (two sibling argument children break Brigadier's
                        // suggestions, since word() matches the empty trailing token).
                        .then(Commands.literal("add")
                                .requires(src -> src.getSender().hasPermission(ADMIN_ADD_PERMISSION))
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests(KNOWN_PLAYERS)
                                        .then(Commands.argument("size", IntegerArgumentType.integer(9, 54))
                                                .suggests(CHEST_SIZES)
                                                .executes(ctx -> ChestAdminCommand.add(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "player"),
                                                        IntegerArgumentType.getInteger(ctx, "size")))
                                                // Optional count → create several chests at once (default 1).
                                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 100))
                                                        .suggests(CHEST_COUNTS)
                                                        .executes(ctx -> ChestAdminCommand.add(
                                                                ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "player"),
                                                                IntegerArgumentType.getInteger(ctx, "size"),
                                                                IntegerArgumentType.getInteger(ctx, "count")))
                                                        // Optional duration → expiring chests (e.g. 7d, 1h, 1d_12h).
                                                        .then(Commands.argument("duration", StringArgumentType.word())
                                                                .suggests(DURATIONS)
                                                                .executes(ctx -> ChestAdminCommand.add(
                                                                        ctx.getSource(),
                                                                        StringArgumentType.getString(ctx, "player"),
                                                                        IntegerArgumentType.getInteger(ctx, "size"),
                                                                        IntegerArgumentType.getInteger(ctx, "count"),
                                                                        StringArgumentType.getString(ctx, "duration"))))))))
                        // /ee view <player> [list | index] — open another player's chest, sharing the
                        // live session (concurrent edit on Paper, single-viewer on Folia). No argument:
                        // 1 chest opens directly, 2+ show the picker dialog. The literal 'list' forces
                        // the picker even for a single chest. Modifying requires enhancedechest.admin.edit.
                        .then(Commands.literal("view")
                                .requires(src -> src.getSender().hasPermission(ADMIN_VIEW_PERMISSION))
                                .then(viewPlayerArgument()))
                        // /ee resize <player> <index> <size>
                        .then(Commands.literal("resize")
                                .requires(src -> src.getSender().hasPermission(ADMIN_RESIZE_PERMISSION))
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests(KNOWN_PLAYERS)
                                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                                .then(Commands.argument("size", IntegerArgumentType.integer(9, 54))
                                                        .suggests(CHEST_SIZES)
                                                        .executes(ctx -> ChestAdminCommand.resize(
                                                                ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "player"),
                                                                IntegerArgumentType.getInteger(ctx, "index"),
                                                                IntegerArgumentType.getInteger(ctx, "size")))))))
                        // /ee delete <player> <count> [force] — delete the <count> newest chests
                        // (highest indices); the player's first chest is always kept.
                        .then(Commands.literal("delete")
                                .requires(src -> src.getSender().hasPermission(ADMIN_DELETE_PERMISSION))
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests(KNOWN_PLAYERS)
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                                .suggests(CHEST_COUNTS)
                                                .executes(ctx -> ChestAdminCommand.delete(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "player"),
                                                        IntegerArgumentType.getInteger(ctx, "count")))
                                                // Literal 'force' → hard-delete (items lost); default spills to a temp chest.
                                                .then(Commands.literal("force")
                                                        .executes(ctx -> ChestAdminCommand.deleteForce(
                                                                ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "player"),
                                                                IntegerArgumentType.getInteger(ctx, "count")))))))
                        // /ee transfer <from> <to> <index|name|all> [override|temp] — move one player's
                        // chests onto another account (account switch). The target and the optional
                        // override/temp flag share one greedy argument so the target may be a #index, a
                        // custom name (which can contain spaces), or 'all'; the flag is parsed off the end.
                        .then(Commands.literal("transfer")
                                .requires(src -> src.getSender().hasPermission(ADMIN_TRANSFER_PERMISSION))
                                .then(Commands.argument("from", StringArgumentType.word())
                                        .suggests(KNOWN_PLAYERS)
                                        .then(Commands.argument("to", StringArgumentType.word())
                                                .suggests(KNOWN_PLAYERS)
                                                .then(Commands.argument("target", StringArgumentType.greedyString())
                                                        .suggests(TRANSFER_TARGETS)
                                                        .executes(ctx -> ChestTransferCommand.transfer(
                                                                ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "from"),
                                                                StringArgumentType.getString(ctx, "to"),
                                                                StringArgumentType.getString(ctx, "target")))))))
                        .build(),
                "EnhancedEchest admin commands",
                List.of("ee")
        );

        // /endersee <player> [list | index] — a standalone shorthand for /ee view, registered as its own
        // command rather than an alias because Paper aliases the whole root literal, not a subcommand.
        // Same permission and same handlers, so the two stay behaviourally identical by construction.
        commands.register(
                Commands.literal("endersee")
                        .requires(src -> src.getSender().hasPermission(ADMIN_VIEW_PERMISSION))
                        .then(viewPlayerArgument())
                        .build(),
                "Open another player's ender chest (same as /ee view)"
        );
    }
}
