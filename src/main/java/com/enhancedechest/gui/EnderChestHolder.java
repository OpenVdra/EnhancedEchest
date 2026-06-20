package com.enhancedechest.gui;

import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Marker holder attached to every custom enderchest Inventory.
 * Listeners identify our GUIs with: inventory.getHolder() instanceof EnderChestHolder
 */
@Getter
public final class EnderChestHolder implements InventoryHolder {

    private final UUID owner;

    /**
     * Location of the ender chest block if the GUI was opened by right-clicking a block.
     * Null when opened via /ec command (no physical block involved).
     * Used to play the open/close animation on the block.
     */
    @Nullable
    private final Location sourceBlock;

    public EnderChestHolder(UUID owner) {
        this(owner, null);
    }

    public EnderChestHolder(UUID owner, @Nullable Location sourceBlock) {
        this.owner = owner;
        this.sourceBlock = sourceBlock;
    }

    @Override
    public @NotNull Inventory getInventory() {
        throw new UnsupportedOperationException("EnderChestHolder does not hold an Inventory reference");
    }
}
