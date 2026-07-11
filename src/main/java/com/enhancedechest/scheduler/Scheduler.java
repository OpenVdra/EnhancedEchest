package com.enhancedechest.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Task scheduler built directly on Paper's own region-aware scheduler API
 * ({@code io.papermc.paper.threadedregions.scheduler.*}). Paper implements that API safely on both a
 * plain Paper server (as the main thread) and on Folia (as the entity/region-owning thread), so no
 * platform branching is needed here for dispatch — only {@link #isFolia()} exists, for the one genuine
 * behavioral difference in {@code ChestSessionManager} (single-viewer vs concurrent-edit per chest).
 */
public final class Scheduler {

    private final Plugin plugin;
    /**
     * -- GETTER --
     * True when running on Folia rather than plain Paper (or a Paper fork).
     */
    @Getter
    private final boolean folia;

    public Scheduler(Plugin plugin) {
        this.plugin = plugin;
        this.folia = detectFolia();
    }

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /** True if the current thread is the global bookkeeping thread (main on Paper, global region on Folia). */
    public boolean isGlobalTickThread() {
        return Bukkit.isGlobalTickThread();
    }

    /** Runs once, off the region/main thread, as soon as possible. */
    public void runAsync(Consumer<ScheduledTask> task) {
        Bukkit.getAsyncScheduler().runNow(plugin, task);
    }

    /** Runs once, off-thread, after {@code delay}. */
    public void runLaterAsync(Runnable task, long delay, TimeUnit unit) {
        Bukkit.getAsyncScheduler().runDelayed(plugin, t -> task.run(), delay, unit);
    }

    /** Repeating off-thread timer; cancel it via the returned {@link ScheduledTask}. */
    public ScheduledTask runTimerAsync(Runnable task, long delay, long period, TimeUnit unit) {
        return Bukkit.getAsyncScheduler().runAtFixedRate(plugin, t -> task.run(), delay, period, unit);
    }

    /** Runs once on the global bookkeeping thread, next tick. */
    public void runNextTick(Consumer<ScheduledTask> task) {
        Bukkit.getGlobalRegionScheduler().run(plugin, task);
    }

    /** Runs once on the thread that owns {@code entity} (its region on Folia; main thread on Paper). A no-op if the entity is no longer valid. */
    public void runAtEntity(Entity entity, Consumer<ScheduledTask> task) {
        entity.getScheduler().run(plugin, task, null);
    }

    /** Runs once on the entity's own thread after {@code delay}. A no-op if the entity is no longer valid. */
    public void runAtEntityLater(Entity entity, Runnable task, long delay, TimeUnit unit) {
        long ticks = Math.max(1, unit.toMillis(delay) / 50);
        entity.getScheduler().runDelayed(plugin, t -> task.run(), null, ticks);
    }

    /** Runs once on the thread that owns {@code location}'s region (main thread on Paper). */
    public void runAtLocation(Location location, Consumer<ScheduledTask> task) {
        Bukkit.getRegionScheduler().run(plugin, location, task);
    }

    /** Cancels every task this plugin has scheduled through the async and global-region schedulers. */
    public void cancelAllTasks() {
        Bukkit.getAsyncScheduler().cancelTasks(plugin);
        Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
    }
}
