package com.enhancedechest.gui;

import lombok.Getter;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Marker holder for a chest-log <b>snapshot preview</b>: a throwaway inventory rebuilt from a logged
 * visit's stored contents, opened when an admin clicks an entry in the {@code /ee log} viewer. It is
 * <i>not</i> a live chest and is never saved — nothing here funnels through the session manager, so
 * closing it simply discards it and the real chest is untouched.
 *
 * <p>The preview is a dupe-proof sandbox: {@link com.enhancedechest.listener.LogMenuListener} lets the
 * admin rearrange items <i>within</i> the preview to inspect them, but blocks every path that could move
 * an item into their own inventory (shift-click, hotbar swap, drop, drags/clicks touching the bottom
 * inventory) and clears the cursor on close. On close it re-opens the log at {@link #page} — the page
 * the admin was viewing when they clicked in — so pressing Esc/E lands back on the same list.
 */
@Getter
public final class LogSnapshotHolder implements InventoryHolder {

    private final UUID owner;
    private final String ownerName;
    private final int index;
    /** Log viewer page to return to when this preview is closed. */
    private final int page;

    public LogSnapshotHolder(UUID owner, String ownerName, int index, int page) {
        this.owner = owner;
        this.ownerName = ownerName;
        this.index = index;
        this.page = page;
    }

    @Override
    public @NotNull Inventory getInventory() {
        throw new UnsupportedOperationException("LogSnapshotHolder does not hold an Inventory reference");
    }
}
