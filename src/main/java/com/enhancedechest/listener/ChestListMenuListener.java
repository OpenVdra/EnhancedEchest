package com.enhancedechest.listener;

import com.enhancedechest.gui.ChestListHolder;
import com.enhancedechest.service.ChestOpener;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

/**
 * Drives the simple 27-slot {@code /eclist} inventory menu ({@link ChestListHolder}). The menu is a
 * read-only chooser: every click and drag on it is cancelled so its display icons can never be taken and
 * items can never be dumped into the empty cells, and clicking a chest icon opens that chest — sharing the
 * live session through {@link ChestOpener#openChest} exactly like the dialog and right-click paths.
 */
@RequiredArgsConstructor
public final class ChestListMenuListener implements Listener {

    private final ChestOpener opener;

    /**
     * Cancels every click while the chooser is open (whichever inventory was clicked, so shift-click and
     * double-click-collect can't pull the player's own items into it either) and, when the click landed on
     * a chest icon in the menu itself, opens that chest.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof ChestListHolder holder)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        // Only a click inside the menu itself can select a chest (a click in the player's own inventory
        // has already been cancelled above and does nothing else).
        if (event.getClickedInventory() != top) return;
        Integer index = holder.chestIndexAt(event.getSlot());
        if (index != null) {
            opener.openChest(player, index, holder.getSourceBlock());
        }
    }

    /** Rejects any drag on the chooser — nothing may be placed into it. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ChestListHolder) {
            event.setCancelled(true);
        }
    }
}
