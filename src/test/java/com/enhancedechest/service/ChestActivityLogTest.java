package com.enhancedechest.service;

import com.enhancedechest.telemetry.Telemetry;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the activity log puts on disk and what it takes back off again. The load behaviour of the same
 * pipeline is covered separately by {@link ChestActivityLogSimulationTest} ({@code ./gradlew stressTest}).
 *
 * <ul>
 *   <li><b>Format</b> — an OPEN header, the totals added and taken while the chest was open, and a
 *       CLOSE header. Chest layout is never written, so an entry stays a handful of lines however full
 *       the chest was.</li>
 *   <li><b>Retention</b> — rotated {@code echest-*.log(.gz)} files are deleted past their age, and
 *       {@code echest-latest.log} never is.</li>
 * </ul>
 */
class ChestActivityLogTest {

    private static final int CHEST_SIZE = 54;
    private static final int RETENTION_DAYS = 14;
    private static final String SWORD = "minecraft:diamond_sword"
            + "{name=\"Excalibur\",enchants=[sharpness:5],meta=1f3a2b0c}";

    // ---- format ----

    @Test
    void writesHeadersAndTotalsOnly() throws Exception {
        Path dir = Files.createTempDirectory("echest-activity-format");
        UUID actor = UUID.randomUUID();
        UUID owner = UUID.randomUUID();

        ChestActivityLogger logger = newLogger(dir, "format", true);
        logger.recordCapturedCycle("Steve", actor, owner, 2, CHEST_SIZE, opened(), closed());
        logger.shutdown();

        String log = readLog(dir);
        System.out.println(log);
        deleteTree(dir);

        List<String> lines = log.lines().filter(l -> !l.isBlank()).toList();
        assertEquals(4, lines.size(), "expected OPEN, ADD, TAKE, CLOSE and nothing else: " + lines);
        assertTrue(lines.get(0).contains("] OPEN "));
        assertEquals("  ADD   minecraft:redstone x24", lines.get(1));
        assertEquals("  TAKE  minecraft:stone x32", lines.get(2));
        assertTrue(lines.get(3).contains("] CLOSE "));

        // Admin access: actor != owner, so both headers name the owner.
        assertEquals(2, lines.stream().filter(l -> l.contains("access=ADMIN_ACCESS owner=" + owner))
                .count(), "admin access must be marked on both headers");
        assertTrue(lines.get(0).contains("chest=2"), "chest number missing");
        assertTrue(lines.get(0).contains("size=" + CHEST_SIZE), "chest size missing");

        assertFalse(log.contains("| "), "no chest grid may be written");
        assertFalse(log.contains("items *"), "no legend may be written");
    }

    /** A metadata item's full description is written on its ADD/TAKE line, not behind a marker. */
    @Test
    void spellsOutMetadataItemsOnTheirTotalLine() throws Exception {
        Path dir = Files.createTempDirectory("echest-activity-meta");
        UUID player = UUID.randomUUID();

        ChestActivityLogger logger = newLogger(dir, "meta", true);
        logger.recordCapturedCycle("Alex", player, player, 1, CHEST_SIZE,
                List.of(stack("meta:sword:1f3a2b0c", SWORD, 1)), List.of());
        logger.shutdown();

        String log = readLog(dir);
        deleteTree(dir);

        assertTrue(log.contains("  TAKE  " + SWORD + " x1"), "metadata item not spelled out: " + log);
    }

    /** Identical stacks spread over several slots are one total, not one entry per slot. */
    @Test
    void totalsIdenticalStacksAcrossSlots() throws Exception {
        Path dir = Files.createTempDirectory("echest-activity-totals");
        UUID player = UUID.randomUUID();

        List<ChestActivityLogger.CapturedStack> three = List.of(
                stack("plain:minecraft:stone", "minecraft:stone", 64),
                stack("plain:minecraft:stone", "minecraft:stone", 64),
                stack("plain:minecraft:stone", "minecraft:stone", 32));

        ChestActivityLogger logger = newLogger(dir, "totals", true);
        logger.recordCapturedCycle("Alex", player, player, 1, CHEST_SIZE, three, List.of());
        logger.shutdown();

        String log = readLog(dir);
        deleteTree(dir);

        assertTrue(log.contains("  TAKE  minecraft:stone x160"), "stacks not totalled: " + log);
        assertEquals(1, log.lines().filter(l -> l.contains("minecraft:stone")).count());
    }

