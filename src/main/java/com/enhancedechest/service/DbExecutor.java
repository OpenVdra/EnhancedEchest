package com.enhancedechest.service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Shared daemon thread-pool for all asynchronous storage work ({@code EnhancedEchest-db}).
 *
 * <p>Owns the single executor every collaborator dispatches DB reads/writes onto, replacing the
 * scattered {@code CompletableFuture.supplyAsync(..., asyncExecutor)} pattern with {@link #supply}
 * / {@link #run}. The pool is closed once, last, on plugin disable (after sessions have flushed their
 * pending saves) — see {@link #shutdown()}.
 *
 * <p>Bounded at {@link #MAX_THREADS}, growing from 0 on demand and idling out after 60s of inactivity —
 * the same shape as a cached pool up to that cap, but never beyond it. A handful of paths dispatch one
 * task per online player at once (join preload, the {@code onEnable} hot-load loop, {@code /ee reload}'s
 * permission re-sync), so an unbounded {@code newCachedThreadPool} would spawn one OS thread per online
 * player on a large server's restart/reload, each briefly queued behind the single SQLite connection —
 * real memory pressure (~1MB stack apiece) for no throughput benefit once concurrency exceeds what the
 * DB side can actually use. Beyond the cap, work queues (unboundedly) rather than spawning further
 * threads or rejecting — every DB call must still eventually run, just serialized behind the cap under a
 * burst.
 *
 * <p>Implementation note: {@code corePoolSize} is set equal to {@code maximumPoolSize} with
 * {@link ThreadPoolExecutor#allowCoreThreadTimeOut} enabled, <b>not</b> {@code core=0} with an unbounded
 * queue — {@link ThreadPoolExecutor} only grows past {@code corePoolSize} when the queue rejects an
 * offer, so with an unbounded queue and {@code core=0} it would never spawn a single worker thread and
 * every task would sit in the queue forever. Core-equals-max plus the timeout is the standard idiom for
 * "grows on demand up to a cap, idles out when quiet."
 */
public final class DbExecutor {

    /**
     * Generous ceiling for concurrent DB-executor threads: comfortably above SQLite's single-connection
     * pool and any realistic {@code database.pool-size} for MySQL/PostgreSQL (default 10), while still
     * capping the worst case (every online player's DB work landing in the same tick) to a bounded
     * number of OS threads instead of one per player.
     */
    private static final int MAX_THREADS = 64;

    private final ExecutorService executor = buildExecutor();

    private static ExecutorService buildExecutor() {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                MAX_THREADS, MAX_THREADS, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), r -> {
            Thread t = new Thread(r, "EnhancedEchest-db");
            t.setDaemon(true);
            return t;
        });
        pool.allowCoreThreadTimeOut(true);
        return pool;
    }

    public <T> CompletableFuture<T> supply(Supplier<T> work) {
        return CompletableFuture.supplyAsync(work, executor);
    }

    public CompletableFuture<Void> run(Runnable work) {
        return CompletableFuture.runAsync(work, executor);
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
