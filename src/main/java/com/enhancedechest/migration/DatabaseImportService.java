package com.enhancedechest.migration;

import com.enhancedechest.config.PluginConfig;
import com.enhancedechest.storage.EnderChestStorage;
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

    private final EnderChestStorage storage;
    private final PluginConfig config;
    private final Logger logger;
    /** Plugin data folder, for resolving relative SQLite paths and the active-destination identity. */
    private final Path dataFolder;

    public DatabaseImportService(EnderChestStorage storage, PluginConfig config, Logger logger,
                                 Path dataFolder) {
        this.storage = storage;
        this.config = config;
        this.logger = logger;
        this.dataFolder = dataFolder;
    }

    /** Outcome of an import run: how many rows landed in the destination. */
    public record Result(int players, int chests) {}

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

    /** Number of chest rows already in the destination; import requires this to be 0 (a fresh DB). */
    public long destinationChestCount() {
        return storage.countChests();
    }

    /**
     * Reads the source described by {@code spec} in full and writes it into the active destination in one
     * transaction. Synchronous — call on the DB executor. A missing source column surfaces as a
     * {@code SQLException} ("source schema outdated"); a duplicate key rolls the whole import back.
     */
    public Result importFrom(SourceSpec spec) throws Exception {
        try (SourceDatabaseReader reader = SourceDatabaseReader.open(spec, dataFolder, logger, config.getTablePrefix())) {
            SourceDatabaseReader.Data data = reader.readAll();
            logger.info("[Import] Read {} player row(s) and {} chest row(s) from {} source",
                    data.players().size(), data.chests().size(), reader.backend());
            int[] counts = storage.importRows(data.players(), data.chests());
            logger.info("[Import] Wrote {} player row(s) and {} chest row(s) into the active database",
                    counts[0], counts[1]);
            return new Result(counts[0], counts[1]);
        }
    }
}
