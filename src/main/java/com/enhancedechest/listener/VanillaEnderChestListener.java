package com.enhancedechest.listener;

import com.enhancedechest.gui.EnderChestService;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

@RequiredArgsConstructor
public final class VanillaEnderChestListener implements Listener {

    private final EnderChestService service;

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        var block = event.getClickedBlock();
        if (block == null || block.getType() != Material.ENDER_CHEST) return;

        // Block the vanilla enderchest GUI for all players regardless of permission.
        // Players without ee.use see nothing (vanilla access fully removed by this plugin).
        event.setCancelled(true);

        Player player = event.getPlayer();
        if (!player.hasPermission("ee.use")) {
            player.sendMessage(Component.text("[EnhancedEChest] You don't have permission to use the enderchest."));
            return;
        }

        service.open(player);
    }
}
