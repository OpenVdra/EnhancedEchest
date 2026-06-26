package com.enhancedechest.migration;

import com.enhancedechest.migration.AxVaultsReader.VaultRow;
import com.enhancedechest.model.EnderChestData;
import com.enhancedechest.serialization.ContainerCodec;
import com.enhancedechest.storage.EnderChestStorage;
import lombok.RequiredArgsConstructor;
import org.bukkit.inventory.ItemStack;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Migrates vaults from an AxVaults installation into EnhancedEchest's storage.
 *
 * <p>Each AxVaults vault is imported into the EnhancedEchest chest at the <b>same index</b> (vault
 * #3 → chest #3), so a player's layout carries over. A vault is only written when no chest already
 * holds data at that index — a chest with existing contents is left untouched and counted as skipped,
 * which makes the migration safe to re-run and prevents it from ever overwriting live data.
 *
 * <p>All work here is synchronous; callers dispatch it onto the shared DB executor. The vault items
 * are already decoded by {@link AxVaultsReader}; this class only resizes/encodes/writes into storage.
 */
@RequiredArgsConstructor
public final class AxVaultsMigrationService {

    private final EnderChestStorage storage;
    private final ContainerCodec codec;
    private final Logger logger;
    /** The server {@code plugins/} directory; the AxVaults data lives in {@code plugins/AxVaults}. */
    private final Path pluginsFolder;

    /** Outcome of a migration run. */
    public record Result(int playersMigrated, int vaultsMigrated, int vaultsSkipped, int playersFailed) {}

    /** True if an AxVaults database file is present to migrate from. */
    public boolean sourceAvailable() {
        Path folder = pluginsFolder.resolve("AxVaults");
        return Files.exists(folder.resolve("data.db"))
                || Files.exists(folder.resolve("data.mv.db"));
    }

    /** Migrates every player found in the AxVaults database. */
    public Result migrateAll() throws Exception {
        try (AxVaultsReader reader = openReader()) {
            Map<UUID, List<VaultRow>> byOwner = reader.readAll();
            logger.info("[AxVaults] Read {} player(s) from {} database", byOwner.size(), reader.backend());

            int players = 0, vaults = 0, skipped = 0, failed = 0;
            for (Map.Entry<UUID, List<VaultRow>> entry : byOwner.entrySet()) {
                try {
                    int[] counts = migrateRows(entry.getKey(), entry.getValue());
                    if (counts[0] > 0) players++;
                    vaults += counts[0];
                    skipped += counts[1];
                } catch (Exception e) {
                    failed++;
                    logger.error("[AxVaults] Failed migrating {}", entry.getKey(), e);
                }
            }
            return new Result(players, vaults, skipped, failed);
        }
    }

    /** Migrates a single player by UUID. Returns the same counters scoped to that player. */
    public Result migratePlayer(UUID owner) throws Exception {
        try (AxVaultsReader reader = openReader()) {
            List<VaultRow> rows = reader.read(owner);
            if (rows.isEmpty()) {
                return new Result(0, 0, 0, 0);
            }
            int[] counts = migrateRows(owner, rows);
            return new Result(counts[0] > 0 ? 1 : 0, counts[0], counts[1], 0);
        }
    }

    private AxVaultsReader openReader() throws Exception {
        return AxVaultsReader.open(pluginsFolder.resolve("AxVaults"), logger);
    }

    /**
     * Imports one player's vaults. Returns {@code [migrated, skipped]} counts.
     *
     * <p>For each vault: ensure a chest exists at the same index, grow it if the items need more room
     * than an existing chest has, then write the items — unless the chest already holds data, in which
     * case it is left alone (skipped).
     */
    private int[] migrateRows(UUID owner, List<VaultRow> rows) {
        int migrated = 0, skipped = 0;
        for (VaultRow row : rows) {
            int needed = fitSize(row.items().length);

            EnderChestData existing = storage.loadChest(owner, row.id());
            if (existing != null && existing.containerData() != null) {
                // A chest with contents already lives here — never clobber it.
                skipped++;
                continue;
            }

            int size;
            if (existing == null) {
                storage.ensureChest(owner, row.id(), needed);
                size = needed;
            } else {
                size = Math.max(existing.size(), needed);
                if (size != existing.size()) {
                    storage.resizeChest(owner, row.id(), size);
                }
            }

            ItemStack[] slots = new ItemStack[size];
            int copy = Math.min(row.items().length, size);
            System.arraycopy(row.items(), 0, slots, 0, copy);

            byte[] encoded;
            try {
                encoded = codec.encode(slots);
            } catch (Exception e) {
                logger.error("[AxVaults] Encode failed for vault #{} of {} — skipping", row.id(), owner, e);
                skipped++;
                continue;
            }
            storage.saveChest(owner, row.id(), encoded);

            if (row.iconMaterial() != null && !row.iconMaterial().isBlank()) {
                storage.setIcon(owner, row.id(), "minecraft:" + row.iconMaterial().toLowerCase());
            }

            migrated++;
            logger.info("[AxVaults] Imported vault #{} ({} slots) for {}", row.id(), size, owner);
        }
        return new int[]{migrated, skipped};
    }

    /** Rounds a slot count up to a valid chest size: a multiple of 9, clamped to 9..54. */
    private static int fitSize(int slots) {
        int rounded = ((Math.max(slots, 1) + ContainerCodec.SLOT_STEP - 1) / ContainerCodec.SLOT_STEP)
                * ContainerCodec.SLOT_STEP;
        return Math.clamp(rounded, ContainerCodec.SLOT_STEP, ContainerCodec.MAX_SIZE);
    }
}
