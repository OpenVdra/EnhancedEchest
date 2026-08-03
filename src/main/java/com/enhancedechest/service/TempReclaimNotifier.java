package com.enhancedechest.service;

import com.enhancedechest.config.PluginConfig;
import com.enhancedechest.lang.LanguageManager;
import com.enhancedechest.scheduler.Scheduler;
import com.enhancedechest.telemetry.Telemetry;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.minimessage.translation.Argument;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.slf4j.Logger;

import java.util.UUID;

/**
 * Tells a player where their temporary-chest items went when
 * {@link ChestSpillService#reclaimTempInto} moved them into a newly granted chest
 * ({@code temp-enderchest.reclaim-notify}, default on). Chat and action bar at once, optionally with a
 * sound — the same shape as the join reminder, because it answers the same question from the other end:
 * the join reminder says items are waiting, this says they have been put away.
 *
 * <p>Hooked into the spill service rather than its callers, so every way of granting a chest (a
 * permission reconcile, {@code /ee add}) reports the move without having to remember to.
 *
 * <p>An offline owner is simply skipped. The reclaim already happened and is durable, so there is
 * nothing to retry: the items are sitting in a normal chest the player will find on their own.
 */
public final class TempReclaimNotifier {

    private final PluginConfig config;
    private final LanguageManager lang;
    private final Scheduler scheduler;
    private final Logger logger;
    private final Telemetry telemetry;

    public TempReclaimNotifier(PluginConfig config, LanguageManager lang, Scheduler scheduler,
                               Logger logger, Telemetry telemetry) {
        this.config    = config;
        this.lang      = lang;
        this.scheduler = scheduler;
        this.logger    = logger;
        this.telemetry = telemetry;
    }

    /**
     * Called from the DB executor the moment a reclaim reports success. Never throws: this sits on the
     * tail of the reclaim future, and a failure to say something must not fail the move that already
     * happened.
     */
    public void notifyReclaimed(UUID owner, int targetIndex) {
        if (!config.isTempReclaimNotifyEnabled()) return;
        try {
            Player player = Bukkit.getPlayer(owner);
            if (player == null || !player.isOnline()) return;
            scheduler.runAtEntity(player, task -> send(player, targetIndex));
        } catch (RuntimeException e) {
            logger.warn("Could not announce a temp-chest reclaim to {}: {}", owner, e.getMessage());
            telemetry.error(e, "temp.reclaim-notify");
        }
    }

    private void send(Player player, int targetIndex) {
        if (!player.isOnline()) return;
        String chest = Integer.toString(targetIndex);
        player.sendMessage(lang.getArgs("chest.temp-reclaim-chat", Argument.string("chest", chest)));
        player.sendActionBar(lang.getArgs("chest.temp-reclaim-actionbar", Argument.string("chest", chest)));

        Sound sound = config.getTempReclaimNotifySound();
        if (sound != null) {
            player.playSound(sound);
        }
    }
}
