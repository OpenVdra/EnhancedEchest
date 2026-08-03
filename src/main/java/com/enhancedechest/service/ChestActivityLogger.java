package com.enhancedechest.service;

import com.enhancedechest.telemetry.Telemetry;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemContainerContents;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.slf4j.Logger;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPOutputStream;

/**
 * A bounded, batched audit pipeline designed for busy servers.
 *
 * <p>The Bukkit-thread side captures each occupied slot into immutable strings/numbers exactly once.
 * No {@link ItemStack}, {@link ItemMeta}, Bukkit registry lookup or component serializer crosses the
 * thread boundary. Diffing, grid rendering, rotation and all file I/O happen on one dedicated worker.
 * The queue is bounded so a stalled disk can never grow the heap without limit.
 *
 * <p><b>Item identity is derived in memory.</b> The diff only ever compares the OPEN and CLOSE halves
 * of the <i>same</i> cycle, so identity needs no cross-cycle, cross-server or on-disk stability — only
 * "same item content produces the same string". {@code Material} plus
 * {@link Object#hashCode() ItemMeta.hashCode()}
 * gives that without cloning the stack or serializing its data components, which is what makes the
 * capture cheap enough to sit on a region thread. Two different stacks of the same material whose
 * metadata hashes collide would be totalled as one line in a single cycle; that is an acceptable
 * trade for a human-readable log and cannot affect stored chest contents.
 *
 * <p><b>Container contents are rendered, not accounted.</b> A shulker box is still <i>one</i> entry in
 * a snapshot; what it holds is spelled out in the text of that entry only. That placement is the whole
 * reason the feature is affordable: the text is built inside {@link #buildMetaId}, which runs on a
 * {@link #META_CACHE} miss and never again for that exact shulker, whereas folding the inner items
 * into {@link Snapshot#totals()} would have to unpack every container on every capture — up to 27
 * items per occupied slot, on a region thread. The diff still sees a repacked shulker change, because
 * {@code ItemMeta.hashCode()} already covers the container component.
 */
public final class ChestActivityLogger {

    private record OpenKey(UUID actor, UUID owner, int chestIndex) {}

    /**
     * Interned, async-safe identity of one kind of item: {@code identity} groups equal stacks and
     * {@code detail} is the text written on an ADD/TAKE line.
     */
    private record ItemId(String identity, String detail) {}

    /**
     * What a chest held at one end of a visit, totalled per item rather than per slot. Slot positions
     * are deliberately not kept: nothing in the log shows them, and dropping them makes a chest that
     * was only rearranged compare equal, so it is correctly treated as an unchanged visit.
     *
     * <p>Package-private so the session manager can capture once and share it across viewers.
     */
    record Snapshot(int size, Map<String, Group> totals) {}

    private record OpenCycle(Instant openedAt, String actorName, Snapshot snapshot) {}

    private record ClosedCycle(OpenKey key, OpenCycle open, String closingActorName,
                               Snapshot closing, Instant closedAt) {}

    record Group(String identity, String detail, int amount) {}

    private record Diff(List<Group> added, List<Group> taken) {}

    /** Pure-Java stack input used by the load simulation to exercise the real async pipeline. */
    record CapturedStack(String identity, String description, int amount) {}

    /** Observable pipeline totals; package-private for the stress simulation. */
    record PipelineStats(long accepted, long written, long dropped, long unchanged, int queued,
                         long workerCpuNanos, long uncompressedBytes) {}

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss.SSS z").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter ROTATED_TIME =
            DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss-SSS").withZone(ZoneId.systemDefault());
    private static final String LINE = System.lineSeparator();

    /**
     * The file currently being written. It keeps a fixed name so an operator (or a tail command)
     * always knows where the live log is. Note it shares the {@link #ROTATED_PREFIX} of the rotated
     * files, so retention has to exclude it by name rather than by prefix.
     */
    private static final String ACTIVE_FILE_NAME = "echest-latest.log";
    /** Rotated files are {@code echest-<uuuuMMdd-HHmmss-SSS>.log}, later {@code .log.gz}. */
    private static final String ROTATED_PREFIX = "echest-";
    private static final int BATCH_SIZE = 128;
    private static final long BATCH_WAIT_MILLIS = 1_000L;

