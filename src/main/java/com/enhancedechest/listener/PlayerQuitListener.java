package com.enhancedechest.listener;

import com.enhancedechest.gui.EnderChestHolder;
import com.enhancedechest.gui.EnderChestService;
import lombok.RequiredArgsConstructor;
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

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Inventory top = event.getPlayer().getOpenInventory().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof EnderChestHolder ecHolder)) return;

        service.save(ecHolder, top);
    }
}
