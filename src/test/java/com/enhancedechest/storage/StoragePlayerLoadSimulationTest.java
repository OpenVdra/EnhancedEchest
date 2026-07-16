package com.enhancedechest.storage;

import com.enhancedechest.model.ChestSummary;
import com.enhancedechest.storage.sql.SqliteStorage;
import com.enhancedechest.telemetry.Telemetry;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Concurrency / performance / memory-leak stress test for the storage layer that was refactored into
 * {@link CachedStorage} + {@link OwnerResidencyCache} + {@link ChestCacheState}. It drives the <b>real</b>
 * classes over a real {@link SqliteStorage} on a throwaway temp DB — no Minecraft server — with a large
 * universe of players cycling through join → open/save ender chests → quit, plus admin threads touching
 * random (often offline) players, and a background autosave (flush + idle eviction) exactly like
 * {@code AutosaveService}.
 *
 * <p>Each player owns a <b>random number of chests</b> drawn from a realistic skewed distribution
 * (most just the base chest, a long tail hoarding dozens), materialized on first join with random sizes
 * (a fraction temporary/expiring). Sessions then open a <i>random</i> owned chest (main or list-picked),
 * and grow/shrink the set via create/delete churn — so residency sees a lifelike mix of light and heavy
 * owners rather than everyone holding exactly one chest. The distribution is printed in the report.
 *
 * <p>Being in the {@code storage} package it can reach the package-private helper classes; it uses
 * reflection to sample the private residency/dirty structures over time. It asserts three things:
 * <ol>
 *   <li><b>Correctness:</b> not a single storage call threw during the whole run.</li>
 *   <li><b>No deadlock:</b> the JVM never reports deadlocked threads.</li>
 *   <li><b>No structural memory leak:</b> after every player has quit and a final flush+evict, the
 *       resident set, the in-memory chest map, the dirty sets, the in-flight-load map and the pinned
 *       set are all empty — memory returns to baseline, proving "memory ∝ online players".</li>
 * </ol>
 * A detailed report (throughput, per-op latency, heap curve, residency curve) is printed to stdout and
 * written to {@code build/reports/stress/stress-report.txt}.
 *
 * <p>Excluded from the normal {@code test} task; run with {@code ./gradlew stressTest}.
 */
class StoragePlayerLoadSimulationTest {

    // ---- tunables ----
    private static final int  PLAYER_UNIVERSE = 450;   // distinct players that connect over the window
    private static final int  WORKER_THREADS  = 64;    // ≈ peak concurrently-online players
    private static final int  ADMIN_THREADS   = 8;     // concurrent admin reads on random (offline) players
    private static final long RUN_MILLIS      = 30_000;
    private static final long AUTOSAVE_MS     = 2_000;  // flush + evictIdle cadence (like AutosaveService)
    private static final long SAMPLE_MS       = 500;    // heap / residency sampler cadence

    // ---- latency histogram (upper bounds in microseconds) ----
    private static final long[] HB_US = {
            50, 100, 250, 500, 1_000, 2_500, 5_000, 10_000, 25_000,
            50_000, 100_000, 250_000, 500_000, 1_000_000, Long.MAX_VALUE };
    private static final String[] HB_LABEL = {
            "<50us", "<100us", "<250us", "<500us", "<1ms", "<2.5ms", "<5ms", "<10ms", "<25ms",
            "<50ms", "<100ms", "<250ms", "<500ms", "<1s", ">=1s" };
    private final AtomicLong[] histo = newHisto();

    // ---- per-op-type stats ----
    private final Map<String, OpStat> ops = new ConcurrentHashMap<>();

    // ---- failure capture ----
    private final ConcurrentLinkedQueue<String> failures = new ConcurrentLinkedQueue<>();
    private final AtomicLong failureCount = new AtomicLong();

    // ---- samples ----
    private final List<long[]> heapSamples = new ArrayList<>();      // [usedBytes]
    private final List<int[]>  residencySamples = new ArrayList<>(); // [resident, chests, dirtyChests, dirtyPlayers, loading, threads]
    private volatile boolean deadlockSeen = false;

    // ---- online-player claim set (prevents two workers being the same player at once) ----
    private final Map<UUID, Boolean> online = new ConcurrentHashMap<>();

