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
 * <p>Bounded at the caller-chosen {@code maxThreads}, growing from 0 on demand and idling out after 60s
 * of inactivity — the same shape as a cached pool up to that cap, but never beyond it. A handful of paths
 * dispatch one task per online player at once (join preload, the {@code onEnable} hot-load loop,
 * {@code /ee reload}'s permission re-sync), so an unbounded {@code newCachedThreadPool} would spawn one
 * OS thread per online player on a large server's restart/reload, each briefly queued behind the DB
 * connection pool — real memory pressure (~1MB stack apiece) for no throughput benefit once concurrency
 * exceeds what the DB side can actually use. Beyond the cap, work queues (unboundedly) rather than
 * spawning further threads or rejecting — every DB call must still eventually run, just serialized behind
 * the cap under a burst.
 *
 * <p>The cap is sized by the plugin to the storage backend, since threads beyond the JDBC pool's
 * connection count only ever block inside Hikari: a small handful for SQLite's single connection, about
 * twice {@code database.pool-size} for MySQL/PostgreSQL (some headroom for tasks doing non-JDBC work —
 * encode, decode, future plumbing — around their DB call).
 *
 * <p>Implementation note: {@code corePoolSize} is set equal to {@code maximumPoolSize} with
 * {@link ThreadPoolExecutor#allowCoreThreadTimeOut} enabled, <b>not</b> {@code core=0} with an unbounded
 * queue — {@link ThreadPoolExecutor} only grows past {@code corePoolSize} when the queue rejects an
 * offer, so with an unbounded queue and {@code core=0} it would never spawn a single worker thread and
 * every task would sit in the queue forever. Core-equals-max plus the timeout is the standard idiom for
 * "grows on demand up to a cap, idles out when quiet."
 */
public final class DbExecutor {

    private final ExecutorService executor;

    /**
     * @param maxThreads ceiling for concurrent DB-executor threads; see the class doc for how the
     *                   plugin sizes this per storage backend
     */
    public DbExecutor(int maxThreads) {
        this.executor = buildExecutor(maxThreads);
    }

    private static ExecutorService buildExecutor(int maxThreads) {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                maxThreads, maxThreads, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), r -> {
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
