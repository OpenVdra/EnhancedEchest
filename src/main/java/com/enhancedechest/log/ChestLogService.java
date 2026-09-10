package com.enhancedechest.log;

import com.enhancedechest.serialization.ContainerCodec;
import com.enhancedechest.telemetry.Telemetry;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Captures chest visits and persists each as one row (a contents snapshot + a change summary) in the
 * separate {@link ChestLogStore} SQLite database, for the in-game {@code /ee log} viewer. Replaced the
 * old plain-text audit logger.
 *
 * <h2>One row per visit</h2>
 * A visit is opened, maybe changed, then closed. Only the close is written, and only when something
 * actually changed — a visit that took or added nothing is never stored, so the database stays small
 * (this is deliberate and not configurable). The stored snapshot is the chest as it was left; the diff
 * is what moved while it was open, computed against the totals taken at open. Opening therefore does no
 * disk work and no encoding at all: it just remembers when the chest was opened and what it held.
 *
 * <h2>Where the work runs</h2>
 * The Bukkit-owned thread does the minimum: total the contents by material at open, and at close encode
 * the contents to bytes once ({@link ContainerCodec}, the same immutable format chests are stored in)
 * plus total them again for the diff. Both results are immutable, so they cross to the single writer
 * thread with no Bukkit object. The writer batches inserts into one transaction and prunes on a timer.
 * The queue is bounded, so a stalled disk drops the newest visits instead of growing the heap.
 */
public final class ChestLogService {

    /** Interned per-material namespaced key, so repeated captures don't re-allocate the same string. */
    private static final String[] MATERIAL_KEYS = new String[Material.values().length];

    /** Identity of one open visit: the same (actor, owner, index) the close will diff against. */
    private record OpenKey(UUID actor, UUID owner, int index) {}

    /** What a visit looked like at open: when, and its per-material totals for the diff. */
    private record OpenState(long openedAt, Map<String, Integer> counts) {}

    /** An encoded, thread-safe capture of a chest at close: bytes to store, totals to diff. */
    public record Capture(byte[] blob, Map<String, Integer> counts, int size) {}

    private static final int BATCH_SIZE = 128;
    private static final long POLL_MILLIS = 1_000L;

    private final ContainerCodec codec;
    private final Logger logger;
    private final Telemetry telemetry;
    private final ChestLogStore store;
    private final boolean storeReady;

    private final ArrayBlockingQueue<LogWrite> queue;
    private final ConcurrentHashMap<OpenKey, OpenState> openBaselines = new ConcurrentHashMap<>();
    private final AtomicLong dropped = new AtomicLong();
    private final Thread worker;

    private volatile boolean enabled;
    private volatile int retentionDays;
    private volatile int maxPerPlayer;
    private volatile long pruneIntervalMillis;
    private volatile boolean stopping;
    private long lastPruneAt;   // worker-thread confined

    public ChestLogService(ContainerCodec codec, Logger logger, Telemetry telemetry, ChestLogStore store,
                           boolean storeReady, boolean enabled, int queueCapacity, int retentionDays,
                           int maxPerPlayer, long pruneIntervalMillis) {
        this.codec = codec;
        this.logger = logger;
        this.telemetry = telemetry;
        this.store = store;
        this.storeReady = storeReady;
        this.enabled = enabled;
        this.retentionDays = retentionDays;
        this.maxPerPlayer = maxPerPlayer;
        this.pruneIntervalMillis = pruneIntervalMillis;
        this.queue = new ArrayBlockingQueue<>(Math.max(64, queueCapacity));
        if (storeReady) {
            this.worker = new Thread(this::runWriter, "EnhancedEchest-log");
            this.worker.setDaemon(true);
            this.worker.start();
        } else {
            this.worker = null;   // store failed to open: the whole feature is inert
        }
    }