    @Test
    void simulate() throws Exception {
        Logger log = LoggerFactory.getLogger("stress-sim");
        Path dir = Files.createTempDirectory("echest-stress");
        // Each player is assigned a random number of ender chests up front, drawn from a realistic
        // skewed distribution (most players own just the base chest, a long tail hoards many). This is
        // materialized on that player's first join, so the residency cache sees a lifelike mix of light
        // and heavy owners rather than everyone holding exactly one chest.
        UUID[] universe = new UUID[PLAYER_UNIVERSE];
        int[] chestTarget = new int[PLAYER_UNIVERSE];
        ThreadLocalRandom setupRnd = ThreadLocalRandom.current();
        for (int i = 0; i < PLAYER_UNIVERSE; i++) {
            universe[i] = UUID.randomUUID();
            chestTarget[i] = rollChestTarget(setupRnd);
        }

        CachedStorage storage = new CachedStorage(new SqliteStorage(dir, "stress.db", "echest_"), log, Telemetry.NOOP);
        storage.init();

        // Baseline heap after init + warmup GC.
        gc();
        long baselineUsed = usedHeap();

        AtomicBoolean running = new AtomicBoolean(true);
        LongAdder sessions = new LongAdder();
        LongAdder adminOps = new LongAdder();
        AtomicLong flushedRows = new AtomicLong();
        AtomicLong evicted = new AtomicLong();

        // Autosave: flush dirty rows then evict idle owners, just like AutosaveService.saveNow().
        ScheduledExecutorService autosave = Executors.newSingleThreadScheduledExecutor(r -> named(r, "autosave"));
        autosave.scheduleAtFixedRate(() -> {
            try {
                flushedRows.addAndGet(storage.flush());
                evicted.addAndGet(storage.evictIdle());
            } catch (Throwable t) { recordFail("autosave", t); }
        }, AUTOSAVE_MS, AUTOSAVE_MS, TimeUnit.MILLISECONDS);

        // Sampler: heap, residency, deadlock watch.
        Object cache = field(storage, "cache");
        Object state = field(storage, "state");
        ScheduledExecutorService sampler = Executors.newSingleThreadScheduledExecutor(r -> named(r, "sampler"));
        sampler.scheduleAtFixedRate(() -> {
            heapSamples.add(new long[]{ usedHeap() });
            residencySamples.add(new int[]{
                    size(cache, "resident"), size(state, "chests"), size(state, "dirtyChests"),
                    size(state, "dirtyPlayers"), size(cache, "loading"),
                    ManagementFactory.getThreadMXBean().getThreadCount() });
            if (ManagementFactory.getThreadMXBean().findDeadlockedThreads() != null) deadlockSeen = true;
        }, 0, SAMPLE_MS, TimeUnit.MILLISECONDS);

        long deadline = System.currentTimeMillis() + RUN_MILLIS;
        ExecutorService workers = Executors.newFixedThreadPool(WORKER_THREADS, r -> named(r, "player"));
        ExecutorService admins  = Executors.newFixedThreadPool(ADMIN_THREADS, r -> named(r, "admin"));
        CountDownLatch done = new CountDownLatch(WORKER_THREADS + ADMIN_THREADS);

        long wall0 = System.nanoTime();
        for (int i = 0; i < WORKER_THREADS; i++) {
            workers.submit(() -> { try { playerLoop(storage, universe, chestTarget, deadline, sessions); } finally { done.countDown(); } });
        }
        for (int i = 0; i < ADMIN_THREADS; i++) {
            admins.submit(() -> { try { adminLoop(storage, universe, deadline, adminOps); } finally { done.countDown(); } });
        }
        done.await(RUN_MILLIS + 60_000, TimeUnit.MILLISECONDS);
        running.set(false);
        long wallNanos = System.nanoTime() - wall0;

        workers.shutdown(); admins.shutdown();
        workers.awaitTermination(30, TimeUnit.SECONDS);
        admins.awaitTermination(30, TimeUnit.SECONDS);
        sampler.shutdownNow();

        // Quiesce: everyone has quit → a final flush + a couple of evictions must drain everything.
        flushedRows.addAndGet(storage.flush());
        evicted.addAndGet(storage.evictIdle());
        evicted.addAndGet(storage.evictIdle());
        autosave.shutdownNow();

        long peakUsed = heapSamples.stream().mapToLong(s -> s[0]).max().orElse(0);
        gc();
        long finalUsed = usedHeap();

        int residentEnd = size(cache, "resident");
        int chestsEnd   = size(state, "chests");
        int dirtyChEnd  = size(state, "dirtyChests");
        int dirtyPlEnd  = size(state, "dirtyPlayers");
        int loadingEnd  = size(cache, "loading");
        int pinnedEnd   = size(cache, "pinned");
        long dbChests   = storage.countChests();

        String report = buildReport(wallNanos, sessions.sum(), adminOps.sum(), flushedRows.get(), evicted.get(),
                baselineUsed, peakUsed, finalUsed, residentEnd, chestsEnd, dirtyChEnd, dirtyPlEnd,
                loadingEnd, pinnedEnd, dbChests, chestProfile(chestTarget, dbChests));
        System.out.println(report);
        writeReport(report);

        storage.close();
        deleteTree(dir);

        // ---- verdicts ----
        assertEquals(0, failureCount.get(),
                "storage ops threw during the run (see report for the first few):\n" + firstFailures());
        assertNull(ManagementFactory.getThreadMXBean().findDeadlockedThreads(), "deadlocked threads at end");
        assertTrue(!deadlockSeen, "a deadlock was observed during the run");
        assertEquals(0, residentEnd + chestsEnd + dirtyChEnd + dirtyPlEnd + loadingEnd + pinnedEnd,
                "residency/dirty structures not empty after quiesce — memory leak "
                + "(resident=" + residentEnd + " chests=" + chestsEnd + " dirtyChests=" + dirtyChEnd
                + " dirtyPlayers=" + dirtyPlEnd + " loading=" + loadingEnd + " pinned=" + pinnedEnd + ")");
    }

