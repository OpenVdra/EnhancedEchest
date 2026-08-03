package com.enhancedechest.service;

import com.enhancedechest.telemetry.Telemetry;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two load simulations for the activity log, sharing one workload generator and one set of
 * measurement helpers ({@code ./gradlew stressTest}).
 *
 * <ul>
 *   <li>{@link #compareLogDisabledAndEnabled()} — what the feature <b>costs</b>: the same 450-player
 *       workload with the log off, on, and in its shipped configuration.</li>
 *   <li>{@link #enabledPipelineDoesNotRetainHistory()} — what the feature <b>retains</b>: 68,000 visits
 *       must leave the logger exactly as large as it started.</li>
 * </ul>
 *
 * <p>Bukkit item capture cannot run reliably under plain JUnit because it needs a live Paper registry,
 * so inputs here are immutable snapshots equivalent to the DTOs produced immediately after capture.
 * The pipeline under test is the exact production implementation, not a mock, but the cost numbers are
 * a floor: they exclude the Bukkit-side capture that production pays on a region thread.
 */
class ChestActivityLogSimulationTest {

    private static final int PLAYERS = 450;
    private static final int CHEST_SIZE = 54;
    /** Share of visits that actually change the chest, in both simulations. */
    private static final int CHANGED_PERCENT = 30;
    private static final int QUEUE_CAPACITY = 4096;

    // ---- A/B run ----
    private static final int AB_PRODUCERS = 32;
    private static final int TARGET_CYCLES_PER_SECOND = 200;
    private static final int DURATION_SECONDS = 10;
    private static final int AB_VISITS = TARGET_CYCLES_PER_SECOND * DURATION_SECONDS;

    // ---- leak run ----
    private static final int LEAK_PRODUCERS = 16;
    private static final int ROUNDS = 40;
    private static final int VISITS_PER_ROUND = 1_500;
    /** Chests deliberately left open when a round ends, to keep the baseline map non-empty throughout. */
    private static final int ORPHANS = 200;
    private static final int LEAK_CYCLES = ROUNDS * (VISITS_PER_ROUND + ORPHANS);
    private static final int ORPHAN_CHEST_INDEX = 9;
    /**
     * A slice of each round closes on freshly minted item identities, in {@value #IDENTITY_VARIANTS}
     * variants per round. Over the run that mints far more distinct identities than the shared cache
     * can hold, which is what exercises its clear-when-full path.
     */
    private static final int FRESH_IDENTITY_PERCENT = 3;
    private static final int IDENTITY_VARIANTS = 4;
    /**
     * Retained-heap budget for the whole leak run. A pipeline that leaked even ~200 bytes per visit
     * would blow through this; the logger is a fixed-size pipeline, so the real figure should be ~0.
     */
    private static final long MAX_GROWTH_BYTES = 12L * 1024 * 1024;

    /**
     * Every distinct item identity the leak run feeds the logger. Only touched from the test thread,
     * while a round's snapshots are built. Its size is what proves the cache-ceiling check is not
     * vacuous: a run that minted fewer identities than the ceiling would never test the clear at all.
     */
    private final Set<String> minted = new HashSet<>();

    // =================================================================================
    // A/B: what the log costs
    // =================================================================================

    /**
     * All three scenarios receive the same deterministic 450-player workload at 200 chest visits/second:
     *
     * <ul>
     *   <li><b>OFF</b> is the shipped default and measures only the call-site fast path.</li>
     *   <li><b>ON</b> sets {@code log-unchanged=true}, so every visit goes through the bounded enqueue,
     *       the O(n) ADD/TAKE diff, batched UTF-8 output, rotation, gzip and drain.</li>
     *   <li><b>MIXED</b> is the shipped configuration on a realistic server: {@code log-unchanged=false}
     *       with {@value #CHANGED_PERCENT}% of visits actually changing the chest, the rest discarded.</li>
     * </ul>
     */
    @Test
    void compareLogDisabledAndEnabled() throws Exception {
        UUID[] players = players("activity-player-");
        List<ChestActivityLogger.CapturedStack> opened = snapshot(0, false);
        List<ChestActivityLogger.CapturedStack> closed = snapshot(0, true);

        // Small untimed warmup for class loading/JIT and filesystem initialization.
        runScenario(true, true, 100, 250, 1_000, players, opened, closed, "warmup");

        // Each scenario runs twice, the second pass in mirrored order. Running them once in a fixed
        // order charges whatever GC the earlier phases queued up to the later ones, which is enough to
        // make the cheapest scenario look like the most expensive. Keep the better pass of each.
        Result off1 = runScenario(false, true, 100, AB_VISITS, TARGET_CYCLES_PER_SECOND,
                players, opened, closed, "off-1");
        Result on1 = runScenario(true, true, 100, AB_VISITS, TARGET_CYCLES_PER_SECOND,
                players, opened, closed, "on-1");
        Result mixed1 = runScenario(true, false, CHANGED_PERCENT, AB_VISITS, TARGET_CYCLES_PER_SECOND,
                players, opened, closed, "mixed-1");
        Result mixed2 = runScenario(true, false, CHANGED_PERCENT, AB_VISITS, TARGET_CYCLES_PER_SECOND,
                players, opened, closed, "mixed-2");
        Result on2 = runScenario(true, true, 100, AB_VISITS, TARGET_CYCLES_PER_SECOND,
                players, opened, closed, "on-2");
        Result off2 = runScenario(false, true, 100, AB_VISITS, TARGET_CYCLES_PER_SECOND,
                players, opened, closed, "off-2");

        Result off = quieter(off1, off2);
        Result on = quieter(on1, on2);
        Result mixed = quieter(mixed1, mixed2);

        writeReport("activity-log-comparison.txt", comparisonReport(off, on, mixed));

        assertEquals(AB_VISITS, on.stats().accepted(), "enabled pipeline did not accept every cycle");
        assertEquals(AB_VISITS, on.stats().written(), "enabled pipeline did not flush every cycle");
        assertEquals(0, on.stats().dropped(), "enabled pipeline dropped cycles at the target load");
        assertEquals(0, on.stats().queued(), "enabled pipeline queue was not drained at shutdown");
        assertEquals(0, off.stats().accepted(), "disabled logger should take the zero-work fast path");
        assertTrue(on.bytesWritten() > 0, "enabled logger produced no audit file");
        assertEquals(0, off.bytesWritten(), "disabled logger unexpectedly wrote audit content");
        assertTrue(on.maxQueueDepth() < QUEUE_CAPACITY, "queue reached its configured bound");
        assertTrue(on.gzipFiles() > 0, "stress run did not exercise rotation + gzip");

        // The shipped default drops visits that changed nothing, so only the rest reach the disk.
        int changed = AB_VISITS * CHANGED_PERCENT / 100;
        assertEquals(changed, mixed.stats().accepted(), "mixed run enqueued the wrong number of visits");
        assertEquals(AB_VISITS - changed, mixed.stats().unchanged(), "unchanged visits miscounted");
        assertTrue(mixed.stats().uncompressedBytes() < on.stats().uncompressedBytes(),
                "skipping unchanged visits must write less than logging everything");
    }

    private Result runScenario(boolean enabled, boolean logUnchanged, int changedPercent,
                               int cycles, int targetCps, UUID[] players,
                               List<ChestActivityLogger.CapturedStack> opened,
                               List<ChestActivityLogger.CapturedStack> closed,
                               String label) throws Exception {
        Path dir = Files.createTempDirectory("echest-activity-" + label);
        ThreadMXBean threadMx = ManagementFactory.getThreadMXBean();
        if (threadMx.isCurrentThreadCpuTimeSupported() && !threadMx.isThreadCpuTimeEnabled()) {
            threadMx.setThreadCpuTimeEnabled(true); // benchmark-only; production does not enable it
        }
        ChestActivityLogger logger = newLogger(dir, label, enabled, logUnchanged);
        ExecutorService producers = Executors.newFixedThreadPool(AB_PRODUCERS);
        CountDownLatch done = new CountDownLatch(cycles);
        long[] latencies = new long[cycles];
        AtomicLong maxQueue = new AtomicLong();
        long wallStart = System.nanoTime();
        long periodNanos = TimeUnit.SECONDS.toNanos(1) / targetCps;

        for (int i = 0; i < cycles; i++) {
            long target = wallStart + i * periodNanos;
            long wait = target - System.nanoTime();
            if (wait > 0) LockSupport.parkNanos(wait);
            final int sequence = i;
            // Deterministic mix: the first changedPercent out of every 100 visits actually changed
            // something, the rest are someone opening their chest, looking, and closing it again.
            final boolean changed = sequence % 100 < changedPercent;
            producers.execute(() -> {
                long start = System.nanoTime();
                UUID player = players[sequence % players.length];
                logger.recordCapturedCycle("Player-" + (sequence % players.length),
                        player, player, 1 + (sequence % 4), CHEST_SIZE,
                        opened, changed ? closed : opened);
                latencies[sequence] = System.nanoTime() - start;
                maxQueue.accumulateAndGet(logger.pipelineStats().queued(), Math::max);
                done.countDown();
            });
        }

        assertTrue(done.await(30, TimeUnit.SECONDS), "producer tasks timed out");
        producers.shutdown();
        assertTrue(producers.awaitTermination(10, TimeUnit.SECONDS), "producer pool did not stop");
        logger.shutdown();
        long wallNanos = System.nanoTime() - wallStart;
        ChestActivityLogger.PipelineStats stats = logger.pipelineStats();
        long bytes = logBytes(dir, null);
        long gzipBytes = logBytes(dir, ".log.gz");
        long plainBytes = logBytes(dir, ".log");
        long gzipFiles = gzipFileCount(dir);
        deleteTree(dir);
        Arrays.sort(latencies);
        return new Result(enabled, cycles, wallNanos,
                average(latencies), percentile(latencies, 0.50), percentile(latencies, 0.95),
                percentile(latencies, 0.99), latencies[latencies.length - 1],
                maxQueue.get(), bytes, gzipBytes, plainBytes, gzipFiles, stats);
    }

    // =================================================================================
    // Leak: what the log retains
    // =================================================================================

    /**
     * The log's only structures that could grow with <i>history</i> rather than with what is happening
     * right now are the open-cycle baselines, the bounded work queue, the shared identity caches and the
     * writer thread. This drives {@value #ROUNDS} rounds of {@value #VISITS_PER_ROUND} full OPEN→CLOSE
     * lifecycles through the real pipeline and checks after every round that none of them grew:
     *
     * <ul>
     *   <li><b>Open-cycle baselines</b> — each round also leaves {@value #ORPHANS} chests open across the
     *       round boundary (half closed late, half abandoned, as a force-close does), so the map is never
     *       trivially empty. It must still measure exactly the chests open <i>now</i>, never the run.</li>
     *   <li><b>Accounting</b> — every OPEN must be consumed exactly once, by a write, a drop or an
     *       unchanged/abandoned discard. A close path that ever failed to consume its baseline would
     *       leave an entry behind forever, which is precisely how this map would leak in production.</li>
     *   <li><b>Identity caches</b> — deliberately static and shared, so the run feeds them far more
     *       distinct items than their ceiling and checks the whole-cache clear keeps them bounded.</li>
     *   <li><b>Queue and worker thread</b> — drained at shutdown and the daemon thread actually gone.</li>
     *   <li><b>Heap</b> — used heap after a full GC must come back to where it started.</li>
     * </ul>
     */
    @Test
    void enabledPipelineDoesNotRetainHistory() throws Exception {
        UUID[] players = players("leak-player-");
        List<ChestActivityLogger.CapturedStack> opened = snapshot(0, false);
        List<ChestActivityLogger.CapturedStack> closed = snapshot(0, true);

        Path dir = Files.createTempDirectory("echest-activity-leak");
        ChestActivityLogger logger = newLogger(dir, "leak", true, false);
        ExecutorService producers = Executors.newFixedThreadPool(LEAK_PRODUCERS);
        int threadsBefore = workerThreadCount();
        gc();
        long baselineHeap = usedHeap();

        List<long[]> samples = new ArrayList<>(ROUNDS);   // [round, openCycles, queued, usedHeap]
        long midRoundHeap = 0;
        try {
            for (int round = 0; round < ROUNDS; round++) {
                // Retire the previous round's still-open chests: half close late, half are abandoned
                // exactly as a force-close on an untouched chest does.
                if (round > 0) retireOrphans(logger, producers, players, round - 1, opened);
                runVisits(logger, producers, players, round, opened, closed);
                openOrphans(logger, producers, players, round, opened);
                awaitDrain(logger);

                samples.add(new long[]{ round, logger.openCycleCount(),
                        logger.pipelineStats().queued(), usedHeap() });
                if (round == ROUNDS / 2 - 1) {
                    gc();
                    midRoundHeap = usedHeap();
                }
            }
            retireOrphans(logger, producers, players, ROUNDS - 1, opened);
            awaitDrain(logger);
        } finally {
            producers.shutdown();
            assertTrue(producers.awaitTermination(30, TimeUnit.SECONDS), "producer pool did not stop");
        }

        int openBeforeShutdown = logger.openCycleCount();
        logger.shutdown();
        ChestActivityLogger.PipelineStats stats = logger.pipelineStats();
        long onDisk = logBytes(dir, null);
        deleteTree(dir);

        gc();
        long finalHeap = usedHeap();
        long peakHeap = samples.stream().mapToLong(s -> s[3]).max().orElse(0);
        long maxOpen = samples.stream().mapToLong(s -> s[1]).max().orElse(0);
        long maxQueued = samples.stream().mapToLong(s -> s[2]).max().orElse(0);
        int threadsAfter = workerThreadCount();
        long growth = finalHeap - baselineHeap;

        writeReport("activity-log-leak.txt", leakReport(stats, samples, baselineHeap, midRoundHeap,
                peakHeap, finalHeap, maxOpen, maxQueued, openBeforeShutdown,
                threadsBefore, threadsAfter, onDisk));

        // ---- verdicts ----
        assertEquals(LEAK_CYCLES, stats.accepted() + stats.dropped() + stats.unchanged(),
                "an OPEN baseline was never consumed — openCycles leaks one entry per orphaned visit");
        assertEquals(0, stats.dropped(), "cycles were dropped: the run outpaced the writer, not a leak "
                + "but it invalidates the accounting above");
        assertEquals(stats.accepted(), stats.written(), "queued work was not all written");
        assertEquals(0, stats.queued(), "queue was not drained at shutdown");
        assertEquals(ORPHANS, maxOpen,
                "open-cycle map grew past the chests actually open (" + maxOpen + " > " + ORPHANS + ")");
        assertEquals(0, openBeforeShutdown, "open cycles survived their closes");
        assertTrue(minted.size() > ChestActivityLogger.identityCacheLimit(),
                "the run minted only " + minted.size() + " distinct identities, so it never reached the "
                + "cache ceiling and the bound below proves nothing");
        assertTrue(ChestActivityLogger.identityCacheSize() <= ChestActivityLogger.identityCacheLimit(),
                "identity cache exceeded its ceiling: " + ChestActivityLogger.identityCacheSize());
        assertEquals(0, threadsAfter, "the writer thread outlived shutdown()");
        assertTrue(growth < MAX_GROWTH_BYTES, "heap grew " + mb(growth) + " over " + LEAK_CYCLES
                + " cycles (" + (growth / LEAK_CYCLES) + " bytes/cycle retained)");
    }

    /** One round of complete OPEN→CLOSE visits, {@value #CHANGED_PERCENT}% of them actually changed. */
    private void runVisits(ChestActivityLogger logger, ExecutorService producers, UUID[] players,
                           int round, List<ChestActivityLogger.CapturedStack> opened,
                           List<ChestActivityLogger.CapturedStack> closed) throws Exception {
        List<List<ChestActivityLogger.CapturedStack>> fresh = new ArrayList<>(IDENTITY_VARIANTS);
        for (int variant = 0; variant < IDENTITY_VARIANTS; variant++) {
            List<ChestActivityLogger.CapturedStack> variantStacks =
                    snapshot(1 + round * IDENTITY_VARIANTS + variant, true);
            fresh.add(variantStacks);
            // Every variant is used below, so each of these identities really does reach the cache.
            for (ChestActivityLogger.CapturedStack stack : variantStacks) minted.add(stack.identity());
        }
        CountDownLatch done = new CountDownLatch(VISITS_PER_ROUND);
        for (int i = 0; i < VISITS_PER_ROUND; i++) {
            final int sequence = i;
            producers.execute(() -> {
                try {
                    // (player, chest) is unique per sequence, so no two concurrent visits in a round
                    // share an open-cycle key — an overlap would break the accounting, not the logger.
                    int slot = sequence % players.length;
                    UUID player = players[slot];
                    int chest = 1 + sequence / players.length;
                    boolean changed = sequence % 100 < CHANGED_PERCENT;
                    boolean mintsIdentities = sequence % 100 < FRESH_IDENTITY_PERCENT;
                    String actor = "Player-" + slot;
                    logger.openCaptured(actor, player, player, chest, CHEST_SIZE, opened);
                    logger.closeCaptured(actor, player, player, chest, CHEST_SIZE, changed
                            ? (mintsIdentities
                                    ? fresh.get(sequence / 100 % IDENTITY_VARIANTS)   // hits all variants
                                    : closed)
                            : opened);
                } finally {
                    done.countDown();
                }
            });
        }
        assertTrue(done.await(60, TimeUnit.SECONDS), "round " + round + " visits timed out");
    }

    /** Leaves {@value #ORPHANS} chests open past the end of the round. */
    private void openOrphans(ChestActivityLogger logger, ExecutorService producers, UUID[] players,
                             int round, List<ChestActivityLogger.CapturedStack> opened) throws Exception {
        CountDownLatch done = new CountDownLatch(ORPHANS);
        for (int i = 0; i < ORPHANS; i++) {
            final UUID player = players[i % players.length];
            producers.execute(() -> {
                try {
                    logger.openCaptured("Orphan-" + player, player, player,
                            ORPHAN_CHEST_INDEX, CHEST_SIZE, opened);
                } finally {
                    done.countDown();
                }
            });
        }
        assertTrue(done.await(60, TimeUnit.SECONDS), "round " + round + " orphan opens timed out");
    }

    /** Closes half of a round's orphans late and abandons the other half, as a force-close does. */
    private void retireOrphans(ChestActivityLogger logger, ExecutorService producers, UUID[] players,
                               int round, List<ChestActivityLogger.CapturedStack> opened)
            throws Exception {
        CountDownLatch done = new CountDownLatch(ORPHANS);
        for (int i = 0; i < ORPHANS; i++) {
            final UUID player = players[i % players.length];
            final boolean abandon = i % 2 == 0;
            producers.execute(() -> {
                try {
                    if (abandon) {
                        logger.abandon(player, player, ORPHAN_CHEST_INDEX);
                    } else {
                        logger.closeCaptured("Orphan-" + player, player, player,
                                ORPHAN_CHEST_INDEX, CHEST_SIZE, opened);
                    }
                } finally {
                    done.countDown();
                }
            });
        }
        assertTrue(done.await(60, TimeUnit.SECONDS), "round " + round + " orphan retire timed out");
    }

    /**
     * Paces the producers to the writer instead of racing it: the point of the run is retention over
     * many rounds, and a backlog the writer never gets to drain would only measure the bounded queue.
     */
    private static void awaitDrain(ChestActivityLogger logger) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (logger.pipelineStats().queued() > 0 && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
    }

    // =================================================================================
    // shared workload
    // =================================================================================

    private static UUID[] players(String prefix) {
        UUID[] players = new UUID[PLAYERS];
        for (int i = 0; i < players.length; i++) {
            players[i] = UUID.nameUUIDFromBytes((prefix + i).getBytes());
        }
        return players;
    }

    private static ChestActivityLogger newLogger(Path dir, String label, boolean enabled,
                                                 boolean logUnchanged) {
        return new ChestActivityLogger(dir, LoggerFactory.getLogger("activity-stress-" + label),
                Telemetry.NOOP, enabled, logUnchanged, true, false,
                QUEUE_CAPACITY, 1, 14);   // 1 MiB deliberately exercises rotation + gzip in these runs
    }

    /**
     * A chest's contents: 42 occupied slots, 43 once a visit added something. {@code changed} produces
     * the closing half of a visit that moved items.
     *
     * <p>Salt 0 is the shared baseline both runs reuse for ordinary visits, and its items repeat across
     * slots like a real chest. A salted snapshot instead makes every slot its own identity, which is
     * what lets the leak run mint more of them than the shared identity cache can hold.
     */
    private static List<ChestActivityLogger.CapturedStack> snapshot(int salt, boolean changed) {
        List<ChestActivityLogger.CapturedStack> result = new ArrayList<>(43);
        for (int slot = 1; slot <= 42; slot++) {
            int type = slot % 14;
            boolean meta = type % 4 == 0;
            String tag = salt == 0 ? String.valueOf(type) : type + "-r" + salt + "s" + slot;
            String id = meta ? "meta:item-" + tag + ":abc" + tag : "plain:item-" + tag;
            String description = meta
                    ? "minecraft:item_" + tag + "{name=\"Custom " + tag + "\",meta=abc" + tag + "}"
                    : "minecraft:item_" + tag;
            // A realistic visit moves a few kinds of item and leaves the rest alone.
            int amount = 16 + slot % 48;
            if (changed) amount += (slot - 1) % 3 == 0 ? 7 : (slot - 1) % 5 == 0 ? -4 : 0;
            result.add(new ChestActivityLogger.CapturedStack(id, description, amount));
        }
        // One new item exercises ADD identity discovery.
        if (changed) {
            String tag = salt == 0 ? "" : "-r" + salt;
            result.add(new ChestActivityLogger.CapturedStack(
                    "plain:new-item" + tag, "minecraft:new_item" + tag, 32));
        }
        return List.copyOf(result);
    }

    // =================================================================================
    // reporting
    // =================================================================================

    private static void writeReport(String name, String report) throws Exception {
        System.out.println(report);
        Path path = Path.of("build", "reports", "stress", name);
        Files.createDirectories(path.getParent());
        Files.writeString(path, report);
    }

    /** The pass less disturbed by GC and scheduling, judged on the median rather than the mean. */
    private static Result quieter(Result first, Result second) {
        return first.p50Nanos() <= second.p50Nanos() ? first : second;
    }

    private static String comparisonReport(Result off, Result on, Result mixed) {
        return String.format(Locale.ROOT, """

                ========== EnhancedEchest activity log A/B stress ==========
                workload : players=%d producers=%d target=%d visits/s duration=%ds visits=%d
                chest    : %d slots, 42 occupied at open, 43 at close

                  OFF     activity-log disabled (the shipped default)
                  ON      enabled, log-unchanged=true  -> every visit is written
                  MIXED   enabled, log-unchanged=false -> only the %d%% that changed is written

                call cost on the calling thread, best of two passes in mirrored order
                compare on p50: avg carries GC outliers that belong to the machine, not the code
                              p50          avg       p95       p99       max
                  OFF     %9.1f us %8.1f %9.1f %9.1f %9.1f
                  ON      %9.1f us %8.1f %9.1f %9.1f %9.1f   %+.1f us vs OFF
                  MIXED   %9.1f us %8.1f %9.1f %9.1f %9.1f   %+.1f us vs OFF

                %s
                %s
                %s
                NOTE: this harness feeds pre-captured snapshots straight into the queue, so it does
                      NOT include the Bukkit-side capture that production pays on a region thread.
                      Treat these as a floor for the enabled cost, not an end-to-end measurement.
                ============================================================
                """,
                PLAYERS, AB_PRODUCERS, TARGET_CYCLES_PER_SECOND, DURATION_SECONDS, AB_VISITS,
                CHEST_SIZE, CHANGED_PERCENT,
                us(off.p50Nanos()), us(off.avgNanos()), us(off.p95Nanos()),
                us(off.p99Nanos()), us(off.maxNanos()),
                us(on.p50Nanos()), us(on.avgNanos()), us(on.p95Nanos()),
                us(on.p99Nanos()), us(on.maxNanos()), us(on.p50Nanos() - off.p50Nanos()),
                us(mixed.p50Nanos()), us(mixed.avgNanos()), us(mixed.p95Nanos()),
                us(mixed.p99Nanos()), us(mixed.maxNanos()), us(mixed.p50Nanos() - off.p50Nanos()),
                describe("OFF  ", off), describe("ON   ", on), describe("MIXED", mixed));
    }

    /** One scenario's pipeline and disk block. */
    private static String describe(String label, Result result) {
        if (result.stats().uncompressedBytes() == 0) {
            return String.format(Locale.ROOT,
                    "%s   nothing enqueued, nothing written, %d bytes on disk", label,
                    result.bytesWritten());
        }
        // The run always stops mid-file, so a chunk of raw output is still sitting uncompressed in the
        // active log. Measuring compression against the whole run therefore understates it badly and
        // moves with wherever the run happened to stop. Score only the bytes that actually rotated.
        long rotatedRaw = Math.max(0L, result.stats().uncompressedBytes() - result.plainBytes());
        double saving = rotatedRaw == 0 ? 0
                : (1.0 - result.gzipBytes() / (double) rotatedRaw) * 100.0;
        double steadyState = result.stats().uncompressedBytes() / (double) result.cycles()
                * (1.0 - saving / 100.0);
        return String.format(Locale.ROOT, """
                %s   enqueued=%d written=%d skipped-unchanged=%d dropped=%d maxQueue=%d
                        worker %.1f ms CPU (coarse: Windows quantizes thread CPU to ~15.6 ms)
                        raw %d bytes, %.0f/visit; gzip %.1f%% smaller over %d rotations
                        steady-state disk: %.0f bytes/visit""",
                label, result.stats().accepted(), result.stats().written(),
                result.stats().unchanged(), result.stats().dropped(), result.maxQueueDepth(),
                result.stats().workerCpuNanos() / 1e6,
                result.stats().uncompressedBytes(),
                result.stats().uncompressedBytes() / (double) result.cycles(),
                saving, result.gzipFiles(), steadyState);
    }

    private String leakReport(ChestActivityLogger.PipelineStats stats, List<long[]> samples,
                              long baseline, long mid, long peak, long finalHeap,
                              long maxOpen, long maxQueued, int openBeforeShutdown,
                              int threadsBefore, int threadsAfter, long onDisk) {
        StringBuilder curve = new StringBuilder();
        // Every eighth round, so the shape of the curve is visible without pages of numbers.
        for (long[] sample : samples) {
            if (sample[0] % 8 != 0 && sample[0] != samples.size() - 1) continue;
            curve.append(String.format(Locale.ROOT, "      round %2d : openCycles=%-4d queued=%-5d heap=%s%n",
                    sample[0], sample[1], sample[2], mb(sample[3])));
        }
        return String.format(Locale.ROOT, """

                ========== EnhancedEchest activity log leak stress ==========
                workload : players=%d producers=%d rounds=%d visits/round=%d orphans/round=%d
                           total OPEN cycles=%d, %d%% of visits changed the chest
                config   : enabled=true log-unchanged=false queue=%d rotate=1MiB

                -- pipeline totals --
                  enqueued=%d written=%d skipped-unchanged=%d dropped=%d
                  raw %d bytes, %d bytes left on disk at the end

                -- per-round samples (after each round quiesced) --
                %s
                -- open-cycle baselines (must equal the chests open right now, never the run) --
                  max after a round : %d   (expected %d: the orphans that round left open)
                  after final close : %d   (must be 0)
                  queued max        : %d   (bound %d)

                -- identity caches (static, shared, cleared whole when full) --
                  distinct identities minted by the run=%d   cache size=%d limit=%d

                -- writer thread --
                  before=%d after shutdown=%d   (must be 0)

                -- memory (heap used) --
                  baseline=%s  mid-run(after gc)=%s  peak=%s  final(after gc)=%s  growth=%s
                  retained per cycle: %d bytes
                  (the growth includes the harness's own set of %d minted identity strings, which is
                   still live at this point — the logger's share is smaller than the figure above)

                ==================== VERDICT: %s ====================
                """,
                PLAYERS, LEAK_PRODUCERS, ROUNDS, VISITS_PER_ROUND, ORPHANS, LEAK_CYCLES, CHANGED_PERCENT,
                QUEUE_CAPACITY,
                stats.accepted(), stats.written(), stats.unchanged(), stats.dropped(),
                stats.uncompressedBytes(), onDisk,
                curve,
                maxOpen, ORPHANS, openBeforeShutdown, maxQueued, QUEUE_CAPACITY,
                minted.size(),
                ChestActivityLogger.identityCacheSize(), ChestActivityLogger.identityCacheLimit(),
                threadsBefore, threadsAfter,
                mb(baseline), mb(mid), mb(peak), mb(finalHeap), mb(finalHeap - baseline),
                (finalHeap - baseline) / LEAK_CYCLES, minted.size(),
                leakVerdict(stats, maxOpen, openBeforeShutdown, threadsAfter, finalHeap - baseline));
    }

    private String leakVerdict(ChestActivityLogger.PipelineStats stats, long maxOpen,
                               int openBeforeShutdown, int threadsAfter, long growth) {
        boolean ok = stats.accepted() + stats.dropped() + stats.unchanged() == LEAK_CYCLES
                && stats.queued() == 0 && maxOpen == ORPHANS && openBeforeShutdown == 0
                && threadsAfter == 0 && growth < MAX_GROWTH_BYTES
                && minted.size() > ChestActivityLogger.identityCacheLimit()
                && ChestActivityLogger.identityCacheSize() <= ChestActivityLogger.identityCacheLimit();
        return ok ? "PASS (no leak)" : "FAIL";
    }

    // =================================================================================
    // measurement helpers
    // =================================================================================

    private static long average(long[] values) {
        long sum = 0;
        for (long value : values) sum += value;
        return sum / values.length;
    }

    private static long percentile(long[] sorted, double percentile) {
        int index = Math.min(sorted.length - 1, (int) Math.ceil(sorted.length * percentile) - 1);
        return sorted[index];
    }

    private static double us(long nanos) {
        return nanos / 1_000.0;
    }

    private static String mb(long bytes) {
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    /** Live writer threads; a logger that never joined its worker would leave one behind per instance. */
    private static int workerThreadCount() {
        return (int) Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> thread.getName().startsWith("EnhancedEchest-activity-log"))
                .count();
    }

    private static long usedHeap() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static void gc() {
        for (int i = 0; i < 3; i++) {
            System.gc();
            try {
                Thread.sleep(120);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /** Total bytes on disk, optionally restricted to one suffix (".log.gz" vs the plain ".log" tail). */
    private static long logBytes(Path dir, String suffix) throws Exception {
        Path logs = dir.resolve("logs");
        if (!Files.exists(logs)) return 0;
        try (var paths = Files.list(logs)) {
            return paths.filter(Files::isRegularFile)
                    // ".log" cannot match a ".log.gz" name, so one endsWith is enough for both.
                    .filter(path -> suffix == null || path.getFileName().toString().endsWith(suffix))
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }).sum();
        }
    }

    private static long gzipFileCount(Path dir) throws Exception {
        Path logs = dir.resolve("logs");
        if (!Files.exists(logs)) return 0;
        try (var paths = Files.list(logs)) {
            return paths.filter(path -> path.getFileName().toString().endsWith(".log.gz")).count();
        }
    }

    private static void deleteTree(Path root) throws Exception {
        try (var paths = Files.walk(root)) {
            paths.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(path -> path.toFile().delete());
        }
    }

    private record Result(boolean enabled, int cycles, long wallNanos,
                          long avgNanos, long p50Nanos, long p95Nanos, long p99Nanos, long maxNanos,
                          long maxQueueDepth, long bytesWritten, long gzipBytes, long plainBytes,
                          long gzipFiles, ChestActivityLogger.PipelineStats stats) {}
}