    // ---- settings ----

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) openBaselines.clear();
    }

    public void setRetention(int retentionDays, int maxPerPlayer, long pruneIntervalMillis) {
        this.retentionDays = retentionDays;
        this.maxPerPlayer = maxPerPlayer;
        this.pruneIntervalMillis = pruneIntervalMillis;
    }

    // ---- capture gates (called by the session manager) ----

    /** Whether logging is on and running; a cheap volatile read callers use before building a snapshot. */
    public boolean isRecording() {
        return storeReady && enabled && !stopping;
    }

    /**
     * Whether the closing contents are worth capturing. A chest nobody clicked cannot have changed, so
     * there is nothing to record on close. Mirrors the old logger's pre-filter so the session manager's
     * call sites stay unchanged; a chest that <i>was</i> touched but ends up identical is still dropped
     * by the exact diff at close.
     */
    public boolean needsCapture(boolean chestTouched) {
        return isRecording() && chestTouched;
    }

    // ---- capture ----

    /**
     * Encodes and totals a chest's closing contents. Returns {@code null} if encoding fails, so the
     * caller simply skips logging that visit rather than aborting the chest operation.
     */
    public @Nullable Capture capture(ItemStack[] contents) {
        byte[] blob;
        try {
            blob = codec.encode(contents);
        } catch (Exception e) {
            logger.warn("Could not encode chest contents for the activity log; skipping this visit");
            telemetry.error(e, "log.encode");
            return null;
        }
        return new Capture(blob, countMaterials(contents), contents.length);
    }

    /**
     * Records the open baseline on the viewer's thread: when the chest was opened and what it held. No
     * disk work and no encoding — the snapshot is only ever taken at close.
     */
    public void opened(@Nullable String actorName, UUID actor, UUID owner, int index, ItemStack[] contents) {
        if (!isRecording()) return;
        openBaselines.put(new OpenKey(actor, owner, index),
                new OpenState(System.currentTimeMillis(), countMaterials(contents)));
    }

    /** Records a close from live contents (captures, then delegates to the shared-capture overload). */
    public void closed(@Nullable String actorName, UUID actor, UUID owner, int index, ItemStack[] contents) {
        if (!isRecording()) return;
        Capture cap = capture(contents);
        if (cap == null) {
            openBaselines.remove(new OpenKey(actor, owner, index));
            return;
        }
        closed(actorName, actor, owner, index, cap);
    }

    /**
     * Records a close from an already-taken capture, writing one row for the whole visit — but only if
     * the contents actually changed. Force-close and shutdown tear down one shared inventory with
     * several viewers; capturing once and passing it here keeps that O(1) in chest size.
     */
    public void closed(@Nullable String actorName, UUID actor, UUID owner, int index, Capture cap) {
        if (!isRecording()) return;
        OpenState base = openBaselines.remove(new OpenKey(actor, owner, index));
        if (base == null) return;   // no baseline (e.g. logging enabled mid-visit): nothing to diff against
        String diff = buildDiff(base.counts(), cap.counts());
        if (diff == null) return;   // nothing changed: not worth a row
        offer(new LogWrite(owner, index, actor, actorName, cap.size(),
                base.openedAt(), System.currentTimeMillis(), diff, cap.blob()));
    }

    /** Drops an open visit's baseline without writing, for a peek that changed nothing. */
    public void abandon(UUID actor, UUID owner, int index) {
        openBaselines.remove(new OpenKey(actor, owner, index));
    }

    // ---- diff / totals ----

    private static Map<String, Integer> countMaterials(ItemStack[] contents) {
        Map<String, Integer> totals = new HashMap<>(Math.max(16, contents.length));
        for (ItemStack item : contents) {
            if (item == null || item.getType().isAir() || item.getAmount() <= 0) continue;
            totals.merge(materialKey(item.getType()), item.getAmount(), Integer::sum);
        }
        return totals;
    }

    private static String materialKey(Material material) {
        int ordinal = material.ordinal();
        String key = MATERIAL_KEYS[ordinal];
        if (key == null) {
            key = material.getKey().toString();   // idempotent; a race just recomputes the same string
            MATERIAL_KEYS[ordinal] = key;
        }
        return key;
    }

    /**
     * Builds the change summary as {@code "<delta> <key>"} lines: additions (positive) first, then
     * removals, each group ordered by key. Returns {@code null} when nothing changed.
     */
    private static @Nullable String buildDiff(Map<String, Integer> open, Map<String, Integer> close) {
        Map<String, Integer> keys = new HashMap<>(open);
        close.forEach((k, v) -> keys.putIfAbsent(k, 0));
        List<DiffLine> lines = new ArrayList<>();
        for (String key : keys.keySet()) {
            int delta = close.getOrDefault(key, 0) - open.getOrDefault(key, 0);
            if (delta != 0) lines.add(new DiffLine(key, delta));
        }
        if (lines.isEmpty()) return null;
        lines.sort(Comparator.<DiffLine>comparingInt(l -> l.delta() > 0 ? 0 : 1).thenComparing(DiffLine::itemKey));
        StringBuilder sb = new StringBuilder(lines.size() * 24);
        for (DiffLine line : lines) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(line.encode());
        }
        return sb.toString();
    }

    // ---- queue / writer ----

    private void offer(LogWrite write) {
        if (!queue.offer(write)) dropped.incrementAndGet();
    }

    private void runWriter() {
        List<LogWrite> batch = new ArrayList<>(BATCH_SIZE);
        while (!stopping || !queue.isEmpty()) {
            LogWrite first;
            try {
                first = queue.poll(POLL_MILLIS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                continue;   // shutdown wakes the poll; the loop still drains what is queued
            }
            maybePrune();
            if (first == null) continue;
            batch.add(first);
            queue.drainTo(batch, BATCH_SIZE - 1);
            try {
                store.insertBatch(batch);
            } catch (Exception e) {
                logger.error("Could not write {} chest-log visit(s); dropping them", batch.size(), e);
                telemetry.error(e, "log.insert");
            } finally {
                batch.clear();
            }
            long lost = dropped.getAndSet(0);
            if (lost > 0) {
                logger.warn("Dropped {} chest-log visit(s) because the async queue was full", lost);
            }
        }
    }

    private void maybePrune() {
        long now = System.currentTimeMillis();
        if (now - lastPruneAt < pruneIntervalMillis) return;
        lastPruneAt = now;
        long cutoff = now - retentionDays * 86_400_000L;
        try {
            int removed = store.prune(cutoff, maxPerPlayer);
            if (removed > 0) {
                logger.info("Pruned {} old chest-log visit(s)", removed);
            }
        } catch (Exception e) {
            logger.warn("Could not prune the chest log: {}", e.getMessage());
            telemetry.error(e, "log.prune");
        }
    }

    /** Stops accepting visits, drains the queue, and joins the writer (≤5s). */
    public void shutdown() {
        stopping = true;
        openBaselines.clear();
        if (worker == null) return;
        worker.interrupt();
        try {
            worker.join(5_000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
