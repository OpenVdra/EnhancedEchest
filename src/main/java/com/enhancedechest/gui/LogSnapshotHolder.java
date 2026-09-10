package com.enhancedechest.gui;

import lombok.Getter;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Marker holder for a chest-log <b>snapshot preview</b>: a throwaway inventory rebuilt from a logged
 * event's stored contents, opened when an admin clicks an event pane in the {@code /ee log} viewer. It
 * is <i>not</i> a live chest and is never saved — nothing here funnels through the session manager, so
 * closing it simply discards it and the real chest is untouched.
 *
 * <p>The preview is a dupe-proof sandbox: {@link com.enhancedechest.listener.LogMenuListener} lets the
 * admin rearrange items <i>within</i> the preview to inspect them, but blocks every path that could move
 * an item into their own inventory (shift-click, hotbar swap, drop, drags/clicks touching the bottom
 * inventory) and clears the cursor on close, so no item can leak out of a historical snapshot.
 */
@Getter
public final class LogSnapshotHolder implements InventoryHolder {

    private final UUID owner;
    private final int index;

    public LogSnapshotHolder(UUID owner, int index) {
        this.owner = owner;
        this.index = index;
    }

    @Override
    public @NotNull Inventory getInventory() {
        throw new UnsupportedOperationException("LogSnapshotHolder does not hold an Inventory reference");
    }
}
