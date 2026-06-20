package com.enhancedechest.listener;

import com.enhancedechest.gui.EnderChestAnimator;
import com.enhancedechest.gui.EnderChestService;
import com.enhancedechest.lang.LanguageManager;
import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
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
    private final LanguageManager lang;

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        var block = event.getClickedBlock();
        if (block == null || block.getType() != Material.ENDER_CHEST) return;

        event.setCancelled(true);

        Player player = event.getPlayer();
        if (!player.hasPermission("ee.use")) {
            player.sendMessage(lang.get("chest.no-permission"));
            return;
        }

        Location blockLoc = block.getLocation();

        // We are on the block's region thread (Folia) or main thread (Spigot/Paper),
        // which is the correct thread to call startOpen on the block entity.
        EnderChestAnimator.open(player, blockLoc);

        service.open(player, blockLoc);
    }
}