    /** The default: opening a chest, touching nothing and closing it leaves no trace at all. */
    @Test
    void dropsAVisitThatChangedNothing() throws Exception {
        Path dir = Files.createTempDirectory("echest-activity-unchanged");
        UUID player = UUID.randomUUID();

        ChestActivityLogger logger = newLogger(dir, "unchanged", true);
        logger.recordCapturedCycle("Alex", player, player, 1, CHEST_SIZE, List.of(), List.of());
        logger.recordCapturedCycle("Alex", player, player, 1, CHEST_SIZE, opened(), opened());
        ChestActivityLogger.PipelineStats afterUnchanged = logger.pipelineStats();

        // A single item taken is still recorded, so the rule cannot hide an actual loss.
        logger.recordCapturedCycle("Alex", player, player, 1, CHEST_SIZE, opened(), closed());
        logger.shutdown();

        Path file = dir.resolve("logs").resolve("echest-latest.log");
        String log = Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
        deleteTree(dir);

        assertEquals(0, afterUnchanged.accepted(), "an unchanged visit must never reach the queue");
        assertEquals(2, afterUnchanged.unchanged(), "both unchanged visits should be counted");
        assertEquals(1, logger.pipelineStats().written(), "only the changed visit belongs in the log");
        assertEquals(1, log.lines().filter(l -> l.contains("] OPEN ")).count());
        assertTrue(log.contains("  TAKE  minecraft:stone x32"));
    }

    /**
     * Nothing gained and nothing lost is nothing worth writing. Now that slot positions are not
     * recorded, a chest whose items only moved around compares equal and is dropped.
     */
    @Test
    void dropsAVisitThatOnlyMovedItems() throws Exception {
        Path dir = Files.createTempDirectory("echest-activity-moved");
        UUID player = UUID.randomUUID();

        List<ChestActivityLogger.CapturedStack> before = List.of(
                stack("plain:minecraft:stone", "minecraft:stone", 64),
                stack("plain:minecraft:dirt", "minecraft:dirt", 8));
        List<ChestActivityLogger.CapturedStack> after = List.of(
                stack("plain:minecraft:dirt", "minecraft:dirt", 8),
                stack("plain:minecraft:stone", "minecraft:stone", 64));

        ChestActivityLogger logger = newLogger(dir, "moved", true);
        logger.recordCapturedCycle("Alex", player, player, 1, CHEST_SIZE, before, after);
        logger.shutdown();

        assertEquals(1, logger.pipelineStats().unchanged(), "a reordered chest changed nothing");
        assertEquals(0, logger.pipelineStats().accepted());
        assertFalse(Files.exists(dir.resolve("logs").resolve("echest-latest.log")));
        deleteTree(dir);
    }

    // ---- retention ----

    /**
     * The active file shares the rotated prefix, so it is only excluded by an explicit name check; this
     * pins that, because losing it would delete the live audit trail.
     */
    @Test
    void prunesOldRotatedFilesButNeverTheActiveOne() throws Exception {
        Path dir = Files.createTempDirectory("echest-activity-retention");
        Path logs = Files.createDirectories(dir.resolve("logs"));

        Path active = aged(logs.resolve("echest-latest.log"), 400);
        Path oldGzip = aged(logs.resolve("echest-20250101-120000-000.log.gz"), 400);
        Path oldPlain = aged(logs.resolve("echest-20250102-120000-000.log"), 400);
        Path recentGzip = aged(logs.resolve("echest-20260727-120000-000.log.gz"), 1);
        Path unrelated = aged(logs.resolve("notes.txt"), 400);
        Path staleTemp = aged(logs.resolve("echest-20250103-120000-000.log.gz.tmp"), 400);

        // The writer sweeps once when it starts, whether or not it ever opens the active file.
        newLogger(dir, "retention", true).shutdown();

        assertTrue(Files.exists(active), "the file being written must never be pruned");
        assertTrue(Files.exists(recentGzip), "a rotated file inside the retention window must stay");
        assertTrue(Files.exists(unrelated), "retention must not touch unrelated files");
        assertTrue(Files.exists(staleTemp), "an interrupted compression leaves a .tmp; not a log");
        assertFalse(Files.exists(oldGzip), "an expired compressed log should be deleted");
        assertFalse(Files.exists(oldPlain), "an expired uncompressed log should be deleted");

        // The active file was appended to, not truncated, so an existing audit trail survives a restart.
        assertTrue(Files.readString(active, StandardCharsets.UTF_8).startsWith("keep me"),
                "reopening the active file must append, never overwrite");

        deleteTree(dir);
    }