    /**
     * Cache of rendered metadata identities, keyed by material ordinal + metadata hash, so the same
     * enchanted item is described once server-wide instead of once per chest open.
     *
     * <p>Deliberately shared across threads rather than thread-confined. OPEN is captured on the
     * viewer's entity thread and CLOSE on the global thread, and the unchanged-visit check compares
     * the two snapshots; sharing the cache makes the identities in them the <i>same objects</i>, which
     * turns that comparison into reference checks instead of string equality over every occupied slot.
     * Reads are lock-free, and a miss is rare after warmup. When the cache fills it is cleared whole:
     * an OPEN/CLOSE pair straddling a clear just falls back to comparing by value, never to a wrong
     * answer.
     */
    private static final int META_CACHE_MAX = 4096;
    private static final ConcurrentHashMap<Long, ItemId> META_CACHE = new ConcurrentHashMap<>();

    /**
     * How many distinct kinds of item one container's line may list. A shulker box has 27 slots, so in
     * vanilla play this is never reached; it exists so a crafted CONTAINER component cannot turn one
     * cached detail string into an arbitrarily large one.
     */
    private static final int MAX_CONTENT_ENTRIES = 27;

    /** Same idea for the simulation's pre-captured identities; never touched in production. */
    private static final ConcurrentHashMap<String, ItemId> CAPTURED_IDS = new ConcurrentHashMap<>();

    private final Path directory;
    private final Path activeFile;
    private final Logger logger;
    private final Telemetry telemetry;
    private final long maxFileBytes;
    private final int retentionDays;
    private final ArrayBlockingQueue<ClosedCycle> queue;
    private final ConcurrentHashMap<OpenKey, OpenCycle> openCycles = new ConcurrentHashMap<>();
    private final AtomicLong droppedCycles = new AtomicLong();
    private final AtomicLong acceptedCycles = new AtomicLong();
    private final AtomicLong writtenCycles = new AtomicLong();
    private final AtomicLong totalDroppedCycles = new AtomicLong();
    private final AtomicLong unchangedCycles = new AtomicLong();
    private final AtomicLong workerCpuNanos = new AtomicLong();
    private final AtomicLong uncompressedBytes = new AtomicLong();
    private final Thread worker;

    private volatile boolean enabled;
    private volatile boolean logUnchanged;
    private volatile boolean containerContents;
    private volatile boolean chestContents;
    private volatile boolean stopping;
    private volatile long stopDeadlineNanos = Long.MAX_VALUE;

    // Worker-thread confined.
    private OutputStream output;
    private long activeFileBytes;

