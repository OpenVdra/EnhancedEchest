package com.enhancedechest.migration;

import com.enhancedechest.serialization.ContainerCodec;
import com.enhancedechest.storage.EnderChestStorage;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.slf4j.Logger;

import java.util.UUID;

/**
 * Orchestrates migration of vanilla enderchest data into the plugin's storage.
 *
 * Single-location invariant: items are moved from vanilla EC to plugin DB in one
 * atomic sync operation with no window where they exist in both places.
 */
@RequiredArgsConstructor
public final class MigrationService {

    private final EnderChestStorage storage;
    private final ContainerCodec codec;
    private final Logger logger;

    /**
     * Migrates a live online player's vanilla enderchest (27 slots) into the
     * first 27 slots of the custom 54-slot storage.
     *
     * @return true if migration ran; false if the player was already migrated or encode failed.
     */
    public boolean migrateOnline(Player player) {
        UUID uuid = player.getUniqueId();

        if (storage.isMigrated(uuid)) {
            return false;
        }

        ItemStack[] vanilla = player.getEnderChest().getContents();

        // Map vanilla slots 0-26 into custom slots 0-26; slots 27-53 stay empty.
        ItemStack[] combined = new ItemStack[ContainerCodec.CHEST_SIZE];
        System.arraycopy(vanilla, 0, combined, 0, vanilla.length);

        byte[] encoded;
        try {
            encoded = codec.encode(combined);
        } catch (Exception e) {
            logger.error("Codec encode failed during migration for {} — migration aborted", player.getName(), e);
            return false;
        }

        // Persist to DB, clear vanilla EC, and flag migrated — all in the same main-thread tick.
        // No window where items exist in both locations.
        storage.save(uuid, encoded);
        player.getEnderChest().clear();
        storage.setMigrated(uuid, true);

        logger.info("Migrated {} — {} item(s) moved from vanilla EC", player.getName(), countNonEmpty(vanilla));
        return true;
    }

    private static int countNonEmpty(ItemStack[] items) {
        int count = 0;
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) count++;
        }
        return count;
    }
}
