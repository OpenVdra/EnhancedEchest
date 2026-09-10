package com.enhancedechest.command.admin;

import com.enhancedechest.EnhancedEchestPlugin;
import com.enhancedechest.benchmark.StorageBenchmark;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@code /ee benchmark} — a real, in-server storage performance and memory benchmark.
 *
 * <p><b>Developer builds only.</b> The command node is registered only when the jar is a local dev
 * run (see {@code EnhancedEchestBootstrap}), so it never exists in the released plugin.
 *
 * <p>The benchmark is heavy (real disk I/O, {@code System.gc()} settling, an 8s concurrency phase),
 * so it must never touch the main server thread. This command dispatches it to a dedicated daemon
 * thread and streams progress back to the sender; a single-run guard rejects overlapping invocations.
 */
public final class BenchmarkCommand {

    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    private BenchmarkCommand() {}

    public static int execute(CommandSourceStack source) {
        CommandSender sender = source.getSender();
        EnhancedEchestPlugin plugin =
                (EnhancedEchestPlugin) Bukkit.getPluginManager().getPlugin("EnhancedEchest");
        if (plugin == null || !plugin.isEnabled()) {
            sender.sendMessage(Component.text("[EnhancedEchest] Plugin is not available.", NamedTextColor.RED));
            return 0;
        }

        if (!RUNNING.compareAndSet(false, true)) {
            sender.sendMessage(Component.text(
                    "[EnhancedEchest Benchmark] A benchmark is already running — wait for it to finish.",
                    NamedTextColor.RED));
            return 0;
        }

        sender.sendMessage(Component.text(
                "[EnhancedEchest Benchmark] Running off-thread; the server stays up. This may take ~30s.",
                NamedTextColor.YELLOW));

        Thread worker = new Thread(() -> {
            try {
                StorageBenchmark.run(sender, plugin.getDataFolder().toPath());
            } catch (Throwable t) {
                sender.sendMessage(Component.text(
                        "[EnhancedEchest Benchmark] Crashed: " + t, NamedTextColor.RED));
            } finally {
                RUNNING.set(false);
            }
        }, "echest-benchmark");
        worker.setDaemon(true);
        worker.start();
        return 1;
    }
}
