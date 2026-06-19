package com.enhancedechest;

import com.enhancedechest.command.EnderChestOpenCommand;
import com.enhancedechest.command.admin.MigrateRunCommand;
import com.enhancedechest.command.admin.MigrateToggleCommand;
import com.enhancedechest.command.admin.ReloadCommand;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class EnhancedEChestBootstrap implements PluginBootstrap {

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
                        .build(),
                "Open your enhanced enderchest",
                List.of("enderchest")
        );
    }

    private void registerAdminCommands(Commands commands) {
        commands.register(
                Commands.literal("enhancedechest")
                        .requires(src -> src.getSender().isOp())
                        .then(Commands.literal("migrate")
                                .then(Commands.literal("on")
                                        .requires(src -> src.getSender().hasPermission("ee.admin.migrate.toggle"))
                                        .executes(ctx -> MigrateToggleCommand.execute(ctx.getSource(), true)))
                                .then(Commands.literal("off")
                                        .requires(src -> src.getSender().hasPermission("ee.admin.migrate.toggle"))
                                        .executes(ctx -> MigrateToggleCommand.execute(ctx.getSource(), false)))
                                .then(Commands.literal("run")
                                        .requires(src -> src.getSender().hasPermission("ee.admin.migrate.run"))
                                        .then(Commands.literal("all")
                                                .executes(ctx -> MigrateRunCommand.executeAll(ctx.getSource())))
                                        .then(Commands.argument("player", StringArgumentType.word())
                                                .suggests((ctx, builder) -> {
                                                    String prefix = builder.getRemaining().toLowerCase();
                                                    Bukkit.getOnlinePlayers().stream()
                                                            .map(p -> p.getName())
                                                            .filter(name -> name.toLowerCase().startsWith(prefix))
                                                            .forEach(builder::suggest);
                                                    return builder.buildFuture();
                                                })
                                                .executes(ctx -> MigrateRunCommand.executePlayer(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "player"))))))
                        .then(Commands.literal("reload")
                                .requires(src -> src.getSender().hasPermission("ee.admin.reload"))
                                .executes(ctx -> ReloadCommand.execute(ctx.getSource())))
                        .build(),
                "EnhancedEChest admin commands",
                List.of("ee")
        );
    }
}
