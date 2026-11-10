package com.enhancedechest.gui;

import lombok.Getter;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
 *
 * <p>An admin holding {@code enhancedechest.admin.log.take} gets a <b>take-out</b> preview instead
 * ({@link #canTake}): items may leave the preview (to their inventory, the cursor or the ground) but
 * nothing may go <i>into</i> it, because the preview is discarded on close and anything placed there
 * would be destroyed. The mode is fixed when the preview opens so one window never mixes the two rule
 * sets; {@link #original} keeps the opened contents so the close handler can log exactly what was taken.
 */
@Getter
public final class LogSnapshotHolder implements InventoryHolder {

    private final UUID owner;
    private final String ownerName;
    private final int index;
    /** Log viewer page to return to when this preview is closed. */
    private final int page;
    /** Active search filter to restore on return, or {@code null} for the full log. */
    @Nullable
    private final String query;
    /** Logged visit id, for the take audit line. */
    private final long entryId;
    /** True when this preview lets the admin take items out (decided once, at open). */
    private final boolean canTake;
    /** Contents as opened (defensive copies), compared on close to log what was taken; empty when not {@link #canTake}. */
    private final ItemStack[] original;

    public LogSnapshotHolder(UUID owner, String ownerName, int index, int page, @Nullable String query,
                             long entryId, boolean canTake, ItemStack[] contents) {
        this.owner = owner;
        this.ownerName = ownerName;
        this.index = index;
        this.page = page;
        this.query = query;
        this.entryId = entryId;
        this.canTake = canTake;
        if (canTake) {
            this.original = new ItemStack[contents.length];
            for (int i = 0; i < contents.length; i++) {
                this.original[i] = contents[i] == null ? null : contents[i].clone();
            }
        } else {
            this.original = new ItemStack[0];
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        throw new UnsupportedOperationException("LogSnapshotHolder does not hold an Inventory reference");
    }
}