    // ---- one player's join → play → quit session, looped until the deadline ----
    private void playerLoop(CachedStorage s, UUID[] universe, int[] chestTarget, long deadline, LongAdder sessions) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        while (System.currentTimeMillis() < deadline) {
            int slot = claim(universe, rnd);
            if (slot < 0) { sleep(1); continue; }               // server "full" on distinct players — retry
            UUID p = universe[slot];
            int target = chestTarget[slot];
            try {
                run("join.pin", () -> s.pin(p));
                call("join.loadSettings", () -> s.loadSettings(p));   // the real join prefetch (materializes owner)
                List<ChestSummary> chests = call("listChests", () -> s.listChests(p));
                if (chests == null || chests.isEmpty()) {
                    // First-ever join: materialize this player's randomly-sized chest set — the base chest
                    // plus (target-1) more with random sizes, a fraction of them temporary (with an expiry).
                    call("createChest.bootstrap", () -> s.createChest(p, 54));
                    run("upsertPlayerName", () -> s.upsertPlayerName(p, "Player-" + Integer.toHexString(p.hashCode())));
                    for (int k = 1; k < target; k++) {
                        Long expiry = rnd.nextInt(6) == 0 ? System.currentTimeMillis() + rnd.nextInt(1, 250) : null;
                        call("createChest.bootstrap", () -> s.createChest(p, randomSize(rnd), expiry));
                    }
                    chests = call("listChests", () -> s.listChests(p));
                }

                // The player's live set of chest indices; the base (lowest index) is kept inviolable, like
                // the real plugin — deletes only ever target a non-base chest.
                List<Integer> owned = new ArrayList<>();
                if (chests != null) for (ChestSummary cs : chests) owned.add(cs.index());
                if (owned.isEmpty()) owned.add(1);
                owned.sort(null);

                int cycles = rnd.nextInt(2, 12);
                for (int c = 0; c < cycles; c++) {
                    // Model both open paths: ~25% open the "main" chest (like /ec reads getPrimaryIndex),
                    // otherwise pick a random owned chest (like choosing one from the /eclist dialog).
                    int idx;
                    if (rnd.nextInt(4) == 0) {
                        Integer primary = call("getPrimaryIndex", () -> s.getPrimaryIndex(p));
                        idx = (primary == null || !owned.contains(primary)) ? owned.get(0) : primary;
                    } else {
                        idx = owned.get(rnd.nextInt(owned.size()));
                    }
                    call("loadChest", () -> s.loadChest(p, idx));
                    run("saveChest", () -> s.saveChest(p, idx, blob(rnd)));
                    double roll = rnd.nextDouble();
                    // Create/delete churn oscillates around this player's random target, so the per-player
                    // count stays near its assigned value all run long (rather than drifting upward).
                    if (roll < 0.06 && owned.size() < target) {      // grow toward target
                        Integer ni = call("createChest", () -> s.createChest(p, randomSize(rnd)));
                        if (ni != null) owned.add(ni);
                    } else if (roll < 0.12 && owned.size() > 1) {     // shrink: delete a non-base chest
                        int victim = owned.get(1 + rnd.nextInt(owned.size() - 1));
                        run("deleteChest", () -> s.deleteChest(p, victim));
                        owned.remove(Integer.valueOf(victim));
                    } else if (roll < 0.15) run("renameChest", () -> s.renameChest(p, idx, "v" + rnd.nextInt(1000)));
                    else if (roll < 0.18) run("resizeChest", () -> s.resizeChest(p, idx, randomSize(rnd)));
                    else if (roll < 0.21) run("setPrimary",  () -> s.setPrimary(p, idx));
                    else if (roll < 0.24) run("setEditMode", () -> s.setEditMode(p, rnd.nextBoolean()));
                    if (rnd.nextInt(3) == 0) sleep(rnd.nextInt(1, 4));   // think time so sessions interleave
                }
                sessions.increment();
            } finally {
                run("quit.unpin", () -> s.unpin(p));
                run("quit.flushOwner", () -> s.flushOwner(p));          // per-player write-back + evict
                online.remove(p);
            }
        }
    }

    // ---- admin threads: read random (often offline) players → on-demand load + evictIdle churn ----
    private void adminLoop(CachedStorage s, UUID[] universe, long deadline, LongAdder adminOps) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        while (System.currentTimeMillis() < deadline) {
            UUID p = universe[rnd.nextInt(universe.length)];
            switch (rnd.nextInt(5)) {
                case 0 -> call("admin.listChests", () -> s.listChests(p));
                case 1 -> call("admin.getAppliedDefaultSize", () -> s.getAppliedDefaultSize(p));
                case 2 -> call("admin.loadChest", () -> s.loadChest(p, 1));
                case 3 -> call("admin.findExpired", () -> s.findExpired(System.currentTimeMillis()));
                default -> call("admin.countChests", () -> s.countChests());
            }
            adminOps.increment();
            sleep(rnd.nextInt(1, 6));
        }
    }

    /** Atomically claim a distinct offline player, returning its universe slot, or -1 after a few misses. */
    private int claim(UUID[] universe, ThreadLocalRandom rnd) {
        for (int tries = 0; tries < 6; tries++) {
            int slot = rnd.nextInt(universe.length);
            if (online.putIfAbsent(universe[slot], Boolean.TRUE) == null) return slot;
        }
        return -1;
    }

    // ---- timing wrappers (capture every throwable, keep the sim running) ----
    private void run(String label, ThrowingRunnable r) {
        long t0 = System.nanoTime();
        try { r.run(); } catch (Throwable t) { recordFail(label, t); }
        finally { record(label, System.nanoTime() - t0); }
    }
    private <T> T call(String label, ThrowingSupplier<T> sup) {
        long t0 = System.nanoTime();
        try { return sup.get(); } catch (Throwable t) { recordFail(label, t); return null; }
        finally { record(label, System.nanoTime() - t0); }
    }

    private void record(String label, long nanos) {
        ops.computeIfAbsent(label, OpStat::new).add(nanos);
        long us = nanos / 1_000;
        for (int i = 0; i < HB_US.length; i++) if (us < HB_US[i]) { histo[i].incrementAndGet(); break; }
    }
    private void recordFail(String label, Throwable t) {
        failureCount.incrementAndGet();
        if (failures.size() < 25) {
            StringBuilder sb = new StringBuilder(label).append(" -> ").append(t);
            StackTraceElement[] st = t.getStackTrace();
            for (int i = 0; i < Math.min(6, st.length); i++) sb.append("\n      at ").append(st[i]);
            failures.add(sb.toString());
        }
    }

    // ---- report ----
    private String buildReport(long wallNanos, long sessions, long adminOps, long flushedRows, long evicted,
                               long baselineUsed, long peakUsed, long finalUsed,
                               int residentEnd, int chestsEnd, int dirtyChEnd, int dirtyPlEnd,
                               int loadingEnd, int pinnedEnd, long dbChests, String chestProfile) {
        long totalOps = ops.values().stream().mapToLong(o -> o.count.sum()).sum();
        double wallSec = wallNanos / 1e9;
        StringBuilder r = new StringBuilder();
        r.append("\n============ EnhancedEchest storage stress simulation ============\n");
        r.append(String.format("players(universe)=%d  workerThreads=%d  adminThreads=%d  run=%.1fs%n",
                PLAYER_UNIVERSE, WORKER_THREADS, ADMIN_THREADS, wallSec));
        r.append(String.format("sessions(join→play→quit)=%d  adminOps=%d  storageOps=%d  throughput=%.0f ops/s%n",
                sessions, adminOps, totalOps, totalOps / wallSec));
        r.append(String.format("autosave flushedRows=%d  evictedOwners=%d  finalDbChests=%d%n",
                flushedRows, evicted, dbChests));

        r.append(chestProfile);

        r.append("\n-- per-op latency (count / avg / max, ms) --\n");
        ops.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> {
                    OpStat o = e.getValue();
                    long n = o.count.sum();
                    double avg = n == 0 ? 0 : (o.sum.sum() / 1e6) / n;
                    r.append(String.format("  %-28s n=%-8d avg=%7.3f  max=%8.3f%n",
                            e.getKey(), n, avg, o.max.get() / 1e6));
                });

        r.append("\n-- overall latency distribution --\n");
        long tot = 0; for (AtomicLong b : histo) tot += b.get();
        long cum = 0;
        for (int i = 0; i < histo.length; i++) {
            long c = histo[i].get(); cum += c;
            if (c == 0) continue;
            r.append(String.format("  %-8s %10d  (%5.1f%% cum)%n", HB_LABEL[i], c, 100.0 * cum / Math.max(1, tot)));
        }
        r.append("  approx p50=").append(pct(tot, 0.50)).append("  p95=").append(pct(tot, 0.95))
                .append("  p99=").append(pct(tot, 0.99)).append('\n');

        r.append("\n-- memory (heap used) --\n");
        r.append(String.format("  baseline=%s  peak=%s  final(after gc)=%s  growth=%s%n",
                mb(baselineUsed), mb(peakUsed), mb(finalUsed), mb(finalUsed - baselineUsed)));

        r.append("\n-- residency over time (min / max across samples) --\n");
        r.append(String.format("  resident owners : %d / %d%n", minCol(0), maxCol(0)));
        r.append(String.format("  chest-map size  : %d / %d%n", minCol(1), maxCol(1)));
        r.append(String.format("  dirtyChests     : %d / %d%n", minCol(2), maxCol(2)));
        r.append(String.format("  dirtyPlayers    : %d / %d%n", minCol(3), maxCol(3)));
        r.append(String.format("  loading (inflt) : %d / %d%n", minCol(4), maxCol(4)));
        r.append(String.format("  live threads    : %d / %d%n", minCol(5), maxCol(5)));

        r.append("\n-- leak check (must all be 0 after quiesce) --\n");
        r.append(String.format("  resident=%d chests=%d dirtyChests=%d dirtyPlayers=%d loading=%d pinned=%d%n",
                residentEnd, chestsEnd, dirtyChEnd, dirtyPlEnd, loadingEnd, pinnedEnd));

        r.append("\n-- errors --\n");
        r.append("  storage-op failures: ").append(failureCount.get()).append('\n');
        r.append("  deadlock observed  : ").append(deadlockSeen).append('\n');
        if (!failures.isEmpty()) { r.append("  first failures:\n"); failures.forEach(f -> r.append("    ").append(f).append('\n')); }

        boolean pass = failureCount.get() == 0 && !deadlockSeen
                && (residentEnd + chestsEnd + dirtyChEnd + dirtyPlEnd + loadingEnd + pinnedEnd) == 0;
        r.append("\n==================== VERDICT: ").append(pass ? "PASS" : "FAIL").append(" ====================\n");
        return r.toString();
    }

    private String pct(long total, double p) {
        long want = (long) Math.ceil(total * p), cum = 0;
        for (int i = 0; i < histo.length; i++) { cum += histo[i].get(); if (cum >= want) return HB_LABEL[i]; }
        return HB_LABEL[HB_LABEL.length - 1];
    }
    private int minCol(int c) { return residencySamples.stream().mapToInt(s -> s[c]).min().orElse(0); }
    private int maxCol(int c) { return residencySamples.stream().mapToInt(s -> s[c]).max().orElse(0); }
    private String firstFailures() { StringBuilder b = new StringBuilder(); failures.forEach(f -> b.append(f).append('\n')); return b.toString(); }

    private void writeReport(String report) {
        try {
            Path out = Path.of("build", "reports", "stress", "stress-report.txt");
            Files.createDirectories(out.getParent());
            Files.writeString(out, report);
        } catch (Exception ignored) { }
    }

    // ---- helpers ----
    /** A realistic, skewed per-player chest count: most own just the base chest, a long tail hoards many. */
    private static int rollChestTarget(ThreadLocalRandom rnd) {
        double r = rnd.nextDouble();
        if (r < 0.50) return 1;                   // 50%: just the base chest
        if (r < 0.78) return rnd.nextInt(2, 4);   // 28%: 2–3
        if (r < 0.93) return rnd.nextInt(4, 9);   // 15%: 4–8
        if (r < 0.99) return rnd.nextInt(9, 21);  //  6%: 9–20
        return rnd.nextInt(21, 41);               //  1%: 21–40 (hoarders)
    }

    /** A valid ender-chest size: a multiple of 9 in [9, 54]. */
    private static int randomSize(ThreadLocalRandom rnd) { return 9 * rnd.nextInt(1, 7); }

    /** A report block making the random per-player chest distribution visible. */
    private static String chestProfile(int[] target, long dbChests) {
        int n = target.length, min = Integer.MAX_VALUE, max = 0;
        long sum = 0;
        int[] b = new int[5];
        for (int t : target) {
            sum += t;
            min = Math.min(min, t);
            max = Math.max(max, t);
            if (t == 1) b[0]++; else if (t <= 3) b[1]++; else if (t <= 8) b[2]++; else if (t <= 20) b[3]++; else b[4]++;
        }
        return String.format(
                "%n-- chest-count profile (random per player, assigned at setup) --%n"
              + "  target chests/player : min=%d  avg=%.2f  max=%d%n"
              + "  distribution         : 1=%d  2-3=%d  4-8=%d  9-20=%d  21+=%d%n"
              + "  chests now in DB      : %d%n",
                min, (double) sum / n, max, b[0], b[1], b[2], b[3], b[4], dbChests);
    }

    private static byte[] blob(ThreadLocalRandom rnd) { byte[] b = new byte[rnd.nextInt(200, 2000)]; rnd.nextBytes(b); return b; }
    private static void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }
    private static long usedHeap() { Runtime rt = Runtime.getRuntime(); return rt.totalMemory() - rt.freeMemory(); }
    private static String mb(long bytes) { return String.format("%.1f MB", bytes / (1024.0 * 1024.0)); }
    private static void gc() { for (int i = 0; i < 3; i++) { System.gc(); sleep(120); } }
    private static Thread named(Runnable r, String prefix) { Thread t = new Thread(r); t.setName("sim-" + prefix + "-" + t.getId()); t.setDaemon(true); return t; }
    private static AtomicLong[] newHisto() { AtomicLong[] a = new AtomicLong[HB_US.length]; for (int i = 0; i < a.length; i++) a[i] = new AtomicLong(); return a; }

    private static Object field(Object o, String name) {
        try { Class<?> c = o.getClass(); while (c != null) { for (Field f : c.getDeclaredFields()) if (f.getName().equals(name)) { f.setAccessible(true); return f.get(o); } c = c.getSuperclass(); } }
        catch (Exception e) { throw new RuntimeException("field " + name, e); }
        throw new RuntimeException("no field " + name + " on " + o.getClass());
    }
    private static int size(Object owner, String field) {
        Object v = field(owner, field);
        if (v instanceof Map<?, ?> m) return m.size();
        if (v instanceof Collection<?> c) return c.size();
        return -1;
    }
    private static void deleteTree(Path dir) {
        try (var s = Files.walk(dir)) { s.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) { } }); }
        catch (Exception ignored) { }
    }

    @FunctionalInterface private interface ThrowingRunnable { void run() throws Exception; }
    @FunctionalInterface private interface ThrowingSupplier<T> { T get() throws Exception; }

    private static final class OpStat {
        final LongAdder count = new LongAdder();
        final LongAdder sum = new LongAdder();
        final AtomicLong max = new AtomicLong();
        OpStat(String label) { }
        void add(long nanos) { count.increment(); sum.add(nanos); max.accumulateAndGet(nanos, Math::max); }
    }
}
