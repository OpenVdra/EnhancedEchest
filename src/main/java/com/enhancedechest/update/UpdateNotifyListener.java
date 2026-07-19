package com.enhancedechest.update;

import com.enhancedechest.lang.LanguageManager;
import com.enhancedechest.scheduler.Scheduler;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
public final class UpdateNotifyListener implements Listener {

    private final Scheduler scheduler;
    private final UpdateChecker checker;
    private final LanguageManager lang;

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!checker.isUpdateAvailable()) return;
        Player player = event.getPlayer();
        if (!player.isOp()) return;

        // Slight delay so the player is fully loaded before receiving messages.
        // runAtEntityLater targets the player's entity thread (safe for Folia),
        // 2 seconds ≈ 40 ticks at 20 TPS.
        scheduler.runAtEntityLater(player, () -> {
            if (!player.isOnline()) return;
            player.sendMessage(lang.get("update.available",
                    "current", checker.getCurrentVersion(),
                    "latest",  checker.getLatestVersion()));
            // The click event has to live on the component, not in the message string: a placeholder
            // inside a <click:...> attribute isn't substituted per-viewer. So we build the clickable
            // link here and pass it as the {url} argument (text position) of a localized message.
            //
            // The label is a translatable, not the address: a release URL is long enough to wrap over two
            // chat lines and is unreadable anyway. Chat is one of the two surfaces Paper runs the
            // GlobalTranslator over, so it still resolves in each viewer's own language. Hovering shows
            // where the click actually leads, so the destination is never hidden from the player.
            String url = checker.getDownloadUrl();
            Component link = lang.get("update.download-label")
                    .color(NamedTextColor.AQUA)
                    .decorate(TextDecoration.UNDERLINED)
                    .clickEvent(ClickEvent.openUrl(url))
                    .hoverEvent(HoverEvent.showText(Component.text(url)));
            player.sendMessage(lang.getRich("update.download", "url", link));
        }, 2L, TimeUnit.SECONDS);
    }
}