    /**
     * A disabled logger must leave no trace on disk. The worker thread still runs, but it may not open
     * the active file until there is something to write, or a server that never turned the feature on
     * would find a logs/ folder and an empty log it never asked for.
     */
    @Test
    void writesNothingAtAllWhileDisabled() throws Exception {
        Path dir = Files.createTempDirectory("echest-activity-disabled");
        UUID player = UUID.randomUUID();

        ChestActivityLogger logger = newLogger(dir, "disabled", false);
        logger.recordCapturedCycle("Alex", player, player, 1, 27, List.of(), List.of());
        // Long enough for the writer loop to come round at least once and try to open a file.
        Thread.sleep(1_200L);
        logger.shutdown();

        assertFalse(Files.exists(dir.resolve("logs")),
                "a disabled logger must not create the logs folder");
        deleteTree(dir);
    }

    // ---- helpers ----

    private static ChestActivityLogger newLogger(Path dir, String label, boolean enabled) {
        return new ChestActivityLogger(dir, LoggerFactory.getLogger("activity-" + label),
                Telemetry.NOOP, enabled, false, 64, 1, RETENTION_DAYS);
    }

    private static String readLog(Path dir) throws Exception {
        return Files.readString(dir.resolve("logs").resolve("echest-latest.log"), StandardCharsets.UTF_8);
    }

    private static List<ChestActivityLogger.CapturedStack> opened() {
        List<ChestActivityLogger.CapturedStack> stacks = new ArrayList<>();
        stacks.add(stack("plain:minecraft:stone", "minecraft:stone", 64));
        stacks.add(stack("plain:minecraft:dirt", "minecraft:dirt", 12));
        stacks.add(stack("meta:minecraft:diamond_sword:1f3a2b0c", SWORD, 1));
        stacks.add(stack("plain:minecraft:torch", "minecraft:torch", 16));
        return List.copyOf(stacks);
    }

    private static List<ChestActivityLogger.CapturedStack> closed() {
        List<ChestActivityLogger.CapturedStack> stacks = new ArrayList<>();
        stacks.add(stack("plain:minecraft:stone", "minecraft:stone", 32));   // 32 taken
        stacks.add(stack("plain:minecraft:dirt", "minecraft:dirt", 12));
        stacks.add(stack("meta:minecraft:diamond_sword:1f3a2b0c", SWORD, 1));
        stacks.add(stack("plain:minecraft:torch", "minecraft:torch", 16));
        stacks.add(stack("plain:minecraft:redstone", "minecraft:redstone", 24)); // added
        return List.copyOf(stacks);
    }

    private static ChestActivityLogger.CapturedStack stack(String identity, String description,
                                                           int amount) {
        return new ChestActivityLogger.CapturedStack(identity, description, amount);
    }

    private static Path aged(Path path, int daysOld) throws Exception {
        Files.writeString(path, "keep me" + System.lineSeparator(), StandardCharsets.UTF_8);
        Files.setLastModifiedTime(path, FileTime.from(Instant.now().minus(Duration.ofDays(daysOld))));
        return path;
    }

    private static void deleteTree(Path root) throws Exception {
        try (var paths = Files.walk(root)) {
            paths.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(path -> path.toFile().delete());
        }
    }
}
