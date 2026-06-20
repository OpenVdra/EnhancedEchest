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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Backstop save when the player disconnects while the custom GUI is open.
 *
 * In most cases, Paper fires InventoryCloseEvent before PlayerQuitEvent, so
 * EnderChestGuiListener already saved. This listener is a safety net for edge
 * cases (e.g., server-side forced disconnects) where close may not have fired.
 * The save is idempotent — writing the same bytes twice causes no harm.
 */
@RequiredArgsConstructor
public final class PlayerQuitListener implements Listener {

    private final EnderChestService service;
    private final FoliaLib foliaLib;

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Inventory top = player.getOpenInventory().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof EnderChestHolder ecHolder)) return;

        service.save(ecHolder, top);

        // Close animation: dispatch to block's region thread.
        // The player entity is still valid during this event, so the NMS handle
        // reference captured in the lambda is safe to use in the scheduled task.
        Location sourceBlock = ecHolder.getSourceBlock();
        if (sourceBlock != null) {
            foliaLib.getScheduler().runAtLocation(sourceBlock, task ->
                    EnderChestAnimator.close(player, sourceBlock));
        }
    }
}
