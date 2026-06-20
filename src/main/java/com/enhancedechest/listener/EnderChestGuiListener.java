package com.enhancedechest.listener;

import com.enhancedechest.gui.EnderChestAnimator;
import com.enhancedechest.gui.EnderChestHolder;
import com.enhancedechest.gui.EnderChestService;
import com.tcoded.folialib.FoliaLib;
import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

@RequiredArgsConstructor
public final class EnderChestGuiListener implements Listener {

    private final EnderChestService service;
    private final FoliaLib foliaLib;

    /**
     * Saves inventory contents to DB on every close, regardless of close reason.
     * This fires for normal closes, /ec reopens (reason OPEN_NEW), and forced closes
     * from server-side events. The DB write is always correct because:
     * - On reopen via /ec: save fires here first, then EnderChestService.open() waits
     *   for the pending save before loading the fresh snapshot. No stale state.
     * - On quit: PlayerQuitListener fires save independently; both are idempotent.
     *
     * Close animation: dispatched to the block's region thread via runAtLocation so
     * the NMS call is always on the correct thread (required for Folia).
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof EnderChestHolder ecHolder)) return;

        service.save(ecHolder, top);

        Location sourceBlock = ecHolder.getSourceBlock();
        if (sourceBlock != null) {
            Player player = (Player) event.getPlayer();
            foliaLib.getScheduler().runAtLocation(sourceBlock, task ->
                    EnderChestAnimator.close(player, sourceBlock));
        }
    }
}
