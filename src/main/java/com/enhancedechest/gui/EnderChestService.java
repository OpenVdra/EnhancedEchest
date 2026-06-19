package com.enhancedechest.gui;

import com.enhancedechest.config.PluginConfig;
import com.enhancedechest.model.EnderChestData;
import com.enhancedechest.serialization.CodecException;
import com.enhancedechest.serialization.ContainerCodec;
import com.enhancedechest.storage.EnderChestStorage;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.slf4j.Logger;

/**
 * Owns the open and save lifecycle of the custom enderchest GUI.
 *
 * Dupe-safety contract:
 * - open() always loads a fresh snapshot from DB.
 * - If the player already has our GUI open, we close it first (firing InventoryCloseEvent
 *   synchronously, which saves the current state to DB) before loading the fresh snapshot.
 * - save() writes immediately to DB and releases the Inventory object — nothing is cached.
 * - All operations run on the main thread; no async path exists.
 */
@RequiredArgsConstructor
public final class EnderChestService {

    private final PluginConfig config;
    private final ContainerCodec codec;
    private final EnderChestStorage storage;
    private final Logger logger;

    /**
     * Opens the custom enderchest for the player.
     * If the player already has the custom GUI open, it is closed first (save fires) so that
     * the subsequent DB load picks up the player's latest edits.
     */
    public void open(Player player) {
        // If the player's current top inventory is our GUI, close it before loading.
        // player.closeInventory() fires InventoryCloseEvent synchronously, which triggers
        // EnderChestGuiListener.onClose(), which calls save(). The load below then reads
        // the just-written data — no stale state, no dupe window.
        Inventory currentTop = player.getOpenInventory().getTopInventory();
        if (currentTop.getHolder() instanceof EnderChestHolder) {
            player.closeInventory();
        }

        EnderChestData data;
        try {
            data = storage.load(player.getUniqueId());
        } catch (Exception e) {
            logger.error("Failed to load enderchest for {} — aborting open", player.getName(), e);
            player.sendMessage(Component.text("[EnhancedEChest] Could not load your enderchest. Please contact an admin."));
            return;
        }

        Inventory inv = Bukkit.createInventory(
                new EnderChestHolder(player.getUniqueId()),
                ContainerCodec.CHEST_SIZE,
                Component.text(config.getGuiTitle())
        );

        if (data != null && data.containerData() != null && data.containerData().length > 0) {
            try {
                inv.setContents(codec.decode(data.containerData()));
            } catch (CodecException e) {
                // Data present but unreadable — abort rather than showing an empty chest.
                // Opening an empty chest would overwrite good data on close.
                logger.error("Codec failure for {} — aborting open to protect stored data", player.getName(), e);
                player.sendMessage(Component.text(
                        "[EnhancedEChest] Your enderchest data could not be read. Please contact an admin."));
                return;
            }
        }

        player.openInventory(inv);
    }

    /**
     * Saves the current inventory contents to DB immediately.
     * Called by listeners on InventoryCloseEvent and PlayerQuitEvent.
     */
    public void save(EnderChestHolder holder, Inventory inventory) {
        byte[] encoded;
        try {
            encoded = codec.encode(inventory.getContents());
        } catch (Exception e) {
            logger.error("Codec encode failure for {} — data NOT saved to prevent corruption",
                    holder.getOwner(), e);
            return;
        }

        try {
            storage.save(holder.getOwner(), encoded);
        } catch (Exception e) {
            logger.error("DB save failure for {}", holder.getOwner(), e);
        }
    }
}
