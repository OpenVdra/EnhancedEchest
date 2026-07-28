package com.enhancedechest.storage;

import com.enhancedechest.model.ChestKind;
import com.enhancedechest.model.ChestSummary;
import com.enhancedechest.storage.sql.SqliteStorage;
import com.enhancedechest.telemetry.Telemetry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers where a new chest lands. The index is the number the player reads on the chest ("Ender Chest
 * 3"), so a delete must free its number for the next chest instead of the counter climbing forever.
 */
class ChestIndexAllocationTest {

    private static final Logger LOG = LoggerFactory.getLogger(ChestIndexAllocationTest.class);

    private Path dir;
    private CachedStorage storage;
    private final UUID owner = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        dir = Files.createTempDirectory("ee-index");
        storage = new CachedStorage(new SqliteStorage(dir, "index.db", "echest_"), LOG, Telemetry.NOOP);
        storage.init();
    }

    @AfterEach
    void tearDown() throws Exception {
        storage.close();
        try (var paths = Files.walk(dir)) {
            paths.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> p.toFile().delete());
        }
    }

    private List<Integer> indices() {
        return storage.listChests(owner).stream().map(ChestSummary::index).toList();
    }

    @Test
    void numbersRunFromOneUpwardsOnAFreshPlayer() {
        assertEquals(1, storage.createChest(owner, 27, null));
        assertEquals(2, storage.createChest(owner, 27, null));
        assertEquals(3, storage.createPermChest(owner, 27));
    }

    @Test
    void aDeletedNumberIsReusedByTheNextChest() {
        storage.createChest(owner, 27, null);       // 1
        storage.createChest(owner, 27, null);       // 2
        storage.createChest(owner, 27, null);       // 3

        storage.deleteChest(owner, 2);
        assertEquals(2, storage.createChest(owner, 27, null), "the freed number comes back");
        assertEquals(List.of(1, 2, 3), indices());
    }

    @Test
    void deletingTheLastChestDoesNotPushTheCounterUp() {
        storage.createChest(owner, 27, null);       // 1
        storage.createChest(owner, 27, null);       // 2
        storage.deleteChest(owner, 2);

        assertEquals(2, storage.createChest(owner, 27, null));
        assertEquals(List.of(1, 2), indices());
    }

    @Test
    void severalGapsAreFilledLowestFirst() {
        for (int i = 0; i < 5; i++) storage.createChest(owner, 27, null);   // 1..5
        storage.deleteChest(owner, 2);
        storage.deleteChest(owner, 4);

        assertEquals(2, storage.createPermChest(owner, 27));
        assertEquals(4, storage.createPermChest(owner, 27));
        assertEquals(6, storage.createPermChest(owner, 27), "then it grows again");
        assertEquals(List.of(1, 2, 3, 4, 5, 6), indices());
    }

    /** Temp chests keep going to the end, so they never appear between the player's real chests. */
    @Test
    void aTempChestIsAppendedAfterTheHighestNumberEvenWithAGapBelow() {
        storage.createChest(owner, 27, null);       // 1
        storage.createChest(owner, 27, null);       // 2
        storage.createChest(owner, 27, null);       // 3
        storage.saveChest(owner, 3, new byte[]{1, 2});
        storage.deleteChest(owner, 2);              // leaves the gap at 2

        storage.spillRemove(owner, 3, new byte[]{1, 2}, 27, System.currentTimeMillis() + 60_000);

        List<ChestSummary> chests = storage.listChests(owner);
        assertEquals(List.of(1, 4), chests.stream().map(ChestSummary::index).toList());
        assertEquals(ChestKind.TEMP, chests.get(1).kind());
    }
}
