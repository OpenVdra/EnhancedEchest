package com.enhancedechest.storage;

import com.enhancedechest.scheduler.Scheduler;
import com.enhancedechest.telemetry.Telemetry;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.slf4j.Logger;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Write-back scheduling for the lazy cache: an async repeating timer at the configured
 * {@code database.autosave-interval} (default 3m) that calls {@link CachedStorage#flush()} and then
 * {@link CachedStorage#evictIdle()} (releasing owners who are offline and fully flushed), plus the
 * per-player write-back a few seconds after a quit ({@link #flushQuitterLater}). The flush itself
 * snapshots dirty rows under the cache lock and performs the JDBC writes off it, so an autosave never
 * blocks gameplay. The final save of everything happens in {@code CachedStorage.close()} at shutdown,
 * independent of this timer.
 *
 * <p>Failures are logged and reported to telemetry, never thrown — the failed rows stay dirty inside
 * the cache and are retried on the next tick. Interval changes apply on {@code /ee reload} via
 * {@link #reschedule(long)}.
 */
public final class AutosaveService {

    /**
     * Delay between a quit and that player's write-back + eviction — long enough for the close-save
     * of a chest that was open at quit time to land first (so the flush captures it and the eviction
     * finds the owner clean). If the save still hasn't landed, nothing is lost: the rows stay dirty,
     * the owner stays resident, and the next autosave writes and evicts them.
     */
    private static final long QUIT_FLUSH_DELAY_MS = 5_000;

    private final CachedStorage storage;
    private final Scheduler scheduler;
    private final Logger logger;
    private final Telemetry telemetry;

    // Touched only on the main thread (start/stop/reschedule).
    private long intervalMillis;
    private ScheduledTask task;

    public AutosaveService(CachedStorage storage, Scheduler scheduler, Logger logger,
                           Telemetry telemetry, long intervalMillis) {
        this.storage        = storage;
        this.scheduler      = scheduler;
        this.logger         = logger;
        this.telemetry      = telemetry;
        this.intervalMillis = intervalMillis;
    }

    /** Starts the repeating async autosave. */
    public void start() {
        task = scheduler.runTimerAsync(
                this::saveNow, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
    }

    /** Re-applies a possibly-changed interval after a {@code /ee reload} (restarts the timer). */
    public void reschedule(long newIntervalMillis) {
        if (newIntervalMillis == intervalMillis) {
            return;
        }
        intervalMillis = newIntervalMillis;
        stop();
        start();
    }

    /** Cancels the repeating autosave. Safe to call even if never started. */
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /**
     * Schedules one player's write-back + eviction shortly after they quit (see
     * {@link #QUIT_FLUSH_DELAY_MS}). Runs async; failures are logged and stay dirty for the autosave.
     */
    public void flushQuitterLater(UUID owner) {
        scheduler.runLaterAsync(() -> {
            try {
                storage.flushOwner(owner);
            } catch (Exception e) {
                logger.error("Write-back after quit failed for {} — changes stay in memory and will "
                        + "be retried on the next autosave", owner, e);
                telemetry.error(e, "storage.quit-flush");
            }
        }, QUIT_FLUSH_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * One flush + idle eviction. Runs on an async scheduler thread; logs and swallows all failures
     * (rows stay dirty, owners stay resident).
     */
    private void saveNow() {
        try {
            long start = System.currentTimeMillis();
            int rows = storage.flush();
            int evicted = storage.evictIdle();
            if (rows > 0 || evicted > 0) {
                logger.info("Auto-saved {} changed row(s) and released {} offline player(s) "
                        + "from memory in {} ms", rows, evicted, System.currentTimeMillis() - start);
            }
        } catch (Exception e) {
            logger.error("Autosave failed — changes stay in memory and will be retried "
                    + "on the next autosave", e);
            telemetry.error(e, "storage.autosave");
        }
    }
}
