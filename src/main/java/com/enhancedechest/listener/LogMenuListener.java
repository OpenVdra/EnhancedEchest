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
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.Set;

/**
 * Drives the inventories the {@code /ee log} feature opens:
 *
 * <ul>
 *   <li>the <b>log viewer</b> ({@link LogMenuHolder}) — a read-only paged list: every click is
 *       cancelled, a click on an event pane opens that snapshot, and the newer/older controls page;</li>
 *   <li>the <b>snapshot preview</b> ({@link LogSnapshotHolder}) — a dupe-proof sandbox: the admin may
 *       rearrange items <i>within</i> the preview to inspect them, but no path can move an item into
 *       their own inventory or the world, and the cursor is cleared on close.</li>
 *   <li>the <b>take-out preview</b> (same holder, {@code LogSnapshotHolder.isCanTake()}, a Lombok getter javadoc cannot
 *       link to) — for admins
 *       with {@code enhancedechest.admin.log.take}: the inverse rule, items may only <i>leave</i> the
 *       preview (cursor, own inventory, ground), never enter it, since the preview is discarded on close
 *       and anything placed in it would be destroyed. What was taken is written to the server log.</li>
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

    /** Take-mode actions on the preview's own slots that only move items <i>out</i> of it. */
    private static final Set<InventoryAction> TAKE_OUT = Set.of(
            InventoryAction.PICKUP_ALL, InventoryAction.PICKUP_HALF,
            InventoryAction.PICKUP_SOME, InventoryAction.PICKUP_ONE,
            InventoryAction.MOVE_TO_OTHER_INVENTORY, InventoryAction.COLLECT_TO_CURSOR,
            InventoryAction.DROP_ALL_SLOT, InventoryAction.DROP_ONE_SLOT, InventoryAction.NOTHING);

    private final LogViewer viewer;
    private final Logger logger;

    // ---- log viewer (read-only list) ----

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof LogMenuHolder holder) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) return;
            if (event.getClickedInventory() != top) return;   // a click in the player's own inv, already cancelled
            handleMenuClick(player, holder, event.getSlot());
            return;
        }
        if (top.getHolder() instanceof LogSnapshotHolder holder) {
            if (holder.isCanTake()) {
                guardTakeClick(event, top);
            } else {
                guardSnapshotClick(event, top);
            }
        }
    }

    private void handleMenuClick(Player player, LogMenuHolder holder, int slot) {
        LogEntry entry = holder.entryAt(slot);
        if (entry != null) {
            viewer.openSnapshot(player, holder.getOwner(), holder.getOwnerName(), entry,
                    holder.getPage(), holder.getQuery());
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

    /**
     * Take-out mode: nothing may enter the preview. On the preview's slots only {@link #TAKE_OUT} actions
     * pass (a hotbar/off-hand swap only when the target slot is empty, so it is a pure take); on the
     * player's own inventory everything passes except shift-click, which would push into the preview.
     * Clicks outside the window (throwing the cursor item) pass. Creative middle-click clone and any
     * placing/swapping onto a preview slot are cancelled.
     */
    private void guardTakeClick(InventoryClickEvent event, Inventory top) {
        Inventory clicked = event.getClickedInventory();
        if (clicked == null) return;   // outside the window: drops the cursor item
        InventoryAction action = event.getAction();
        if (clicked != top) {
            if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY || action == InventoryAction.UNKNOWN) {
                event.setCancelled(true);
            }
            return;
        }
        if (action == InventoryAction.HOTBAR_SWAP) {
            if (!(event.getWhoClicked() instanceof Player player) || !swapTargetEmpty(player, event)) {
                event.setCancelled(true);
            }
            return;
        }
        if (!TAKE_OUT.contains(action)) {
            event.setCancelled(true);
        }
    }

    /** True when the hotbar slot (or off-hand, for F) a swap would move into the preview is empty. */
    private static boolean swapTargetEmpty(Player player, InventoryClickEvent event) {
        ItemStack target = event.getClick() == ClickType.SWAP_OFFHAND
                ? player.getInventory().getItemInOffHand()
                : event.getHotbarButton() >= 0 ? player.getInventory().getItem(event.getHotbarButton()) : null;
        return target == null || target.isEmpty();
    }

    /**
     * Sandbox: a drag is allowed only if every affected slot is inside the snapshot inventory. Take-out:
     * the inverse — a drag may not touch any preview slot, since that would place items into it.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof LogMenuHolder) {
            event.setCancelled(true);
            return;
        }
        if (top.getHolder() instanceof LogSnapshotHolder holder) {
            int topSize = top.getSize();
            boolean blocked = holder.isCanTake()
                    ? event.getRawSlots().stream().anyMatch(raw -> raw < topSize)
                    : event.getRawSlots().stream().anyMatch(raw -> raw >= topSize);
            if (blocked) event.setCancelled(true);
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
        if (holder.isCanTake()) {
            // Taken items are the admin's now: leave the cursor for the server to return as usual.
            logTaken(player, holder, event.getView().getTopInventory());
        } else if (!player.getItemOnCursor().isEmpty()) {
            player.setItemOnCursor(null);
        }
        viewer.openLog(player, holder.getOwner(), holder.getOwnerName(), holder.getPage(), holder.getQuery());
    }

    /**
     * Audit line for a take-out preview. Nothing can enter the preview in take mode, so whatever is
     * missing from it on close is exactly what the admin took (into their inventory or onto the ground).
     * Restored items are created from history, so this line is the only record of where they came from.
     */
    private void logTaken(Player admin, LogSnapshotHolder holder, Inventory top) {
        Map<ItemStack, Integer> missing = new LinkedHashMap<>();
        for (ItemStack stack : holder.getOriginal()) {
            if (stack != null && !stack.isEmpty()) missing.merge(stack.asOne(), stack.getAmount(), Integer::sum);
        }
        for (ItemStack stack : top.getContents()) {
            if (stack != null && !stack.isEmpty()) missing.computeIfPresent(stack.asOne(),
                    (k, n) -> n - stack.getAmount() > 0 ? n - stack.getAmount() : null);
        }
        if (missing.isEmpty()) return;
        StringJoiner taken = new StringJoiner(", ");
        missing.forEach((item, amount) -> taken.add(amount + "x " + item.getType().getKey().getKey()));
        logger.info("{} took items from {}'s chest log (chest #{}, visit {}): {}",
                admin.getName(), holder.getOwnerName(), holder.getIndex(), holder.getEntryId(), taken);
    }
}
