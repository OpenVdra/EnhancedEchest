package com.enhancedechest.gui;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Marker holder attached to every custom enderchest Inventory.
 * Listeners identify our GUIs with: inventory.getHolder() instanceof EnderChestHolder
 */
@Getter
@RequiredArgsConstructor
public final class EnderChestHolder implements InventoryHolder {

    private final UUID owner;

    /** Not used — the holder does not hold a back-reference to the Inventory. */
    @Override
    public @NotNull Inventory getInventory() {
        throw new UnsupportedOperationException("EnderChestHolder does not hold an Inventory reference");
    }
}
