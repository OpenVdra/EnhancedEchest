package com.enhancedechest.storage;

import com.enhancedechest.model.ChestSummary;
import com.enhancedechest.model.EnderChestData;
import com.enhancedechest.model.ChestKind;
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers {@link CachedStorage#reclaimTemp} — the move that pulls a temp chest whole into a newly
 * granted empty chest — against a real {@link SqliteStorage} on a throwaway DB, no Minecraft server.
 *
 * <p>The contents are opaque bytes here on purpose: reclaim copies them verbatim (that is what keeps
 * every item on its original slot), so the assertions compare the byte array itself rather than
 * decoding items, which would need a live Bukkit registry.
 */
class TempReclaimTest {

    private static final Logger LOG = LoggerFactory.getLogger(TempReclaimTest.class);

    private Path dir;
    private CachedStorage storage;
    private final UUID owner = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        dir = Files.createTempDirectory("ee-reclaim");
        storage = new CachedStorage(new SqliteStorage(dir, "reclaim.db", "echest_"), LOG, Telemetry.NOOP);
        storage.init();
    }

    @AfterEach
    void tearDown() throws Exception {
        storage.close();
        try (var paths = Files.walk(dir)) {
            paths.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> p.toFile().delete());
        }
    }

    /** Creates a temp chest of {@code size} holding {@code items} and returns its index. */
    private int spillTemp(int size, byte[] items) {
        int index = storage.createChest(owner, size, null);
        storage.saveChest(owner, index, items);
        // spillRemove deletes the source row and re-inserts the items as a temp chest at the next index.
        storage.spillRemove(owner, index, items, size, System.currentTimeMillis() + 60_000);
        return storage.listChests(owner).stream()
                .filter(c -> c.kind() == ChestKind.TEMP)
                .mapToInt(ChestSummary::index)
                .max()
                .orElseThrow();
    }

    @Test
    void movesTempIntoALargerEmptyChestVerbatimAndDeletesTheTemp() {
        byte[] items = {1, 2, 3, 4};
        int tempIndex = spillTemp(27, items);
        int targetIndex = storage.createPermChest(owner, 54);

        assertTrue(storage.reclaimTemp(owner, tempIndex, targetIndex));

        EnderChestData target = storage.loadChest(owner, targetIndex);
        assertNotNull(target);
        assertArrayEquals(items, target.containerData(), "bytes must be copied verbatim, not re-packed");
        assertEquals(54, target.size(), "the target keeps its own size");
        assertNull(storage.loadChest(owner, tempIndex), "the emptied temp chest is deleted");
        assertTrue(storage.listChests(owner).stream().noneMatch(c -> c.kind() == ChestKind.TEMP));
    }

    @Test
    void movesTempIntoAnEqualSizedChest() {
        int tempIndex = spillTemp(27, new byte[]{9});
        int targetIndex = storage.createPermChest(owner, 27);

        assertTrue(storage.reclaimTemp(owner, tempIndex, targetIndex));
        assertNull(storage.loadChest(owner, tempIndex));
    }

    @Test
    void refusesWhenTheTempIsLargerThanTheTarget() {
        byte[] items = {7, 7};
        int tempIndex = spillTemp(54, items);
        int targetIndex = storage.createPermChest(owner, 27);

        assertFalse(storage.reclaimTemp(owner, tempIndex, targetIndex),
                "a temp chest that does not fit must be left alone, never split");

        EnderChestData temp = storage.loadChest(owner, tempIndex);
        assertNotNull(temp, "the temp chest survives a refused reclaim");
        assertArrayEquals(items, temp.containerData());
        assertNull(storage.loadChest(owner, targetIndex).containerData(), "the target stays empty");
    }

    @Test
    void refusesWhenTheTargetAlreadyHoldsItems() {
        int tempIndex = spillTemp(27, new byte[]{1});
        int targetIndex = storage.createPermChest(owner, 54);
        storage.saveChest(owner, targetIndex, new byte[]{5, 5});

        assertFalse(storage.reclaimTemp(owner, tempIndex, targetIndex),
                "a non-empty target would have its layout overwritten");
        assertArrayEquals(new byte[]{5, 5}, storage.loadChest(owner, targetIndex).containerData());
        assertNotNull(storage.loadChest(owner, tempIndex));
    }

    @Test
    void refusesToMoveIntoATempChestOrOntoItself() {
        int tempIndex = spillTemp(27, new byte[]{1});
        int otherTemp = spillTemp(27, new byte[]{2});

        assertFalse(storage.reclaimTemp(owner, tempIndex, otherTemp), "temp → temp is not a reclaim");
        assertFalse(storage.reclaimTemp(owner, tempIndex, tempIndex));
        assertEquals(2, storage.listChests(owner).stream().filter(c -> c.kind() == ChestKind.TEMP).count());
    }

    @Test
    void refusesWhenARowIsMissing() {
        int targetIndex = storage.createPermChest(owner, 54);
        assertFalse(storage.reclaimTemp(owner, 999, targetIndex));
        assertFalse(storage.reclaimTemp(owner, targetIndex, 999));
    }

    /** The move must survive a flush + eviction round trip, i.e. it really reached the database. */
    @Test
    void thePersistedRowsMatchAfterAFlush() {
        byte[] items = {4, 2};
        int tempIndex = spillTemp(27, items);
        int targetIndex = storage.createPermChest(owner, 54);
        assertTrue(storage.reclaimTemp(owner, tempIndex, targetIndex));

        storage.flush();
        storage.evictIdle();

        List<ChestSummary> after = storage.listChests(owner);
        assertTrue(after.stream().noneMatch(c -> c.kind() == ChestKind.TEMP),
                "the temp row must be gone from the database, not just from the cache");
        assertArrayEquals(items, storage.loadChest(owner, targetIndex).containerData());
    }
}
