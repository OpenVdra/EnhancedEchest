package com.enhancedechest.listener;

import com.enhancedechest.config.PluginConfig;
import com.enhancedechest.gui.EnderChestHolder;
import com.enhancedechest.lang.LanguageManager;
import com.enhancedechest.model.ChestKind;
import com.enhancedechest.scheduler.Scheduler;
import com.enhancedechest.service.ChestSessionManager;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.sound.Sound;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public final class EnderChestGuiListener implements Listener {

    /** Permission an admin needs to <i>modify</i> (not just view) another player's chest. */
    private static final String ADMIN_EDIT_PERMISSION = "enhancedechest.admin.edit";

    /** Minimum gap between deny sounds per player, so spam-clicking can't machine-gun the sound. */
    private static final long DENY_SOUND_COOLDOWN_MILLIS = 350L;

    private final ChestSessionManager sessions;
    @SuppressWarnings("unused")
    private final Scheduler scheduler;
    private final LanguageManager lang;
    private final PluginConfig config;

    /**
     * Last time (ms) each player heard the deny sound; cleared on chest close, and on quit as a backstop
     * (see {@link #onQuit}) so a player who disconnects without a close event (e.g. a forced disconnect,
     * mirroring the edge case {@link PlayerQuitListener} exists for) doesn't leak an entry here forever.
     */
    private final Map<UUID, Long> lastDenySoundAt = new ConcurrentHashMap<>();

    // Wiring note: 'scheduler' is still injected by the plugin and retained so the constructor
    // signature stays stable; 'sessions' is used by onClose and the two touched-marking handlers.

    /**
     * Guards item moves on an open chest GUI:
     * <ol>
     *   <li><b>Read-only viewers</b> — an admin viewing someone else's chest without the edit permission
     *       — cannot change the shared contents at all; any action touching the top inventory is cancelled
     *       (they still see live updates).</li>
     *   <li><b>Temporary chests</b> are take-only for everyone: deposits into the top inventory are
     *       cancelled, take-outs left untouched.</li>
     * </ol>
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof EnderChestHolder holder)) return;
        HumanEntity who = event.getWhoClicked();

        if (isReadOnlyViewer(who, holder)) {
            if (touchesTop(event)) {
                event.setCancelled(true);
                notifyViewOnly(who);
            }
            return;
        }

        if (holder.getKind() != ChestKind.TEMP) return;
        if (isDeposit(event, event.getView().getTopInventory())) {
            event.setCancelled(true);
            notifyTakeOnly(who);
        }
    }

    /**
     * Records that the shared chest was actually edited, for the activity log. Runs at
     * {@code MONITOR} with {@code ignoreCancelled}, so it sees only clicks that survived every handler
     * above (including this class's own read-only and take-only cancellations) and therefore really do
     * reach the chest. Flagging here rather than diffing later lets the log skip building a snapshot at
     * all for the many visits where a player just looks at their chest and closes it.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClickMarkTouched(InventoryClickEvent event) {
        if (!sessions.isActivityRecording()) return;   // one volatile read when the log is off
        if (!(event.getView().getTopInventory().getHolder() instanceof EnderChestHolder holder)) return;
        if (touchesTop(event)) sessions.markTouched(holder.getOwner(), holder.getIndex());
    }

    /** Same as {@link #onClickMarkTouched} for a drag that spreads items into the chest. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDragMarkTouched(InventoryDragEvent event) {
        if (!sessions.isActivityRecording()) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof EnderChestHolder holder)) return;
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(rawSlot -> rawSlot < topSize)) {
            sessions.markTouched(holder.getOwner(), holder.getIndex());
        }
    }

    /** True if the click's action would modify the top (shared chest) inventory. */
    private static boolean touchesTop(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        Inventory clicked = event.getClickedInventory();
        return switch (event.getAction()) {
            case NOTHING -> false;
            // Shift-click and double-click-collect cross the boundary regardless of which side was clicked.
            case MOVE_TO_OTHER_INVENTORY, COLLECT_TO_CURSOR -> true;
            // All other actions only affect the top if the click landed inside it.
            default -> clicked != null && clicked.equals(top);
        };
    }

    private static boolean isDeposit(InventoryClickEvent event, Inventory top) {
        Inventory clicked = event.getClickedInventory();
        return switch (event.getAction()) {
            // Cursor → a clicked top slot.
            case PLACE_ALL, PLACE_SOME, PLACE_ONE, SWAP_WITH_CURSOR,
                 HOTBAR_SWAP -> clicked != null && clicked.equals(top);
            // Shift-click from the player inventory moves the stack into the temp chest.
            case MOVE_TO_OTHER_INVENTORY -> clicked != null && !clicked.equals(top);
            default -> false;
        };
    }

    /**
     * Cancels any drag that would spread items into the top inventory slots — rejected outright for a
     * read-only viewer, and for everyone on a temporary (take-only) chest.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof EnderChestHolder holder)) return;

        int topSize = event.getView().getTopInventory().getSize();
        boolean intoTop = event.getRawSlots().stream().anyMatch(rawSlot -> rawSlot < topSize);
        if (!intoTop) return; // a drag confined to the player inventory is always fine

        HumanEntity who = event.getWhoClicked();
        if (isReadOnlyViewer(who, holder)) {
            event.setCancelled(true);
            notifyViewOnly(who);
            return;
        }
        if (holder.getKind() == ChestKind.TEMP) {
            event.setCancelled(true);
            notifyTakeOnly(who);
        }
    }

    /** A non-owner viewing the chest without the edit permission may look but not touch. */
    private static boolean isReadOnlyViewer(HumanEntity who, EnderChestHolder holder) {
        return !who.getUniqueId().equals(holder.getOwner())
                && !who.hasPermission(ADMIN_EDIT_PERMISSION);
    }

    private void notifyViewOnly(HumanEntity who) {
        if (who instanceof Player p) {
            p.sendActionBar(lang.get("chest.view-only"));
        }
    }

    private void notifyTakeOnly(HumanEntity who) {
        if (who instanceof Player p) {
            p.sendActionBar(lang.get("chest.temp-take-only"));
            Sound sound = config.getTempDenySound();
            if (sound != null && notOnSoundCooldown(p)) {
                p.playSound(sound);
            }
        }
    }

    /** True if enough time has passed since this player last heard the deny sound (and records now). */
    private boolean notOnSoundCooldown(Player p) {
        long now = System.currentTimeMillis();
        Long last = lastDenySoundAt.get(p.getUniqueId());
        if (last != null && now - last < DENY_SOUND_COOLDOWN_MILLIS) {
            return false;
        }
        lastDenySoundAt.put(p.getUniqueId(), now);
        return true;
    }

    /**
     * Detaches the closing player from the shared session on every close, regardless of reason. The
     * service removes them as a viewer and, only when the <i>last</i> viewer leaves, persists the shared
     * contents — so concurrent viewers keep editing the one live inventory with no premature save and no
     * dupe. The close (lid) animation is played from {@code detach} using the per-viewer source block.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof EnderChestHolder ecHolder)) return;

        lastDenySoundAt.remove(event.getPlayer().getUniqueId());
        sessions.detach((Player) event.getPlayer(), ecHolder);
    }

    /**
     * Backstop cleanup for {@link #lastDenySoundAt}: unconditional, since a quitting player might not
     * have had a chest open (nothing to do) or might have closed without firing {@link #onClose} (the
     * same edge case {@link PlayerQuitListener} backstops for sessions). Idempotent — a no-op if the
     * entry is already gone.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        lastDenySoundAt.remove(event.getPlayer().getUniqueId());
    }
}
