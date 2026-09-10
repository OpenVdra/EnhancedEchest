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
 * Captures OPEN/CLOSE chest events and persists each as one row (snapshot + change summary) in the
 * separate {@link ChestLogStore} SQLite database, for the in-game {@code /ee log} viewer. Replaces the
 * old plain-text audit logger.
 *
 * <h2>Where the work runs</h2>
 * The Bukkit-owned thread does only the minimum: encode the contents to bytes once
 * ({@link ContainerCodec}, the same immutable format chests are stored in) and total them by material.
 * Both results are immutable, so they cross to the single writer thread with no Bukkit object and no
 * further main-thread work. The writer thread batches inserts into one transaction, and prunes on a
 * timer. The queue is bounded, so a stalled disk drops the newest events instead of growing the heap.
 *
 * <h2>What a visit produces</h2>
 * Every OPEN writes a row (its snapshot is the "before" the {@code /ee log} pane can show). A CLOSE
 * writes a row only when the visit is worth recording — the caller gates that through
 * {@link #needsCapture(boolean)}: an untouched peek is dropped (leaving just the OPEN pane) unless
 * {@code log-unchanged} is on. The CLOSE row carries a material-level diff against its matching OPEN,
 * computed here from the two totals — the exact truth is always the two snapshots themselves.
 */
public final class ChestLogService {

    /** Interned per-material namespaced key, so repeated captures don't re-allocate the same string. */
    private static final String[] MATERIAL_KEYS = new String[Material.values().length];

    /** Identity of one open visit: the same (actor, owner, index) the CLOSE will diff against. */
    private record OpenKey(UUID actor, UUID owner, int index) {}

    /** An encoded, thread-safe capture of a chest at one instant: bytes to store, totals to diff. */
    public record Capture(byte[] blob, Map<String, Integer> counts, int size) {}

    private static final int BATCH_SIZE = 128;
    private static final long POLL_MILLIS = 1_000L;

    private final ContainerCodec codec;
    private final Logger logger;
    private final Telemetry telemetry;
    private final ChestLogStore store;
    private final boolean storeReady;

    private final ArrayBlockingQueue<LogWrite> queue;
    private final ConcurrentHashMap<OpenKey, Map<String, Integer>> openBaselines = new ConcurrentHashMap<>();
    private final AtomicLong dropped = new AtomicLong();
    private final Thread worker;

    private volatile boolean enabled;
    private volatile boolean logUnchanged;
    private volatile int retentionDays;
    private volatile int maxPerPlayer;
    private volatile long pruneIntervalMillis;
    private volatile boolean stopping;
    private long lastPruneAt;   // worker-thread confined

    public ChestLogService(ContainerCodec codec, Logger logger, Telemetry telemetry, ChestLogStore store,
                           boolean storeReady, boolean enabled, boolean logUnchanged,
                           int queueCapacity, int retentionDays, int maxPerPlayer,
                           long pruneIntervalMillis) {
        this.codec = codec;
        this.logger = logger;
        this.telemetry = telemetry;
        this.store = store;
        this.storeReady = storeReady;
        this.enabled = enabled;
        this.logUnchanged = logUnchanged;
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

    public void setLogUnchanged(boolean logUnchanged) {
        this.logUnchanged = logUnchanged;
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
     * unless {@code log-unchanged} is on there is nothing to record on close (the OPEN row already
     * stands). Mirrors the old logger's pre-filter so the session manager's call sites stay unchanged.
     */
    public boolean needsCapture(boolean chestTouched) {
        return isRecording() && (logUnchanged || chestTouched);
    }

    // ---- capture (Bukkit-owned thread) ----

    /**
     * Encodes and totals a chest's contents once, off which both an OPEN and a CLOSE row are built.
     * Returns {@code null} if encoding fails, so the caller simply skips logging that event rather than
     * aborting the chest operation.
     */
    public @Nullable Capture capture(ItemStack[] contents) {
        byte[] blob;
        try {
            blob = codec.encode(contents);
        } catch (Exception e) {
            logger.warn("Could not encode chest contents for the activity log; skipping this event");
            telemetry.error(e, "log.encode");
            return null;
        }
        return new Capture(blob, countMaterials(contents), contents.length);
    }

    /** Records an OPEN on the viewer's thread: writes the row and keeps its totals as the close baseline. */
    public void opened(@Nullable String actorName, UUID actor, UUID owner, int index, ItemStack[] contents) {
        if (!isRecording()) return;
        Capture cap = capture(contents);
        if (cap == null) return;
        openBaselines.put(new OpenKey(actor, owner, index), cap.counts());
        offer(new LogWrite(owner, index, actor, actorName, cap.size(), LogAction.OPEN,
                System.currentTimeMillis(), null, cap.blob()));
    }

    /** Records a CLOSE from live contents (captures, then delegates to the shared-capture overload). */
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
     * Records a CLOSE from an already-taken capture. Force-close and shutdown tear down one shared
     * inventory with several viewers; capturing once and passing it here keeps that O(1) in chest size
     * rather than O(viewers).
     */
    public void closed(@Nullable String actorName, UUID actor, UUID owner, int index, Capture cap) {
        if (!isRecording()) return;
        Map<String, Integer> baseline = openBaselines.remove(new OpenKey(actor, owner, index));
        offer(new LogWrite(owner, index, actor, actorName, cap.size(), LogAction.CLOSE,
                System.currentTimeMillis(), buildDiff(baseline, cap.counts()), cap.blob()));
    }

    /** Drops an open visit's baseline without writing a CLOSE, for a peek not worth recording. */
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
     * Builds the CLOSE change summary as {@code "<delta> <key>"} lines: additions (positive) first, then
     * removals, each group ordered by key. Returns {@code null} when nothing changed, so the row stores
     * no diff text.
     */
    private static @Nullable String buildDiff(@Nullable Map<String, Integer> open, Map<String, Integer> close) {
        Map<String, Integer> before = open != null ? open : Map.of();
        Map<String, Integer> keys = new HashMap<>(before);
        close.forEach((k, v) -> keys.putIfAbsent(k, 0));
        List<DiffLine> lines = new ArrayList<>();
        for (String key : keys.keySet()) {
            int delta = close.getOrDefault(key, 0) - before.getOrDefault(key, 0);
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
                logger.error("Could not write {} chest-log event(s); dropping them", batch.size(), e);
                telemetry.error(e, "log.insert");
            } finally {
                batch.clear();
            }
            long lost = dropped.getAndSet(0);
            if (lost > 0) {
                logger.warn("Dropped {} chest-log event(s) because the async queue was full", lost);
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
                logger.info("Pruned {} old chest-log event(s)", removed);
            }
        } catch (Exception e) {
            logger.warn("Could not prune the chest log: {}", e.getMessage());
            telemetry.error(e, "log.prune");
        }
    }

    /** Stops accepting events, drains the queue, and joins the writer (≤5s). */
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
