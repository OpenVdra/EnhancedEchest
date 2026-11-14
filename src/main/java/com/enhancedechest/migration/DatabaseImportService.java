package com.enhancedechest.migration;

import com.enhancedechest.config.PluginConfig;
import com.enhancedechest.serialization.CodecException;
import com.enhancedechest.serialization.ContainerCodec;
import com.enhancedechest.storage.EnderChestStorage;
import com.enhancedechest.storage.EnderChestStorage.RawChestRow;
import org.bukkit.inventory.ItemStack;
import org.slf4j.Logger;

import java.nio.file.Path;

/**
 * Copies all EnhancedEchest data from an old database backend into the plugin's <b>active</b> backend
 * ({@code /ee import}). The admin edits {@code config.yml} to the new (destination) backend, restarts
 * with no players online, and runs the import; this reads the old backend fully and writes it into the
 * new one in one transaction — no second restart needed.
 *
 * <p>The heavy work runs on the shared DB executor (never a region thread). Item bytes are copied
 * verbatim (no (de)serialization), so cost is dominated by the batched destination writes.
 */
public final class DatabaseImportService {

    /**
     * Above this many destination chest rows the database is treated as in use without decoding them.
     * Empty leftovers come from the handful of people who opened an ender chest after the switch; a
     * real install is far past this, and decoding all of it would only reach the same refusal slower.
     */
    private static final long MAX_LEFTOVER_CHESTS = 500;

    private final EnderChestStorage storage;
    private final ContainerCodec codec;
    private final PluginConfig config;
    private final Logger logger;
    /** Plugin data folder, for resolving relative SQLite paths and the active-destination identity. */
    private final Path dataFolder;

    public DatabaseImportService(EnderChestStorage storage, ContainerCodec codec, PluginConfig config,
                                 Logger logger, Path dataFolder) {
        this.storage = storage;
        this.codec = codec;
        this.config = config;
        this.logger = logger;
        this.dataFolder = dataFolder;
    }

    /** Outcome of an import run: how many rows landed in the destination. */
    public record Result(int players, int chests) {}

    /**
     * What the destination holds before an import. {@code chests} rows exist; {@code withItems} of them
     * hold at least one item, or {@code -1} when there were too many rows to check (treated as in use).
     */
    public record DestinationCheck(long chests, long withItems) {
        /** True when the import may go ahead: every existing chest row is empty and will be replaced. */
        public boolean importable() {
            return withItems == 0;
        }
    }

    /**
     * True if {@code spec} points at the very database the plugin is actively running on — importing
     * from the destination into itself makes no sense (and would trip the empty-destination guard), so
     * the caller refuses it up front. Compares stable identities (see {@link SourceSpec#identity}).
     */
    public boolean pointsAtActiveDatabase(SourceSpec spec) {
        String source = spec.identity(dataFolder);
        String dest = SourceSpec.identity(config.getDatabaseType(), config.getSqliteFile(),
                config.getDbHost(), config.getDbPort(), config.getDbName(), dataFolder);
        return source.equals(dest);
    }

    /**
     * Inspects the destination. Chest rows that hold nothing do not block an import: opening an ender
     * chest once creates an empty base chest, and an admin who joined to run {@code /ee import} has very
     * likely done that. Those rows are replaced by the import. A row holding any item — or one that does
     * not decode, since it might — blocks it: the import never merges or overwrites contents.
     * Synchronous — call on the DB executor.
     */
    public DestinationCheck checkDestination() {
        long count = storage.countChests();
        if (count == 0) return new DestinationCheck(0, 0);
        if (count > MAX_LEFTOVER_CHESTS) return new DestinationCheck(count, -1);
        long withItems = 0;
        for (RawChestRow row : storage.loadAllChests()) {
            if (holdsItems(row)) withItems++;
        }
        return new DestinationCheck(count, withItems);
    }

    private boolean holdsItems(RawChestRow row) {
        byte[] data = row.containerData();
        if (data == null || data.length == 0) return false;
        try {
            for (ItemStack item : codec.decode(data, row.size())) {
                if (item != null && !item.isEmpty()) return true;
            }
            return false;
        } catch (CodecException e) {
            logger.warn("[Import] Cannot decode destination chest #{} of {}; treating it as holding items",
                    row.chestIndex(), row.playerUuid(), e);
            return true;
        }
    }

    /**
     * Reads the source described by {@code spec} in full and writes it into the active destination in one
     * transaction, replacing the {@code check}'s empty leftover chest rows. Synchronous — call on the DB
     * executor. A missing source column surfaces as a {@code SQLException} ("source schema outdated"); a
     * duplicate key, or a destination that changed since {@code check}, rolls the whole import back.
     */
    public Result importFrom(SourceSpec spec, DestinationCheck check) throws Exception {
        if (!check.importable()) {
            throw new IllegalStateException("destination holds items");
        }
        try (SourceDatabaseReader reader = SourceDatabaseReader.open(spec, dataFolder, logger, config.getTablePrefix())) {
            SourceDatabaseReader.Data data = reader.readAll();
            logger.info("[Import] Read {} player row(s) and {} chest row(s) from {} source",
                    data.players().size(), data.chests().size(), reader.backend());
            if (check.chests() > 0) {
                logger.info("[Import] Replacing {} empty chest row(s) already in the active database",
                        check.chests());
            }
            int[] counts = storage.importRows(data.players(), data.chests(), check.chests());
            logger.info("[Import] Wrote {} player row(s) and {} chest row(s) into the active database",
                    counts[0], counts[1]);
            return new Result(counts[0], counts[1]);
        }
    }
}
