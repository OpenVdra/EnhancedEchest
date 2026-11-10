package com.enhancedechest.listener;

import com.enhancedechest.gui.LogMenu;
import com.enhancedechest.gui.LogMenuHolder;
import com.enhancedechest.gui.LogSnapshotHolder;
import com.enhancedechest.log.LogEntry;
import com.enhancedechest.service.LogViewer;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

import java.util.Set;

/**
 * Drives the two inventories the {@code /ee log} feature opens:
 *
 * <ul>
 *   <li>the <b>log viewer</b> ({@link LogMenuHolder}) — a read-only paged list: every click is
 *       cancelled, a click on an event pane opens that snapshot, and the newer/older controls page;</li>
 *   <li>the <b>snapshot preview</b> ({@link LogSnapshotHolder}) — a dupe-proof sandbox: the admin may
 *       rearrange items <i>within</i> the preview to inspect them, but no path can move an item into
 *       their own inventory or the world, and the cursor is cleared on close.</li>
 * </ul>
 */
@RequiredArgsConstructor
public final class LogMenuListener implements Listener {

    /** Click actions that keep items inside the clicked inventory — the only ones a snapshot allows. */
    private static final Set<InventoryAction> SANDBOX_SAFE = Set.of(
            InventoryAction.PICKUP_ALL, InventoryAction.PICKUP_HALF,
            InventoryAction.PICKUP_SOME, InventoryAction.PICKUP_ONE,
            InventoryAction.PLACE_ALL, InventoryAction.PLACE_SOME, InventoryAction.PLACE_ONE,
            InventoryAction.SWAP_WITH_CURSOR, InventoryAction.NOTHING);

    private final LogViewer viewer;

    // ---- log viewer (read-only list) ----

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof LogMenuHolder holder) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) return;
            if (event.getClickedInventory() != top) return;   // a click in the player's own inv, already cancelled
            handleMenuClick(player, holder, event.getSlot(), event.isRightClick());
            return;
        }
        if (top.getHolder() instanceof LogSnapshotHolder holder) {
            if (canTake(event.getWhoClicked())) {
                auditTake(event, top, holder);
                return;   // restore mode: every click allowed (take, shift-click, drop with Q)
            }
            guardSnapshotClick(event, top);
        }
    }

    private void handleMenuClick(Player player, LogMenuHolder holder, int slot, boolean right) {
        LogEntry entry = holder.entryAt(slot);
        if (entry != null) {
            viewer.openSnapshot(player, holder.getOwner(), holder.getOwnerName(), entry,
                    holder.getPage(), holder.getQuery(), right);
            return;
        }
        switch (slot) {
            case LogMenu.SLOT_PREV -> {
                if (holder.getPage() > 0) {
                    viewer.openLog(player, holder.getOwner(), holder.getOwnerName(),
                            holder.getPage() - 1, holder.getQuery());
                }
            }
            case LogMenu.SLOT_NEXT -> {
                if (holder.getPage() < holder.getPageCount() - 1) {
                    viewer.openLog(player, holder.getOwner(), holder.getOwnerName(),
                            holder.getPage() + 1, holder.getQuery());
                }
            }
            case LogMenu.SLOT_SEARCH ->
                    viewer.openSearchDialog(player, holder.getOwner(), holder.getOwnerName(), holder.getQuery());
            case LogMenu.SLOT_CLEAR -> {
                if (holder.getQuery() != null) {
                    viewer.openLog(player, holder.getOwner(), holder.getOwnerName(), 0, null);
                }
            }
            default -> { /* filler / info — nothing to do */ }
        }
    }

    // ---- snapshot sandbox (dupe-proof) ----

    /**
     * Allows only moves that stay inside the snapshot inventory: the click must land in the top inventory
     * and be one of the {@link #SANDBOX_SAFE} actions (a plain pickup/place/swap). Shift-click, hotbar
     * swaps, double-click collect, drops and any click on the player's own inventory are cancelled, so no
     * snapshot item can reach the player or the world.
     */
    private void guardSnapshotClick(InventoryClickEvent event, Inventory top) {
        if (event.getClickedInventory() != top || !SANDBOX_SAFE.contains(event.getAction())) {
            event.setCancelled(true);
        }
    }

    /** Admins with this permission may take / drop items out of a log snapshot to give lost items back. */
    public static final String TAKE_PERMISSION = "enhancedechest.admin.log.take";

    private static boolean canTake(org.bukkit.entity.HumanEntity who) {
        return who.hasPermission(TAKE_PERMISSION);
    }

    /** Console line for every item that leaves a snapshot, so restores can be audited. */
    private void auditTake(InventoryClickEvent event, Inventory top, LogSnapshotHolder holder) {
        org.bukkit.inventory.ItemStack item = null;
        String how = null;
        switch (event.getAction()) {
            case DROP_ALL_SLOT, DROP_ONE_SLOT -> {
                if (event.getClickedInventory() == top) { item = event.getCurrentItem(); how = "dropped"; }
            }
            case MOVE_TO_OTHER_INVENTORY -> {
                if (event.getClickedInventory() == top) { item = event.getCurrentItem(); how = "took"; }
            }
            case HOTBAR_SWAP -> {
                if (event.getClickedInventory() == top) { item = event.getCurrentItem(); how = "took"; }
            }
            case DROP_ALL_CURSOR, DROP_ONE_CURSOR -> { item = event.getCursor(); how = "dropped"; }
            case PLACE_ALL, PLACE_ONE, PLACE_SOME, SWAP_WITH_CURSOR -> {
                if (event.getClickedInventory() != top) { item = event.getCursor(); how = "took"; }
            }
            default -> { }
        }
        if (item == null || item.isEmpty()) return;
        org.bukkit.Bukkit.getLogger().info("[EnhancedEchest] " + event.getWhoClicked().getName() + " " + how + " "
                + item.getAmount() + "x " + item.getType() + " from log snapshot of " + holder.getOwnerName()
                + " #" + holder.getIndex());
    }

    /** A drag is allowed only if every affected slot is inside the snapshot inventory. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof LogMenuHolder) {
            event.setCancelled(true);
            return;
        }
        if (top.getHolder() instanceof LogSnapshotHolder) {
            if (canTake(event.getWhoClicked())) return;
            int topSize = top.getSize();
            if (event.getRawSlots().stream().anyMatch(raw -> raw >= topSize)) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Discards the preview on close and returns to the log. The inventory itself is a throwaway (never
     * saved); the only thing that could leak a historical item is one left on the cursor, so clear it.
     * Then re-open the log at the page the admin came from — {@code openLog} does its work asynchronously,
     * so the actual {@code openInventory} lands a tick later, safely outside this close event.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof LogSnapshotHolder holder)) return;
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!player.getItemOnCursor().isEmpty() && !canTake(player)) {
            player.setItemOnCursor(null);
        }
        viewer.openLog(player, holder.getOwner(), holder.getOwnerName(), holder.getPage(), holder.getQuery());
    }
}
