package com.enhancedechest.benchmark;

import com.enhancedechest.model.ChestSummary;
import com.enhancedechest.model.EnderChestData;
import com.enhancedechest.storage.CachedStorage;
import com.enhancedechest.storage.sql.SqliteStorage;
import com.enhancedechest.telemetry.Telemetry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.slf4j.Logger;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Real, in-server performance and memory benchmark for the storage engine
 * ({@link CachedStorage} + {@code OwnerResidencyCache} + {@code ChestCacheState}).
 *
 * <p>Unlike the offline JUnit simulation this replaces, it runs <b>inside the live Paper/Folia
 * server process</b>: the real JVM, the real thread scheduler, the real GC. It drives the actual
 * production storage classes over an <b>isolated throwaway SQLite database</b> in
 * {@code plugins/EnhancedEchest/benchmark/} — never the live data — so it measures the engine as it
 * really behaves without any risk to a real player's chests. The DB and its files are deleted when
 * the run ends.
 *
 * <p>Two memory numbers are reported and answer different questions:
 * <ul>
 *   <li><b>Alloc/op</b> — bytes allocated on the calling thread per operation, read from
 *       {@code ThreadMXBean}. This is GC pressure: how much garbage one operation produces.</li>
 *   <li><b>Retained</b> — live heap held after the operation, a GC-settled {@code Runtime} delta.
 *       This is where a leak shows up, and it is what proves "memory ∝ online players".</li>
 * </ul>
 *
 * <p>The whole run is blocking and does real disk I/O plus {@code System.gc()} settling, so it must
 * be invoked <b>off the main server thread</b> (the command dispatches it to a dedicated thread).
 * It is gated to local developer builds only (see the bootstrap).
 */
public final class StorageBenchmark {

    private static final DecimalFormat DF = new DecimalFormat("#,##0.00");
    private static final DecimalFormat INT_F = new DecimalFormat("#,##0");

    private static final ThreadMXBean THREAD_MX = resolveThreadMxBean();

    private StorageBenchmark() {}

    private static ThreadMXBean resolveThreadMxBean() {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        if (bean instanceof com.sun.management.ThreadMXBean sunBean
                && sunBean.isThreadAllocatedMemorySupported()) {
            sunBean.setThreadAllocatedMemoryEnabled(true);
            return bean;
        }
        return null;
    }

    /** Bytes allocated by the current thread so far, or -1 when the JVM does not expose it. */
    private static long allocatedBytes() {
        if (THREAD_MX instanceof com.sun.management.ThreadMXBean sunBean) {
            return sunBean.getCurrentThreadAllocatedBytes();
        }
        return -1L;
    }

