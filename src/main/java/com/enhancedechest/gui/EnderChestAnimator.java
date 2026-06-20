package com.enhancedechest.gui;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Lidded;
import org.bukkit.entity.Player;

/**
 * Triggers the vanilla ender chest open/close lid animation and sound.
 *
 * Uses the pure Paper API ({@link org.bukkit.block.Lidded}) — no NMS, no reflection.
 * {@code Lidded#open()} / {@code Lidded#close()} send the vanilla BlockAction to nearby
 * clients (animating the lid) and play the open/close sound, exactly like a real chest.
 *
 * Needed because the plugin opens a custom GUI inventory instead of the real ender chest
 * inventory, so vanilla never animates the block on its own.
 *
 * Silently degrades (no-op) if the block is missing/not an ender chest, or if the running
 * server build doesn't expose {@code Lidded} on ender chests.
 *
 * Thread requirement: must be called on the region thread that owns the block's chunk
 * (Folia) or the main thread (Spigot/Paper). Callers already dispatch via runAtLocation.
 */
public final class EnderChestAnimator {

    private EnderChestAnimator() {}

    /**
     * Plays the open animation and sound on the ender chest at {@code blockLoc}.
     * Call this when the player opens the custom GUI via block right-click.
     */
    public static void open(Player player, Location blockLoc) {
        animate(blockLoc, true);
    }

    /**
     * Plays the close animation and sound on the ender chest at {@code blockLoc}.
     * Call this when the player closes the custom GUI.
     */
    public static void close(Player player, Location blockLoc) {
        animate(blockLoc, false);
    }

    private static void animate(Location blockLoc, boolean open) {
        if (blockLoc == null || blockLoc.getWorld() == null) return;

        Block block = blockLoc.getBlock();
        if (block.getType() != Material.ENDER_CHEST) return;

        BlockState state = block.getState();
        if (!(state instanceof Lidded lidded)) return;

        if (open) {
            lidded.open();
        } else {
            lidded.close();
        }
    }
}
