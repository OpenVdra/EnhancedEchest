package com.enhancedechest.listener;

import com.enhancedechest.config.PluginConfig;
import com.enhancedechest.lang.LanguageManager;
import com.enhancedechest.model.ChestKind;
import com.enhancedechest.model.ChestSummary;
import com.enhancedechest.scheduler.Scheduler;
import com.enhancedechest.service.DbExecutor;
import com.enhancedechest.storage.EnderChestStorage;
import com.enhancedechest.telemetry.Telemetry;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.minimessage.translation.Argument;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.slf4j.Logger;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Reminds a player on join, when they still have items sitting in one or more temporary chests, to
 * collect them before those chests expire (config {@code temp-enderchest.join-notify}, default on). The
 * reminder is sent as a chat message <b>and</b> an action bar at once, optionally with a sound.
 *
 * <p>The chest scan runs on the {@link DbExecutor} — never on the join thread — mirroring
 * {@link JoinMigrationListener}: on a server where nobody has a pending temp chest, a join costs zero
 * main-thread DB time even during a mass reconnect. When there is something to say, the actual send hops
 * to the player's own region thread ({@link Scheduler#runAtEntityLater}, Folia-safe) after a short delay
 * so the messages aren't lost in the join sequence, the same approach as {@link com.enhancedechest.update.UpdateNotifyListener}.
 */
@RequiredArgsConstructor
public final class TempChestJoinNotifyListener implements Listener {

    /** Delay before sending, so the player is fully loaded and the reminder isn't swallowed on join. */
    private static final long SEND_DELAY_SECONDS = 2L;

    private final PluginConfig config;
    private final LanguageManager lang;
    private final EnderChestStorage storage;
    private final DbExecutor db;
    private final Scheduler scheduler;
    private final Logger logger;
    private final Telemetry telemetry;

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!config.isTempJoinNotifyEnabled()) return;
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        db.supply(() -> scan(storage.listChests(uuid)))
                .thenAccept(result -> {
                    if (result == null) return; // no temp chests waiting
                    scheduler.runAtEntityLater(player, () -> send(player, result),
                            SEND_DELAY_SECONDS, TimeUnit.SECONDS);
                })
                .exceptionally(e -> {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    logger.error("Temp-chest join reminder failed for {}", player.getName(), cause);
                    telemetry.error(cause, "temp.join-notify");
                    return null;
                });
    }

    /**
     * Counts the player's temporary chests and finds the soonest expiry among them. Returns {@code null}
     * when the player has none (nothing to remind about).
     */
    private static Scan scan(List<ChestSummary> chests) {
        int count = 0;
        long soonestExpiresAt = Long.MAX_VALUE;
        for (ChestSummary chest : chests) {
            if (chest.kind() != ChestKind.TEMP) continue;
            count++;
            Long expiresAt = chest.expiresAt();
            if (expiresAt != null && expiresAt < soonestExpiresAt) {
                soonestExpiresAt = expiresAt;
            }
        }
        return count == 0 ? null : new Scan(count, soonestExpiresAt);
    }

    private void send(Player player, Scan scan) {
        if (!player.isOnline()) return;

        long remaining = scan.soonestExpiresAt == Long.MAX_VALUE
                ? 0L
                : Math.max(0L, scan.soonestExpiresAt - System.currentTimeMillis());
        String count = Integer.toString(scan.count);

        // The remaining time is a per-viewer duration Component, so both surfaces go through the
        // argument-bearing lookups (a plain-string get() would flatten it to a raw key).
        player.sendMessage(lang.getArgs("chest.temp-join-chat",
                Argument.string("count", count),
                Argument.component("time", lang.duration(remaining))));
        player.sendActionBar(lang.getArgs("chest.temp-join-actionbar",
                Argument.string("count", count),
                Argument.component("time", lang.duration(remaining))));

        Sound sound = config.getTempJoinNotifySound();
        if (sound != null) {
            player.playSound(sound);
        }
    }

    /** Result of {@link #scan}: how many temp chests, and when the soonest expires (epoch millis). */
    private record Scan(int count, long soonestExpiresAt) {}
}
