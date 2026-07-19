package com.enhancedechest.listener;

import com.enhancedechest.config.PluginConfig;
import com.enhancedechest.service.ChestOpener;
import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

@RequiredArgsConstructor
public final class VanillaEnderChestListener implements Listener {

    private final ChestOpener opener;
    private final PluginConfig config;

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        // The event fires once per hand; without this filter a single right-click can start two
        // overlapping open flows for the same chest (see ChestSessionManager.decideOpen dedupe).
        if (event.getHand() != EquipmentSlot.HAND) return;

        var block = event.getClickedBlock();
        if (block == null || block.getType() != Material.ENDER_CHEST) return;

        event.setCancelled(true);

        // Right-clicking an ender chest block always opens the GUI — no permission required.
        // The open-by-command permission only gates /enderchest and /eclist.
        Player player = event.getPlayer();
        Location blockLoc = block.getLocation();

        // The lid animation is driven by the inventory open/close lifecycle (see ChestOpener /
        // EnderChestGuiListener), not eagerly here: a single chest opens straight to its inventory,
        // whereas several chests open a dialog first — and dialogs have no close event to pair an
        // eager open() with, which would otherwise leave the lid stuck open.
        // Shift + right-click is a shortcut to the chest list (same menu as /eclist), so a player with a
        // main chest set can still reach their other chests without typing a command. Gated by
        // enderchest.shift-click-list; when off, a sneaking click opens a chest like any other click.
        if (config.isShiftClickListEnabled() && player.isSneaking()) {
            opener.openListDialog(player, blockLoc);
            return;
        }

        opener.open(player, blockLoc);
    }
}