    public ChestActivityLogger(Path dataFolder, Logger logger, Telemetry telemetry,
                               boolean enabled, boolean logUnchanged, boolean containerContents,
                               boolean chestContents,
                               int queueCapacity, int maxFileSizeMb, int retentionDays) {
        this.directory = dataFolder.resolve("logs");
        this.activeFile = directory.resolve(ACTIVE_FILE_NAME);
        this.logger = logger;
        this.telemetry = telemetry;
        this.enabled = enabled;
        this.logUnchanged = logUnchanged;
        this.containerContents = containerContents;
        this.chestContents = chestContents;
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
        this.maxFileBytes = maxFileSizeMb * 1024L * 1024L;
        this.retentionDays = retentionDays;
        this.worker = new Thread(this::runWriter, "EnhancedEchest-activity-log");
        this.worker.setDaemon(true);
        this.worker.start();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) openCycles.clear();
    }

    /**
     * When false, a visit whose CLOSE contents are identical to its OPEN contents is discarded instead
     * of written. Most visits change nothing, and those entries otherwise bury the ones that matter.
     */
    public void setLogUnchanged(boolean logUnchanged) {
        this.logUnchanged = logUnchanged;
    }

    /**
     * When true, a shulker box's line also spells out what it holds. Flipping this drops
     * {@link #META_CACHE}, whose detail strings were rendered under the old setting — the identities in
     * a snapshot taken before the flip still compare by value, so a visit straddling a reload is
     * diffed correctly, it just loses the reference-equality shortcut for one cycle.
     */
    public void setContainerContents(boolean containerContents) {
        if (this.containerContents == containerContents) return;
        this.containerContents = containerContents;
        META_CACHE.clear();
    }

    /**
     * When true, each entry also carries a HAVE line under both headers listing what the chest held at
     * that moment. Purely a formatting choice: both snapshots are captured and queued either way, so
     * this costs nothing on a Bukkit thread and everything on disk.
     */
    public void setChestContents(boolean chestContents) {
        this.chestContents = chestContents;
    }

    /**
     * Whether a capture is worth paying for. Callers that would have to build a snapshot themselves
     * check this first so a disabled logger costs them nothing.
     */
    boolean isRecording() {
        return enabled && !stopping;
    }

    /**
     * Whether the closing contents of a chest are worth capturing. A chest no viewer clicked or dragged
     * in cannot have changed, so unless the operator asked for every visit there is nothing to compare
     * and the whole capture is skipped. This is the cheap pre-filter; {@link #isUnchanged} is the exact
     * check that still runs for chests that <i>were</i> touched but ended up identical anyway.
     */
    boolean needsCapture(boolean chestTouched) {
        return isRecording() && (logUnchanged || chestTouched);
    }

    /**
     * Drops a visit's baseline without capturing or writing anything, for a chest {@link #needsCapture}
     * ruled out. Must be called whenever a capture is skipped, or the OPEN cycle would linger until the
     * next visit by the same player to the same chest overwrote it.
     */
    void abandon(UUID actor, UUID owner, int chestIndex) {
        if (!isRecording()) return;   // nothing was ever opened, so there is no baseline to drop
        if (openCycles.remove(new OpenKey(actor, owner, chestIndex)) != null) {
            unchangedCycles.incrementAndGet();
        }
    }

    /** Captures OPEN on the Bukkit-owned thread. */
    public void opened(String actorName, UUID actor, UUID owner, int chestIndex, ItemStack[] contents) {
        if (!isRecording()) return;
        openCycles.put(new OpenKey(actor, owner, chestIndex),
                new OpenCycle(Instant.now(), actorName, capture(contents)));
    }

    /**
     * Captures CLOSE on the Bukkit-owned thread and offers immutable work to the bounded async queue.
     * Under extreme sustained disk failure the newest cycle is dropped instead of blocking a server
     * tick or allowing an unbounded queue to exhaust heap; the worker writes an explicit SYSTEM line
     * with the number dropped as soon as it can make progress again.
     */
    public void closed(String actorName, UUID actor, UUID owner, int chestIndex, ItemStack[] contents) {
        if (!isRecording()) return;
        OpenKey key = new OpenKey(actor, owner, chestIndex);
        if (!openCycles.containsKey(key)) return;   // no baseline: nothing to capture against
        if (queue.remainingCapacity() == 0) {
            // Skip the CLOSE capture entirely when backpressure is already certain, but still consume
            // the OPEN cycle so a stalled disk cannot leave stale baselines behind.
            openCycles.remove(key);
            markDropped(1);
            return;
        }
        closed(actorName, actor, owner, chestIndex, capture(contents));
    }

    /**
     * Same as {@link #closed(String, UUID, UUID, int, ItemStack[])} but for a snapshot the caller
     * already captured. Force-close and shutdown tear down one shared inventory with several viewers
     * attached; capturing once and passing it here keeps that O(1) in the chest size instead of O(viewers).
     */
    void closed(String actorName, UUID actor, UUID owner, int chestIndex, Snapshot closing) {
        if (!isRecording()) return;
        OpenKey key = new OpenKey(actor, owner, chestIndex);
        OpenCycle cycle = openCycles.remove(key);
        if (cycle == null) return;
        if (isUnchanged(cycle.snapshot(), closing)) return;
        offer(new ClosedCycle(key, cycle, actorName, closing, Instant.now()));
    }

    /**
     * Whether this visit should be thrown away because nothing about the chest moved. Identities are
     * interned per capture thread, so when OPEN and CLOSE were captured on the same thread this is a
     * walk of reference comparisons. Snapshots hold per-item totals, so a chest that was only
     * rearranged compares equal and its visit is correctly dropped as having changed nothing.
     */
    private boolean isUnchanged(Snapshot open, Snapshot closing) {
        if (logUnchanged || !open.equals(closing)) return false;
        unchangedCycles.incrementAndGet();
        return true;
    }

    /**
     * Feeds already-captured immutable snapshots through the exact production queue/diff/format/I/O
     * path. This deliberately bypasses Bukkit objects so the 300-500 player simulation can run under
     * plain JUnit; production calls continue to use {@link #opened} and {@link #closed}.
     */
    void recordCapturedCycle(String actorName, UUID actor, UUID owner, int chestIndex, int size,
                             List<CapturedStack> opened, List<CapturedStack> closed) {
        if (!isRecording()) return;
        if (queue.remainingCapacity() == 0) {
            markDropped(1);
            return;
        }
        Instant now = Instant.now();
        OpenKey key = new OpenKey(actor, owner, chestIndex);
        OpenCycle open = new OpenCycle(now, actorName, capturedSnapshot(size, opened));
        Snapshot closing = capturedSnapshot(size, closed);
        if (isUnchanged(open.snapshot(), closing)) return;
        offer(new ClosedCycle(key, open, actorName, closing, now));
    }

    /**
     * The {@link #opened} twin of {@link #recordCapturedCycle}: takes a snapshot the caller already
     * captured, so the server-less leak simulation can drive real OPEN/CLOSE <i>lifecycles</i> —
     * and therefore {@link #openCycles} — rather than only the queue behind them.
     */
    void openCaptured(String actorName, UUID actor, UUID owner, int chestIndex, int size,
                      List<CapturedStack> contents) {
        if (!isRecording()) return;
        openCycles.put(new OpenKey(actor, owner, chestIndex),
                new OpenCycle(Instant.now(), actorName, capturedSnapshot(size, contents)));
    }

    /** The {@link #closed} twin of {@link #openCaptured}. */
    void closeCaptured(String actorName, UUID actor, UUID owner, int chestIndex, int size,
                       List<CapturedStack> contents) {
        closed(actorName, actor, owner, chestIndex, capturedSnapshot(size, contents));
    }

    /**
     * Visits opened but not yet closed. Bounded by the number of chests actually open right now, so a
     * close path that ever failed to consume its baseline would show up here as unbounded growth.
     */
    int openCycleCount() {
        return openCycles.size();
    }

    /** Size and hard ceiling of the shared identity caches, for the leak simulation's bound check. */
    static int identityCacheSize() {
        return META_CACHE.size() + CAPTURED_IDS.size();
    }

    static int identityCacheLimit() {
        return META_CACHE_MAX;
    }

    PipelineStats pipelineStats() {
        return new PipelineStats(acceptedCycles.get(), writtenCycles.get(), totalDroppedCycles.get(),
                unchangedCycles.get(), queue.size(), workerCpuNanos.get(), uncompressedBytes.get());
    }

    private static Snapshot capturedSnapshot(int size, List<CapturedStack> stacks) {
        Map<String, Group> totals = new LinkedHashMap<>(64);
        // Interned in a shared map exactly like the production cache, so the simulation measures the
        // queue/diff/format/IO pipeline and the unchanged-visit check as they actually behave.
        for (CapturedStack stack : stacks) {
            ItemId id = CAPTURED_IDS.get(stack.identity());
            if (id == null) {
                id = new ItemId(stack.identity(), stack.description());
                if (CAPTURED_IDS.size() >= META_CACHE_MAX) CAPTURED_IDS.clear();
                CAPTURED_IDS.put(stack.identity(), id);
            }
            add(totals, id, stack.amount());
        }
        return new Snapshot(size, totals);
    }

    private void offer(ClosedCycle cycle) {
        if (queue.offer(cycle)) {
            acceptedCycles.incrementAndGet();
        } else {
            markDropped(1);
        }
    }

    private void markDropped(long count) {
        droppedCycles.addAndGet(count);
        totalDroppedCycles.addAndGet(count);
    }

    // ---- capture (Bukkit-owned thread) ----

    /**
     * Converts Bukkit objects to immutable worker-safe values. Plain vanilla stacks resolve to a
     * cached per-material identity; a metadata-bearing stack costs one {@link ItemStack#getItemMeta()}
     * and a cache lookup, with the description built only the first time that exact item is seen.
     */
    Snapshot capture(ItemStack[] contents) {
        // Sized for the distinct-item count a full chest realistically holds, so accumulating the
        // totals never rehashes on the calling thread.
        Map<String, Group> totals = new LinkedHashMap<>(64);
        for (ItemStack item : contents) {
            if (isEmpty(item)) continue;
            ItemId id = item.hasItemMeta() ? metaId(item) : MaterialTables.plain(item.getType());
            add(totals, id, item.getAmount());
        }
        return new Snapshot(contents.length, totals);
    }

    /** Adds one stack into the running per-item totals of a snapshot. */
    private static void add(Map<String, Group> totals, ItemId id, int amount) {
        // merge() reaches the bucket once; get-then-put would hash the same key twice.
        totals.merge(id.identity(), new Group(id.identity(), id.detail(), amount),
                (running, incoming) -> new Group(running.identity(), running.detail(),
                        running.amount() + incoming.amount()));
    }

    private ItemId metaId(ItemStack item) {
        Material type = item.getType();
        ItemMeta meta = item.getItemMeta();      // the only Bukkit allocation on this path
        if (meta == null) return MaterialTables.plain(type);
        int hash = meta.hashCode();
        long cacheKey = ((long) type.ordinal() << 32) | (hash & 0xFFFFFFFFL);
        ItemId cached = META_CACHE.get(cacheKey);
        if (cached != null) return cached;
        ItemId built = buildMetaId(item, type, meta, hash);
        if (META_CACHE.size() >= META_CACHE_MAX) META_CACHE.clear();
        META_CACHE.put(cacheKey, built);
        return built;
    }

    /** Called once per distinct metadata-bearing item per capture thread, then cached. */
    private ItemId buildMetaId(ItemStack item, Material type, ItemMeta meta, int hash) {
        String key = MaterialTables.key(type);
        String fingerprint = Integer.toHexString(hash);
        List<String> details = new ArrayList<>(4);
        if (meta.hasCustomName()) {
            details.add("name=\"" + escape(PlainTextComponentSerializer.plainText()
                    .serialize(meta.customName())) + "\"");
        }
        if (meta instanceof Damageable damageable && damageable.hasDamage()) {
            details.add("damage=" + damageable.getDamage());
        }
        if (meta.hasEnchants()) {
            List<String> enchantments = meta.getEnchants().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(Comparator.comparing(e -> e.getKey().toString())))
                    .map(e -> e.getKey().getKey() + ":" + e.getValue())
                    .toList();
            details.add("enchants=" + enchantments);
        }
        if (meta.hasCustomModelData()) {
            details.add("custom-model=" + meta.getCustomModelData());
        }
        // Covers all other custom components/NBT without dumping their potentially huge payload.
        details.add("meta=" + fingerprint);
        if (containerContents) {
            String packed = describeContents(item);
            if (packed != null) details.add("contents=[" + packed + "]");
        }
        return new ItemId("meta:" + key + ":" + fingerprint,
                key + "{" + String.join(",", details) + "}");
    }

    /**
     * Spells out what a shulker box holds, as the same {@code item xN} list the ADD/TAKE lines use.
     * Reached only from {@link #buildMetaId}, i.e. once per distinct shulker <i>and its exact
     * contents</i> — the outer hash covers the container component, so a repacked shulker is a
     * different cache entry and gets described again, and an untouched one never is.
     *
     * <p><b>One level deep, deliberately.</b> An item inside is described by material plus, at most,
     * its custom name; its own container is never opened. Vanilla cannot nest shulkers, so the depth
     * costs nothing real, and refusing to recurse is what stops a crafted item from turning a single
     * capture into an unbounded walk. Inner identities are not interned either: they exist only long
     * enough to build this string, and caching them would spend the shared cache on items no diff
     * ever compares.
     *
     * @return the rendered list, or {@code null} when the item carries no container or it is empty
     */
    @SuppressWarnings("UnstableApiUsage")
    private String describeContents(ItemStack item) {
        ItemContainerContents container;
        try {
            container = item.getData(DataComponentTypes.CONTAINER);
        } catch (RuntimeException e) {
            // A malformed component must cost the log one detail string, never a chest close.
            telemetry.error(e, "activity-log.container-contents");
            return null;
        }
        if (container == null) return null;

        Map<String, Group> totals = new LinkedHashMap<>(32);
        int hidden = 0;
        for (ItemStack inner : container.contents()) {
            if (isEmpty(inner)) continue;
            ItemId id = innerId(inner);
            if (totals.size() >= MAX_CONTENT_ENTRIES && !totals.containsKey(id.identity())) {
                hidden++;
                continue;
            }
            add(totals, id, inner.getAmount());
        }
        if (totals.isEmpty()) return null;

        List<Group> groups = new ArrayList<>(totals.values());
        groups.sort(Comparator.comparing(Group::detail));
        String rendered = formatGroups(groups);
        return hidden == 0 ? rendered : rendered + ", +" + hidden + " more";
    }

    /** Shallow identity of an item packed inside a container: never expanded, never cached. */
    private static ItemId innerId(ItemStack inner) {
        Material type = inner.getType();
        if (!inner.hasItemMeta()) return MaterialTables.plain(type);
        ItemMeta meta = inner.getItemMeta();
        if (meta == null) return MaterialTables.plain(type);
        String key = MaterialTables.key(type);
        String fingerprint = Integer.toHexString(meta.hashCode());
        String detail = meta.hasCustomName()
                ? key + "{name=\"" + escape(PlainTextComponentSerializer.plainText()
                        .serialize(meta.customName())) + "\"}"
                : key + "{meta=" + fingerprint + "}";
        return new ItemId("inner:" + key + ":" + fingerprint, detail);
    }

    /**
     * Lazily filled per-material tables. Loading is deferred to the first {@link #capture} so a
     * server-less unit test driving {@link #recordCapturedCycle} never touches the Bukkit registry.
     */
    private static final class MaterialTables {
        private static final String[] KEYS = new String[Material.values().length];
        private static final ItemId[] PLAIN = new ItemId[KEYS.length];

        // Both tables are idempotent, so an unsynchronized race just recomputes the same value;
        // records and Strings have final fields, so what a racing reader sees is fully initialized.
        static String key(Material material) {
            int index = material.ordinal();
            String key = KEYS[index];
            if (key == null) {
                key = material.getKey().toString();
                KEYS[index] = key;
            }
            return key;
        }

        static ItemId plain(Material material) {
            int index = material.ordinal();
            ItemId id = PLAIN[index];
            if (id == null) {
                String key = key(material);
                id = new ItemId("plain:" + key, key);
                PLAIN[index] = id;
            }
            return id;
        }
    }

    // ---- writer thread ----

    private void runWriter() {
        List<ClosedCycle> batch = new ArrayList<>(BATCH_SIZE);
        pruneOldLogs();   // startup sweep; rotation sweeps again as files are replaced
        ThreadMXBean threadMx = ManagementFactory.getThreadMXBean();
        long cpuStart = threadMx.isCurrentThreadCpuTimeSupported()
                ? threadMx.getCurrentThreadCpuTime() : -1L;
        // Held across iterations so a cycle polled while the disk is down is retried, not lost.
        ClosedCycle pending = null;
        try {
            while ((!stopping || !queue.isEmpty())
                    && !(stopping && System.nanoTime() >= stopDeadlineNanos)) {
                if (pending == null) {
                    try {
                        pending = queue.poll(BATCH_WAIT_MILLIS, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        continue; // shutdown wakes the poll; the loop still drains everything queued
                    }
                }

                // Nothing to write: never touch the disk. This is what keeps a disabled logger from
                // creating a logs/ folder and an empty file on a server that never asked for one.
                if (pending == null && droppedCycles.get() == 0) {
                    if (output != null) {
                        try {
                            flushOutput();
                        } catch (IOException e) {
                            handleIoFailure("flush", e);
                        }
                    }
                    continue;
                }
                if (!ensureOutput()) {
                    waitAfterIoFailure();
                    continue; // 'pending' is kept, so nothing is consumed until the disk is writable
                }

                try {
                    writeDroppedNotice();
                } catch (IOException e) {
                    handleIoFailure("write overflow notice", e);
                    continue;
                }
                if (pending == null) {
                    continue; // the overflow notice was the only work
                }

                batch.add(pending);
                pending = null;
                queue.drainTo(batch, BATCH_SIZE - 1);
                int written = 0;
                try {
                    for (ClosedCycle cycle : batch) {
                        writeSection(formatSection(cycle));
                        written++;
                    }
                    flushOutput(); // one flush for up to 128 complete sessions
                } catch (IOException e) {
                    // A section may have partially reached the OS buffer; retrying it could forge a
                    // duplicate audit record. Count this and all remaining batch entries as dropped,
                    // then recover the writer and explicitly report the loss once disk I/O resumes.
                    markDropped(batch.size() - written);
                    handleIoFailure("write batch", e);
                } finally {
                    batch.clear();
                }
                // Only sections followed by a successful batch flush are counted as durable; on the
                // failure path 'written' may be non-zero but handleIoFailure has closed the output.
                if (written > 0 && output != null) writtenCycles.addAndGet(written);
            }
            int abandoned = queue.size() + (pending != null ? 1 : 0);
            if (abandoned > 0) {
                queue.clear();
                markDropped(abandoned);
            }
            // Only worth opening the file to report a loss; a logger that never wrote stays silent.
            if (droppedCycles.get() > 0 && ensureOutput()) {
                writeDroppedNotice();
                flushOutput();
            }
        } catch (Throwable e) {
            logger.error("Chest activity log worker stopped unexpectedly", e);
            telemetry.error(e, "activity-log.worker");
        } finally {
            if (cpuStart >= 0) {
                workerCpuNanos.set(Math.max(0L, threadMx.getCurrentThreadCpuTime() - cpuStart));
            }
            closeOutput();
        }
    }

    private void prepareOutput() throws IOException {
        Files.createDirectories(directory);
        activeFileBytes = Files.exists(activeFile) ? Files.size(activeFile) : 0L;
        output = openActiveFile();
    }

    private OutputStream openActiveFile() throws IOException {
        // Bytes, not a Writer: a section is encoded to UTF-8 once and the same array is used for
        // both the rotation accounting and the write.
        return new BufferedOutputStream(Files.newOutputStream(activeFile,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND), 64 * 1024);
    }

    private boolean ensureOutput() {
        if (output != null) return true;
        try {
            prepareOutput();
            return true;
        } catch (IOException e) {
            logger.error("Could not open chest activity log {}; will retry", activeFile, e);
            telemetry.error(e, "activity-log.open");
            return false;
        }
    }

    private void handleIoFailure(String operation, IOException e) {
        logger.error("Could not {} for chest activity log {}; will retry", operation, activeFile, e);
        // 'operation' is one of three compile-time constants, so it groups reports without ever
        // carrying player data into the label.
        telemetry.error(e, "activity-log." + operation.replace(' ', '-'));
        closeOutput();
    }

    private void waitAfterIoFailure() {
        try {
            Thread.sleep(1_000L);
        } catch (InterruptedException ignored) {
            // shutdown uses interrupt only to wake this worker; the outer condition decides when to exit
        }
    }

    private void writeSection(String section) throws IOException {
        byte[] encoded = section.getBytes(StandardCharsets.UTF_8);
        if (activeFileBytes > 0 && activeFileBytes + encoded.length > maxFileBytes) {
            rotate();
        }
        output.write(encoded);
        activeFileBytes += encoded.length;
        uncompressedBytes.addAndGet(encoded.length);
    }

    private void writeDroppedNotice() throws IOException {
        long dropped = droppedCycles.getAndSet(0L);
        if (dropped == 0) return;
        String warning = "[" + TIME.format(Instant.now()) + "] SYSTEM dropped=" + dropped
                + " chest activity cycle(s): async log queue was full" + LINE + LINE;
        try {
            writeSection(warning);
        } catch (IOException e) {
            droppedCycles.addAndGet(dropped);
            throw e;
        }
        logger.warn("Dropped {} chest activity log cycle(s) because the bounded queue was full", dropped);
    }

    private void rotate() throws IOException {
        closeOutput();
        Path rotated = directory.resolve(ROTATED_PREFIX + ROTATED_TIME.format(Instant.now()) + ".log");
        try {
            Files.move(activeFile, rotated, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            activeFileBytes = Files.exists(activeFile) ? Files.size(activeFile) : 0L;
            output = openActiveFile();
        }
        compressRotated(rotated);
        pruneOldLogs();
    }

    /** Compresses a completed rotation on the worker; a failure keeps the readable .log intact. */
    private void compressRotated(Path rotated) {
        Path compressed = rotated.resolveSibling(rotated.getFileName() + ".gz");
        Path temporary = rotated.resolveSibling(rotated.getFileName() + ".gz.tmp");
        try (InputStream in = Files.newInputStream(rotated);
             OutputStream raw = Files.newOutputStream(temporary, StandardOpenOption.CREATE,
                     StandardOpenOption.TRUNCATE_EXISTING);
             GZIPOutputStream gzip = new GZIPOutputStream(raw, 64 * 1024)) {
            in.transferTo(gzip);
        } catch (IOException e) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // The original .log remains intact; a stale .tmp is harmless and never pruned as a log.
            }
            logger.warn("Could not compress rotated chest activity log {}: {}",
                    rotated.getFileName(), e.getMessage());
            telemetry.error(e, "activity-log.compress");
            return;
        }
        try {
            Files.move(temporary, compressed, StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(rotated);
        } catch (IOException e) {
            logger.warn("Could not finalize compressed chest activity log {}: {}",
                    rotated.getFileName(), e.getMessage());
            telemetry.error(e, "activity-log.compress-finalize");
        }
    }

    private void pruneOldLogs() {
        // Never creates the folder: retention runs at startup, when the log may be switched off and
        // there is nothing on disk to sweep.
        if (!Files.isDirectory(directory)) return;
        Instant cutoff = Instant.now().minusSeconds(retentionDays * 86_400L);
        try (var files = Files.list(directory)) {
            files.filter(path -> {
                        String name = path.getFileName().toString();
                        // The active file carries the same prefix, so exclude it by name: it is the
                        // one log that must never be pruned no matter how long the server has run.
                        return !name.equals(ACTIVE_FILE_NAME)
                                && name.startsWith(ROTATED_PREFIX)
                                && (name.endsWith(".log") || name.endsWith(".log.gz"));
                    })
                    .filter(path -> {
                        try {
                            return Files.getLastModifiedTime(path).toInstant().isBefore(cutoff);
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            logger.warn("Could not prune old chest activity log {}: {}",
                                    path.getFileName(), e.getMessage());
                        }
                    });
        } catch (IOException e) {
            logger.warn("Could not scan chest activity logs for retention cleanup: {}", e.getMessage());
            telemetry.error(e, "activity-log.prune");
        }
    }

    // ---- formatting ----

    /**
     * Renders one visit: when it was opened, what was added and taken while it was open, and when it
     * was closed. Item positions are never written, only totals, so an entry stays a handful of lines
     * however full the chest was — long ones, when {@code chestContents} adds the HAVE lines.
     */
    private String formatSection(ClosedCycle cycle) {
        OpenKey key = cycle.key();
        OpenCycle open = cycle.open();
        String actorName = open.actorName() == null || open.actorName().isBlank()
                ? cycle.closingActorName() : open.actorName();
        String access = key.actor().equals(key.owner())
                ? "" : " access=ADMIN_ACCESS owner=" + key.owner();

        boolean withContents = chestContents;   // read once: a reload must not split one entry
        StringBuilder out = new StringBuilder(withContents ? 1024 : 256);
        appendHeader(out, "OPEN", open.openedAt(), actorName, key, open.snapshot().size(), access);
        if (withContents) appendContents(out, open.snapshot());

        Diff diff = diff(open.snapshot(), cycle.closing());
        if (!diff.added().isEmpty()) {
            out.append("  ADD   ").append(formatGroups(diff.added())).append(LINE);
        }
        if (!diff.taken().isEmpty()) {
            out.append("  TAKE  ").append(formatGroups(diff.taken())).append(LINE);
        }

        appendHeader(out, "CLOSE", cycle.closedAt(), actorName, key, cycle.closing().size(), access);
        if (withContents) appendContents(out, cycle.closing());
        return out.append(LINE).toString();
    }

    /**
     * The HAVE line: everything the chest held at one end of the visit, on one line however long.
     * Kept to a single line on purpose, so grepping for an item still returns its whole context.
     *
     * <p><b>Deliberately unsorted</b>, unlike ADD/TAKE. {@link #capture} walks the slots in order into
     * a {@link LinkedHashMap} and {@code merge} does not reorder an existing key, so iterating the
     * totals reproduces the order the items appear in the chest — first-appearance order, since equal
     * items from several slots are totalled into the entry where the first of them sat. That reads
     * like the chest itself, which is what this line is for; ADD/TAKE stay alphabetical because they
     * are scanned for one item and compared between entries.
     */
    private static void appendContents(StringBuilder out, Snapshot snapshot) {
        Map<String, Group> totals = snapshot.totals();
        out.append("  HAVE  ")
                .append(totals.isEmpty() ? "(empty)" : formatGroups(new ArrayList<>(totals.values())))
                .append(LINE);
    }

    private static void appendHeader(StringBuilder out, String event, Instant at, String actorName,
                                     OpenKey key, int size, String access) {
        out.append('[').append(TIME.format(at)).append("] ").append(event)
                .append(" player=").append(safe(actorName))
                .append(" uuid=").append(key.actor())
                .append(" chest=").append(key.chestIndex())
                .append(" size=").append(size)
                .append(access)
                .append(LINE);
    }

    /** O(n) diff of the two ends of a visit, using the totals each capture already produced. */
    private static Diff diff(Snapshot before, Snapshot after) {
        Map<String, Group> oldTotals = before.totals();
        Map<String, Group> newTotals = after.totals();
        Map<String, Group> identities = new LinkedHashMap<>(oldTotals);
        newTotals.forEach(identities::putIfAbsent);
        List<Group> added = new ArrayList<>();
        List<Group> taken = new ArrayList<>();

        for (Group identity : identities.values()) {
            int delta = amountOf(newTotals, identity.identity()) - amountOf(oldTotals, identity.identity());
            Group display = newTotals.getOrDefault(identity.identity(), identity);
            if (delta > 0) added.add(new Group(display.identity(), display.detail(), delta));
            if (delta < 0) taken.add(new Group(display.identity(), display.detail(), -delta));
        }
        Comparator<Group> byDetail = Comparator.comparing(Group::detail);
        added.sort(byDetail);
        taken.sort(byDetail);
        return new Diff(added, taken);
    }

    private static int amountOf(Map<String, Group> groups, String identity) {
        Group group = groups.get(identity);
        return group == null ? 0 : group.amount();
    }

    private static String formatGroups(List<Group> groups) {
        List<String> values = new ArrayList<>(groups.size());
        for (Group group : groups) values.add(group.detail() + " x" + group.amount());
        return String.join(", ", values);
    }

    private void flushOutput() throws IOException {
        if (output != null) output.flush();
    }

    private void closeOutput() {
        if (output == null) return;
        try {
            output.close();
        } catch (IOException e) {
            logger.warn("Could not close chest activity log {}: {}", activeFile, e.getMessage());
            telemetry.error(e, "activity-log.close");
        } finally {
            output = null;
        }
    }

    private static String safe(String value) {
        if (value == null || value.isBlank()) return "<unknown>";
        return value.replace('\r', '_').replace('\n', '_').replace(' ', '_');
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n");
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    /** Stops accepting new cycles, drains the bounded queue, flushes and closes the active log. */
    public void shutdown() {
        stopping = true;
        stopDeadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        openCycles.clear();
        worker.interrupt();
        try {
            worker.join(11_000L);
            if (worker.isAlive()) {
                logger.warn("Timed out flushing chest activity log {}", activeFile);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Interrupted while flushing chest activity log {}", activeFile);
        }
    }
}