    /** Live heap after coaxing the collector; used for retained-footprint deltas. */
    private static long settledHeap() {
        Runtime rt = Runtime.getRuntime();
        long used = Long.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            System.gc();
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            used = Math.min(used, rt.totalMemory() - rt.freeMemory());
        }
        return used;
    }

    private static long usedHeap() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }

    private static String formatBytes(long bytes) {
        if (bytes < 0) return "n/a";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return DF.format(bytes / 1024.0) + " KB";
        return DF.format(bytes / (1024.0 * 1024.0)) + " MB";
    }

    /** Latency percentiles plus allocation, derived from one timed run. */
    private record Stats(long totalNs, long[] sortedLatenciesNs, long allocBytes, int ops) {
        static Stats of(long totalNs, long[] latenciesNs, long allocBytes) {
            long[] sorted = latenciesNs.clone();
            Arrays.sort(sorted);
            return new Stats(totalNs, sorted, allocBytes, latenciesNs.length);
        }

        double avgUs() {
            return (totalNs / 1000.0) / ops;
        }

        double percentileUs(double q) {
            if (sortedLatenciesNs.length == 0) return 0;
            int idx = Math.min(sortedLatenciesNs.length - 1, (int) (sortedLatenciesNs.length * q));
            return sortedLatenciesNs[idx] / 1000.0;
        }

        double throughput() {
            return totalNs == 0 ? 0 : ops / (totalNs / 1_000_000_000.0);
        }

        long allocPerOp() {
            return allocBytes < 0 ? -1 : allocBytes / Math.max(1, ops);
        }
    }

    // =============================================================================================

    /**
     * Runs the whole suite. Blocking; call from a dedicated thread. Progress and the final report are
     * streamed to {@code sender} and also written to {@code plugins/EnhancedEchest/benchmark/…txt}.
     */
    public static void run(CommandSender sender, Path pluginDataFolder) {
        List<String> report = new ArrayList<>();
        Path benchDir = pluginDataFolder.resolve("benchmark");
        Path dbFile = null;

        send(sender, NamedTextColor.YELLOW,
                "[EnhancedEchest Benchmark] Starting real in-server storage benchmark…");

        Runtime rt = Runtime.getRuntime();
        header(sender, report, rt);

        Logger quietLog = org.slf4j.LoggerFactory.getLogger("echest-benchmark");
        CachedStorage storage = null;
        try {
            Files.createDirectories(benchDir);
            // A fresh, unique DB so a previous run's file can never interfere.
            String dbName = "benchmark-" + Long.toHexString(System.nanoTime()) + ".db";
            dbFile = benchDir.resolve(dbName);
            storage = new CachedStorage(new SqliteStorage(benchDir, dbName, "echest_"), quietLog, Telemetry.NOOP);
            storage.init();
        } catch (Throwable t) {
            send(sender, NamedTextColor.RED,
                    "[EnhancedEchest Benchmark] Could not open the throwaway SQLite database: " + t);
            log(sender, report, "FAILED to initialise storage: " + t);
            log(sender, report, "This benchmark needs the SQLite JDBC driver on the server classpath "
                    + "(present whenever the plugin's own 'sqlite' backend is usable).");
            writeReport(sender, benchDir, report);
            return;
        }

        try {
            gc();
            long baseline = usedHeap();
            log(sender, report, "Heap baseline after init (GC-settled): " + formatBytes(baseline));

            benchmarkJoinPrefetch(storage, sender, report);
            benchmarkOpenSaveCycle(storage, sender, report);
            benchmarkChestLifecycle(storage, sender, report);
            benchmarkWholeDbReads(storage, sender, report);
            benchmarkFlushThroughput(storage, sender, report);
            benchmarkResidencyFootprint(storage, sender, report);
            benchmarkConcurrentLoad(storage, sender, report);
            leakCheck(storage, sender, report, baseline);

            log(sender, report, "");
            log(sender, report, "===============================================================================");
            log(sender, report, "                         BENCHMARK COMPLETED");
            log(sender, report, "===============================================================================");
        } catch (Throwable t) {
            send(sender, NamedTextColor.RED, "[EnhancedEchest Benchmark] Aborted: " + t);
            log(sender, report, "ABORTED with exception: " + t);
            for (StackTraceElement e : t.getStackTrace()) log(sender, report, "    at " + e);
        } finally {
            try {
                storage.close();
            } catch (Throwable ignored) {
                // best-effort
            }
            deleteQuietly(benchDir, dbFile);
            Path saved = writeReport(sender, benchDir, report);
            send(sender, NamedTextColor.GREEN, "[EnhancedEchest Benchmark] Finished."
                    + (saved != null ? " Report saved to " + saved : ""));
        }
    }

    private static void header(CommandSender sender, List<String> report, Runtime rt) {
        log(sender, report, "===============================================================================");
        log(sender, report, "        ENHANCEDECHEST STORAGE PERFORMANCE & MEMORY BENCHMARK (LIVE)");
        log(sender, report, "===============================================================================");
        log(sender, report, "Server: " + Bukkit.getName() + " " + Bukkit.getMinecraftVersion()
                + " | Java: " + System.getProperty("java.version"));
        log(sender, report, "OS: " + System.getProperty("os.name") + " (" + System.getProperty("os.arch")
                + ") | CPUs: " + rt.availableProcessors());
        log(sender, report, "Date: " + new Date());
        log(sender, report, "Heap: max " + formatBytes(rt.maxMemory())
                + " | allocation tracking: " + (THREAD_MX != null ? "enabled" : "unavailable"));
        log(sender, report, "Target: real CachedStorage + OwnerResidencyCache over an isolated throwaway SQLite DB");
        log(sender, report, "-------------------------------------------------------------------------------");
    }

    // ---- BENCHMARK 1: join prefetch (cold owner materialization) --------------------------------
    private static void benchmarkJoinPrefetch(CachedStorage s, CommandSender sender, List<String> report) {
        log(sender, report, "");
        log(sender, report, "### BENCHMARK 1: Join prefetch — cold owner load (pin + loadSettings + listChests)");
        log(sender, report, "The real join sequence: one backend read materialises the whole owner, then residency serves it.");
        log(sender, report, String.format("%-22s | %-10s | %-10s | %-16s | %-11s",
                "Chests per owner", "Avg (us)", "P95 (us)", "Throughput (op/s)", "Alloc/op"));
        log(sender, report, "----------------------------------------------------------------------------------");

        int[] chestCounts = {1, 8, 32};
        int owners = 400;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        for (int chests : chestCounts) {
            // Populate distinct owners in the DB, then flush + evict so every one is cold (non-resident).
            UUID[] ids = new UUID[owners];
            for (int i = 0; i < owners; i++) {
                UUID p = UUID.randomUUID();
                ids[i] = p;
                s.createChest(p, 54);
                s.recordPlayerSeen(p, "Bench-" + Integer.toHexString(p.hashCode()), System.currentTimeMillis());
                for (int c = 1; c < chests; c++) s.createChest(p, 9 * (1 + rnd.nextInt(6)));
                s.saveChest(p, 1, blob(rnd, 2048));
            }
            s.flush();
            for (int i = 0; i < 3; i++) s.evictIdle();

            // Warmup on a handful of throwaway owners so the JIT is hot before timing.
            for (int w = 0; w < Math.min(50, owners); w++) {
                UUID p = ids[w];
                s.loadSettings(p);
                s.listChests(p);
            }
            // Time the cold load on the remaining owners (each touched once = one cold materialization).
            int timed = owners - 50;
            long[] lat = new long[timed];
            long alloc0 = allocatedBytes();
            long start = System.nanoTime();
            for (int i = 0; i < timed; i++) {
                UUID p = ids[i + 50];
                long t0 = System.nanoTime();
                s.pin(p);
                s.loadSettings(p);
                s.listChests(p);
                lat[i] = System.nanoTime() - t0;
                s.unpin(p);
            }
            Stats st = Stats.of(System.nanoTime() - start, lat, allocatedBytes() - alloc0);
            log(sender, report, String.format("%-22s | %-10s | %-10s | %-16s | %-11s",
                    chests + (chests == 1 ? " chest" : " chests"),
                    DF.format(st.avgUs()), DF.format(st.percentileUs(0.95)),
                    INT_F.format(st.throughput()), formatBytes(st.allocPerOp())));

            s.flush();
            for (int i = 0; i < 3; i++) s.evictIdle();
        }
    }

    // ---- BENCHMARK 2: open → save hot cycle (resident, zero-query) ------------------------------
    private static void benchmarkOpenSaveCycle(CachedStorage s, CommandSender sender, List<String> report) {
        log(sender, report, "");
        log(sender, report, "### BENCHMARK 2: Open→save hot cycle (resident owner, zero queries)");
        log(sender, report, "loadChest + saveChest on an already-loaded chest — the gameplay open/close cost.");
        log(sender, report, String.format("%-22s | %-10s | %-10s | %-10s | %-16s | %-11s",
                "Blob size", "Avg (us)", "P95 (us)", "P99 (us)", "Throughput (op/s)", "Alloc/op"));
        log(sender, report, "-------------------------------------------------------------------------------------------");

        int[] blobSizes = {512, 8 * 1024, 64 * 1024};
        String[] labels = {"512 B", "8 KB", "64 KB"};
        int iterations = 20_000;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        UUID p = UUID.randomUUID();
        s.pin(p);
        s.createChest(p, 54);

        for (int b = 0; b < blobSizes.length; b++) {
            byte[] payload = blob(rnd, blobSizes[b]);
            for (int w = 0; w < 500; w++) {
                s.saveChest(p, 1, payload);
                s.loadChest(p, 1);
            }
            long[] lat = new long[iterations];
            long alloc0 = allocatedBytes();
            long start = System.nanoTime();
            for (int it = 0; it < iterations; it++) {
                long t0 = System.nanoTime();
                EnderChestData d = s.loadChest(p, 1);
                s.saveChest(p, 1, d != null && d.containerData() != null ? d.containerData() : payload);
                lat[it] = System.nanoTime() - t0;
            }
            Stats st = Stats.of(System.nanoTime() - start, lat, allocatedBytes() - alloc0);
            log(sender, report, String.format("%-22s | %-10s | %-10s | %-10s | %-16s | %-11s",
                    labels[b], DF.format(st.avgUs()), DF.format(st.percentileUs(0.95)),
                    DF.format(st.percentileUs(0.99)), INT_F.format(st.throughput()),
                    formatBytes(st.allocPerOp())));
        }

        s.unpin(p);
        s.flushOwner(p);
    }

    // ---- BENCHMARK 3: chest lifecycle churn ----------------------------------------------------
    private static void benchmarkChestLifecycle(CachedStorage s, CommandSender sender, List<String> report) {
        log(sender, report, "");
        log(sender, report, "### BENCHMARK 3: Chest lifecycle churn (create / rename / resize / setPrimary / delete)");
        log(sender, report, "In-memory row-model mutations that mark the owner dirty for the next flush.");
        log(sender, report, String.format("%-16s | %-10s | %-10s | %-16s | %-11s",
                "Operation", "Avg (us)", "P95 (us)", "Throughput (op/s)", "Alloc/op"));
        log(sender, report, "----------------------------------------------------------------------------");

        int iterations = 20_000;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        UUID p = UUID.randomUUID();
        s.pin(p);
        s.createChest(p, 54); // base chest, always kept

        // create/delete are timed as a matched pair so the owner's chest set does not run away.
        long[] createLat = new long[iterations];
        long[] deleteLat = new long[iterations];
        long allocCreate = 0, allocDelete = 0;
        long a0 = allocatedBytes();
        long startCreate = System.nanoTime();
        long createTotal = 0, deleteTotal = 0;
        for (int it = 0; it < iterations; it++) {
            long t0 = System.nanoTime();
            int idx = s.createChest(p, 27);
            long t1 = System.nanoTime();
            s.deleteChest(p, idx);
            long t2 = System.nanoTime();
            createLat[it] = t1 - t0;
            deleteLat[it] = t2 - t1;
            createTotal += createLat[it];
            deleteTotal += deleteLat[it];
        }
        long allocPair = allocatedBytes() - a0;
        allocCreate = allocPair / 2;
        allocDelete = allocPair - allocCreate;
        reportOp(sender, report, "createChest", Stats.of(createTotal, createLat, allocCreate));
        reportOp(sender, report, "deleteChest", Stats.of(deleteTotal, deleteLat, allocDelete));

        // rename / resize / setPrimary on the base chest.
        timeOp(sender, report, "renameChest", iterations,
                () -> s.renameChest(p, 1, "name-" + rnd.nextInt(100000)));
        timeOp(sender, report, "resizeChest", iterations,
                () -> s.resizeChest(p, 1, 9 * (1 + rnd.nextInt(6))));
        timeOp(sender, report, "setPrimary", iterations,
                () -> s.setPrimary(p, 1));

        s.unpin(p);
        s.flushOwner(p);
    }

    private static void timeOp(CommandSender sender, List<String> report, String label,
                               int iterations, Runnable op) {
        for (int w = 0; w < 500; w++) op.run();
        long[] lat = new long[iterations];
        long alloc0 = allocatedBytes();
        long start = System.nanoTime();
        for (int it = 0; it < iterations; it++) {
            long t0 = System.nanoTime();
            op.run();
            lat[it] = System.nanoTime() - t0;
        }
        reportOp(sender, report, label, Stats.of(System.nanoTime() - start, lat, allocatedBytes() - alloc0));
    }

    private static void reportOp(CommandSender sender, List<String> report, String label, Stats st) {
        log(sender, report, String.format("%-16s | %-10s | %-10s | %-16s | %-11s",
                label, DF.format(st.avgUs()), DF.format(st.percentileUs(0.95)),
                INT_F.format(st.throughput()), formatBytes(st.allocPerOp())));
    }

    // ---- BENCHMARK 4: whole-DB reads at scale --------------------------------------------------
    private static void benchmarkWholeDbReads(CachedStorage s, CommandSender sender, List<String> report) {
        log(sender, report, "");
        log(sender, report, "### BENCHMARK 4: Whole-DB admin reads at scale (countChests / findExpired / cold listChests)");
        log(sender, report, "These flush-then-query the backend, so cost tracks total DB size, not online players.");
        log(sender, report, String.format("%-22s | %-18s | %-10s | %-10s",
                "DB size (owners)", "Operation", "Avg (ms)", "P95 (ms)"));
        log(sender, report, "----------------------------------------------------------------------------");

        int[] scales = {1_000, 5_000};
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        List<UUID> allOwners = new ArrayList<>();

        int created = 0;
        for (int scale : scales) {
            while (created < scale) {
                UUID p = UUID.randomUUID();
                allOwners.add(p);
                s.createChest(p, 54);
                s.recordPlayerSeen(p, "Scale-" + Integer.toHexString(p.hashCode()), System.currentTimeMillis());
                // a fraction expiring, so findExpired has real candidates to load and check
                if (rnd.nextInt(10) == 0) s.createChest(p, 27, System.currentTimeMillis() + rnd.nextInt(1, 500));
                created++;
            }
            s.flush();
            for (int i = 0; i < 3; i++) s.evictIdle();

            reportTimedMs(sender, report, scale, "countChests", 60, s::countChests);
            long now = System.currentTimeMillis();
            reportTimedMs(sender, report, scale, "findExpired", 60, () -> s.findExpired(now));
            // cold listChests for a random offline owner (residency miss → single-owner backend read)
            List<ChestSummary>[] sink = castList();
            reportTimedMs(sender, report, scale, "cold listChests", 400, () -> {
                UUID p = allOwners.get(rnd.nextInt(allOwners.size()));
                sink[0] = s.listChests(p);
                s.unpin(p);
            });
            for (int i = 0; i < 3; i++) s.evictIdle();
        }
    }

    @SuppressWarnings("unchecked")
    private static List<ChestSummary>[] castList() {
        return (List<ChestSummary>[]) new List[1];
    }

    private static void reportTimedMs(CommandSender sender, List<String> report, int scale,
                                      String label, int iterations, Runnable op) {
        for (int w = 0; w < Math.min(10, iterations); w++) op.run();
        long[] lat = new long[iterations];
        for (int it = 0; it < iterations; it++) {
            long t0 = System.nanoTime();
            op.run();
            lat[it] = System.nanoTime() - t0;
        }
        Stats st = Stats.of(sum(lat), lat, -1);
        log(sender, report, String.format("%-22s | %-18s | %-10s | %-10s",
                INT_F.format(scale), label,
                DF.format(st.avgUs() / 1000.0), DF.format(st.percentileUs(0.95) / 1000.0)));
    }

    // ---- BENCHMARK 5: batch flush throughput ---------------------------------------------------
    private static void benchmarkFlushThroughput(CachedStorage s, CommandSender sender, List<String> report) {
        log(sender, report, "");
        log(sender, report, "### BENCHMARK 5: Batch flush throughput (dirty N owners, then flush())");
        log(sender, report, "One connection, one transaction, native upsert — the autosave write path.");
        log(sender, report, String.format("%-22s | %-14s | %-14s | %-16s",
                "Dirty owners", "Flush (ms)", "Rows written", "Rows/s"));
        log(sender, report, "----------------------------------------------------------------------------");

        int[] batches = {500, 2_000};
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int batch : batches) {
            UUID[] ids = new UUID[batch];
            for (int i = 0; i < batch; i++) {
                UUID p = UUID.randomUUID();
                ids[i] = p;
                s.pin(p);
                s.createChest(p, 54);
                s.saveChest(p, 1, blob(rnd, 1024));
            }
            long t0 = System.nanoTime();
            int rows = s.flush();
            long ms = System.nanoTime() - t0;
            double rowsPerSec = rows / (ms / 1_000_000_000.0);
            log(sender, report, String.format("%-22s | %-14s | %-14s | %-16s",
                    INT_F.format(batch), DF.format(ms / 1_000_000.0), INT_F.format(rows),
                    INT_F.format(rowsPerSec)));
            for (UUID p : ids) s.unpin(p);
            for (int i = 0; i < 3; i++) s.evictIdle();
        }
    }

    // ---- BENCHMARK 6: residency memory footprint (memory ∝ online players) ----------------------
    private static void benchmarkResidencyFootprint(CachedStorage s, CommandSender sender, List<String> report) {
        log(sender, report, "");
        log(sender, report, "### BENCHMARK 6: Residency memory footprint (retained heap ∝ resident owners)");
        log(sender, report, "Retained heap for N simultaneously-online owners each holding a base chest with items.");
        log(sender, report, String.format("%-22s | %-20s | %-18s",
                "Resident owners", "Retained heap", "Per owner"));
        log(sender, report, "--------------------------------------------------------------------");

        int[] counts = {200, 1_000, 4_000};
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int count : counts) {
            long before = settledHeap();
            UUID[] ids = new UUID[count];
            for (int i = 0; i < count; i++) {
                UUID p = UUID.randomUUID();
                ids[i] = p;
                s.pin(p);                       // pinned = "online", never evicted
                s.createChest(p, 54);
                s.loadSettings(p);
                s.saveChest(p, 1, blob(rnd, 2048));
            }
            long retained = settledHeap() - before;
            log(sender, report, String.format("%-22s | %-20s | %-18s",
                    INT_F.format(count), formatBytes(Math.max(0, retained)),
                    formatBytes(Math.max(0, retained) / count)));

            // Release them: unpin, flush, evict — heap returns toward baseline (checked in the leak step).
            for (UUID p : ids) s.unpin(p);
            s.flush();
            for (int i = 0; i < 3; i++) s.evictIdle();
        }
    }

    // ---- BENCHMARK 7: concurrent join→play→quit load (the live simulation) -----------------------
    private static void benchmarkConcurrentLoad(CachedStorage s, CommandSender sender, List<String> report) {
        log(sender, report, "");
        log(sender, report, "### BENCHMARK 7: Concurrent load — join → open/save → quit across many threads");
        log(sender, report, "The old offline simulation, now driven live. Correctness (no thrown op, no deadlock) + latency.");

        final int threads = Math.max(8, Runtime.getRuntime().availableProcessors() * 2);
        final long runMillis = 8_000;
        final int universe = 600;

        UUID[] ids = new UUID[universe];
        int[] chestTarget = new int[universe];
        ThreadLocalRandom setup = ThreadLocalRandom.current();
        for (int i = 0; i < universe; i++) {
            ids[i] = UUID.randomUUID();
            chestTarget[i] = rollChestTarget(setup);
        }

        ConcurrentLinkedQueue<String> failures = new ConcurrentLinkedQueue<>();
        AtomicLong failCount = new AtomicLong();
        AtomicLong sessions = new AtomicLong();
        AtomicLong opCount = new AtomicLong();
        long[][] threadLatencies = new long[threads][];
        java.util.Map<UUID, Boolean> onlineNow = new java.util.concurrent.ConcurrentHashMap<>();

        long deadline = System.currentTimeMillis() + runMillis;
        ExecutorService pool = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("echest-bench-worker");
            return t;
        });
        CountDownLatch done = new CountDownLatch(threads);

        long wall0 = System.nanoTime();
        for (int ti = 0; ti < threads; ti++) {
            final int slot = ti;
            pool.submit(() -> {
                ConcurrentLinkedQueue<Long> lat = new ConcurrentLinkedQueue<>();
                ThreadLocalRandom rnd = ThreadLocalRandom.current();
                try {
                    while (System.currentTimeMillis() < deadline) {
                        int idx = claim(ids, onlineNow, rnd);
                        if (idx < 0) { sleep(1); continue; }
                        UUID p = ids[idx];
                        int target = chestTarget[idx];
                        try {
                            timed(lat, opCount, failures, failCount, "join", () -> { s.pin(p); s.loadSettings(p); });
                            List<ChestSummary> chests =
                                    timedGet(lat, opCount, failures, failCount, "listChests", () -> s.listChests(p));
                            if (chests == null || chests.isEmpty()) {
                                timed(lat, opCount, failures, failCount, "bootstrap", () -> {
                                    s.createChest(p, 54);
                                    s.recordPlayerSeen(p, "W-" + Integer.toHexString(p.hashCode()),
                                            System.currentTimeMillis());
                                    for (int k = 1; k < target; k++) s.createChest(p, 9 * (1 + rnd.nextInt(6)));
                                });
                                chests = s.listChests(p);
                            }
                            List<Integer> owned = new ArrayList<>();
                            if (chests != null) for (ChestSummary cs : chests) owned.add(cs.index());
                            if (owned.isEmpty()) owned.add(1);

                            int cycles = rnd.nextInt(2, 10);
                            for (int c = 0; c < cycles; c++) {
                                int chestIdx = owned.get(rnd.nextInt(owned.size()));
                                timed(lat, opCount, failures, failCount, "loadChest", () -> s.loadChest(p, chestIdx));
                                timed(lat, opCount, failures, failCount, "saveChest",
                                        () -> s.saveChest(p, chestIdx, blob(rnd, rnd.nextInt(256, 4096))));
                                double roll = rnd.nextDouble();
                                if (roll < 0.05 && owned.size() < target) {
                                    Integer ni = timedGet(lat, opCount, failures, failCount, "createChest",
                                            () -> s.createChest(p, 27));
                                    if (ni != null) owned.add(ni);
                                } else if (roll < 0.10 && owned.size() > 1) {
                                    int victim = owned.get(1 + rnd.nextInt(owned.size() - 1));
                                    timed(lat, opCount, failures, failCount, "deleteChest",
                                            () -> s.deleteChest(p, victim));
                                    owned.remove(Integer.valueOf(victim));
                                } else if (roll < 0.13) {
                                    timed(lat, opCount, failures, failCount, "setEditMode",
                                            () -> s.setEditMode(p, rnd.nextBoolean()));
                                }
                            }
                            sessions.incrementAndGet();
                        } finally {
                            timed(lat, opCount, failures, failCount, "quit", () -> { s.unpin(p); s.flushOwner(p); });
                            onlineNow.remove(p);
                        }
                    }
                } finally {
                    threadLatencies[slot] = lat.stream().mapToLong(Long::longValue).toArray();
                    done.countDown();
                }
            });
        }

        boolean deadlock = false;
        try {
            // Poll for deadlock while the workers run.
            while (!done.await(500, TimeUnit.MILLISECONDS)) {
                if (ManagementFactory.getThreadMXBean().findDeadlockedThreads() != null) {
                    deadlock = true;
                    break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        long wallNanos = System.nanoTime() - wall0;
        pool.shutdownNow();
        try {
            pool.awaitTermination(20, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Merge every worker's latency samples for one distribution.
        int total = 0;
        for (long[] arr : threadLatencies) if (arr != null) total += arr.length;
        long[] all = new long[total];
        int at = 0;
        for (long[] arr : threadLatencies) {
            if (arr == null) continue;
            System.arraycopy(arr, 0, all, at, arr.length);
            at += arr.length;
        }
        Stats st = Stats.of(sum(all), all, -1);
        double wallSec = wallNanos / 1_000_000_000.0;

        log(sender, report, String.format("  threads=%d  run=%.1fs  sessions=%,d  ops=%,d  throughput=%,.0f ops/s",
                threads, wallSec, sessions.get(), opCount.get(), opCount.get() / wallSec));
        log(sender, report, String.format("  latency: avg=%s us  p50=%s us  p95=%s us  p99=%s us  max=%s us",
                DF.format(st.avgUs()), DF.format(st.percentileUs(0.50)), DF.format(st.percentileUs(0.95)),
                DF.format(st.percentileUs(0.99)),
                DF.format(all.length == 0 ? 0 : all[argmax(all)] / 1000.0)));
        log(sender, report, "  storage-op failures: " + failCount.get() + "   deadlock observed: " + deadlock);
        if (!failures.isEmpty()) {
            log(sender, report, "  first failures:");
            int shown = 0;
            for (String f : failures) {
                log(sender, report, "    " + f);
                if (++shown >= 5) break;
            }
        }

        s.flush();
        for (int i = 0; i < 3; i++) s.evictIdle();
    }

    // ---- leak check: after quiesce, every residency structure must be empty ---------------------
    private static void leakCheck(CachedStorage s, CommandSender sender, List<String> report, long baseline) {
        log(sender, report, "");
        log(sender, report, "### LEAK CHECK: residency/dirty structures must drain to zero after flush + evict");
        s.flush();
        for (int i = 0; i < 4; i++) s.evictIdle();

        Object cache = field(s, "cache");
        Object state = field(s, "state");
        int resident = size(cache, "resident");
        int loading = size(cache, "loading");
        int pinned = size(cache, "pinned");
        int chests = size(state, "chests");
        int dirtyChests = size(state, "dirtyChests");
        int dirtyPlayers = size(state, "dirtyPlayers");
        int leaked = resident + loading + pinned + chests + dirtyChests + dirtyPlayers;

        long finalHeap = settledHeap();
        long growth = finalHeap - baseline;
        String growthStr = (growth < 0 ? "-" : "+") + formatBytes(Math.abs(growth));
        log(sender, report, String.format("  resident=%d loading=%d pinned=%d chests=%d dirtyChests=%d dirtyPlayers=%d",
                resident, loading, pinned, chests, dirtyChests, dirtyPlayers));
        log(sender, report, String.format("  heap: baseline=%s  final(after gc)=%s  growth=%s",
                formatBytes(baseline), formatBytes(finalHeap), growthStr));
        if (leaked == 0) {
            send(sender, NamedTextColor.GREEN, "  VERDICT: PASS — no structural leak (memory ∝ online players).");
            log(sender, report, "  VERDICT: PASS — no structural leak (memory returns to baseline).");
        } else {
            send(sender, NamedTextColor.RED, "  VERDICT: FAIL — residency structures not empty (see report).");
            log(sender, report, "  VERDICT: FAIL — residency/dirty structures not empty after quiesce (leak).");
        }
    }

    // ---- concurrency helpers -------------------------------------------------------------------
    private static int claim(UUID[] ids, Map<UUID, Boolean> online, ThreadLocalRandom rnd) {
        for (int tries = 0; tries < 6; tries++) {
            int slot = rnd.nextInt(ids.length);
            if (online.putIfAbsent(ids[slot], Boolean.TRUE) == null) return slot;
        }
        return -1;
    }

    private static void timed(Collection<Long> lat, AtomicLong opCount, Collection<String> failures,
                              AtomicLong failCount, String label, Runnable op) {
        long t0 = System.nanoTime();
        try {
            op.run();
        } catch (Throwable t) {
            recordFail(failures, failCount, label, t);
        } finally {
            lat.add(System.nanoTime() - t0);
            opCount.incrementAndGet();
        }
    }

    private static <T> T timedGet(Collection<Long> lat, AtomicLong opCount, Collection<String> failures,
                                  AtomicLong failCount, String label, java.util.function.Supplier<T> op) {
        long t0 = System.nanoTime();
        try {
            return op.get();
        } catch (Throwable t) {
            recordFail(failures, failCount, label, t);
            return null;
        } finally {
            lat.add(System.nanoTime() - t0);
            opCount.incrementAndGet();
        }
    }

    private static void recordFail(Collection<String> failures, AtomicLong failCount, String label, Throwable t) {
        failCount.incrementAndGet();
        if (failures.size() < 25) failures.add(label + " -> " + t);
    }

    private static int rollChestTarget(ThreadLocalRandom rnd) {
        double r = rnd.nextDouble();
        if (r < 0.50) return 1;
        if (r < 0.78) return rnd.nextInt(2, 4);
        if (r < 0.93) return rnd.nextInt(4, 9);
        if (r < 0.99) return rnd.nextInt(9, 21);
        return rnd.nextInt(21, 41);
    }

    // ---- small utilities -----------------------------------------------------------------------
    private static byte[] blob(ThreadLocalRandom rnd, int size) {
        byte[] b = new byte[size];
        rnd.nextBytes(b);
        return b;
    }

    private static long sum(long[] a) {
        long s = 0;
        for (long v : a) s += v;
        return s;
    }

    private static int argmax(long[] a) {
        int best = 0;
        for (int i = 1; i < a.length; i++) if (a[i] > a[best]) best = i;
        return best;
    }

    private static void gc() {
        for (int i = 0; i < 3; i++) {
            System.gc();
            sleep(100);
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static Object field(Object o, String name) {
        try {
            Class<?> c = o.getClass();
            while (c != null) {
                for (Field f : c.getDeclaredFields()) {
                    if (f.getName().equals(name)) {
                        f.setAccessible(true);
                        return f.get(o);
                    }
                }
                c = c.getSuperclass();
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private static int size(Object owner, String field) {
        Object v = field(owner, field);
        if (v instanceof Map<?, ?> m) return m.size();
        if (v instanceof Collection<?> c) return c.size();
        return -1;
    }

    private static void deleteQuietly(Path benchDir, Path dbFile) {
        if (dbFile == null) return;
        // SQLite leaves -wal / -shm sidecars next to the .db; sweep the whole benchmark dir of them.
        try (var stream = Files.list(benchDir)) {
            stream.filter(f -> f.getFileName().toString().startsWith(dbFile.getFileName().toString()))
                    .forEach(f -> {
                        try {
                            Files.deleteIfExists(f);
                        } catch (IOException ignored) {
                            // best-effort
                        }
                    });
        } catch (IOException ignored) {
            // best-effort
        }
    }

    private static Path writeReport(CommandSender sender, Path benchDir, List<String> report) {
        try {
            Files.createDirectories(benchDir);
            Path out = benchDir.resolve("storage-benchmark-report.txt");
            Files.write(out, report);
            return out;
        } catch (Exception e) {
            send(sender, NamedTextColor.RED, "[EnhancedEchest Benchmark] Could not write report file: " + e);
            return null;
        }
    }

    private static void log(CommandSender sender, List<String> report, String line) {
        report.add(line);
        sender.sendMessage(Component.text(line, NamedTextColor.GRAY));
    }

    private static void send(CommandSender sender, NamedTextColor color, String line) {
        sender.sendMessage(Component.text(line, color));
    }
}
