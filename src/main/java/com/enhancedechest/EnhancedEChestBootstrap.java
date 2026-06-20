package com.enhancedechest;

import com.enhancedechest.command.EnderChestOpenCommand;
import com.enhancedechest.command.admin.ChestAdminCommand;
import com.enhancedechest.command.admin.MigrateRunCommand;
import com.enhancedechest.command.admin.ReloadCommand;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class EnhancedEChestBootstrap implements PluginBootstrap {

    /** Suggests names of currently online players for the <player> argument. */
    private static final SuggestionProvider<CommandSourceStack> ONLINE_PLAYERS = (ctx, builder) -> {
        String prefix = builder.getRemaining().toLowerCase();
        Bukkit.getOnlinePlayers().stream()
                .map(p -> p.getName())
                .filter(name -> name.toLowerCase().startsWith(prefix))
                .forEach(builder::suggest);
        return builder.buildFuture();
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
                Commands.literal("ec")
                        .requires(src -> src.getSender().hasPermission("ee.use"))
                        .executes(ctx -> EnderChestOpenCommand.execute(ctx.getSource()))
                        .then(Commands.literal("list")
                                .executes(ctx -> EnderChestOpenCommand.executeList(ctx.getSource())))
                        .build(),
                "Open your enhanced enderchest",
                List.of("enderchest")
        );
    }

    private void registerAdminCommands(Commands commands) {
        commands.register(
                Commands.literal("enhancedechest")
                        .requires(src -> src.getSender().isOp() || src.getSender().hasPermission("ee.admin"))
                        .then(Commands.literal("migrate")
                                .then(Commands.literal("run")
                                        .requires(src -> src.getSender().hasPermission("ee.admin.migrate.run"))
                                        .then(Commands.literal("all")
                                                .executes(ctx -> MigrateRunCommand.executeAll(ctx.getSource())))
                                        .then(Commands.argument("player", StringArgumentType.word())
                                                .suggests(ONLINE_PLAYERS)
                                                .executes(ctx -> MigrateRunCommand.executePlayer(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "player"))))))
                        .then(Commands.literal("reload")
                                .requires(src -> src.getSender().hasPermission("ee.admin.reload"))
                                .executes(ctx -> ReloadCommand.execute(ctx.getSource())))
                        // /ee add <player> <size>
                        .then(Commands.literal("add")
                                .requires(src -> src.getSender().hasPermission("ee.admin"))
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests(ONLINE_PLAYERS)
                                        .then(Commands.argument("size", IntegerArgumentType.integer(9, 54))
                                                .executes(ctx -> ChestAdminCommand.add(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "player"),
                                                        IntegerArgumentType.getInteger(ctx, "size"))))))
                        // /ee resize <player> <index> <size>
                        .then(Commands.literal("resize")
                                .requires(src -> src.getSender().hasPermission("ee.admin"))
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests(ONLINE_PLAYERS)
                                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                                .then(Commands.argument("size", IntegerArgumentType.integer(9, 54))
                                                        .executes(ctx -> ChestAdminCommand.resize(
                                                                ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "player"),
                                                                IntegerArgumentType.getInteger(ctx, "index"),
                                                                IntegerArgumentType.getInteger(ctx, "size")))))))
                        // /ee delete <player> <index>
                        .then(Commands.literal("delete")
                                .requires(src -> src.getSender().hasPermission("ee.admin"))
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests(ONLINE_PLAYERS)
                                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                                .executes(ctx -> ChestAdminCommand.delete(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "player"),
                                                        IntegerArgumentType.getInteger(ctx, "index"))))))
                        .build(),
                "EnhancedEChest admin commands",
                List.of("ee")
        );
    }
}
