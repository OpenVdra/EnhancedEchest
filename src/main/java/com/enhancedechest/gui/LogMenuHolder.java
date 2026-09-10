package com.enhancedechest.gui;

import com.enhancedechest.log.LogEntry;
import lombok.Getter;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

/**
 * Marker holder for the {@code /ee log <player>} viewer: a read-only, paged list of a player's chest
 * OPEN/CLOSE events. Every interaction is cancelled by {@link com.enhancedechest.listener.LogMenuListener};
 * clicking an event pane opens that event's snapshot, and the bottom-row controls page through the log.
 *
 * <p>The holder carries the paging state and a slot → {@link LogEntry} map so the listener can act on a
 * click without re-querying: an entry slot fetches that snapshot, a control slot re-opens an adjacent page.
 */
@Getter
public final class LogMenuHolder implements InventoryHolder {

    private final UUID owner;
    private final String ownerName;
    /** 0-based page currently shown (page 0 is the most recent events). */
    private final int page;
    /** Total number of pages, so the listener knows whether an older/newer page exists. */
    private final int pageCount;
    /** Menu slot &rarr; the event whose pane sits there. */
    private final Map<Integer, LogEntry> slotEntries;

    public LogMenuHolder(UUID owner, String ownerName, int page, int pageCount,
                         Map<Integer, LogEntry> slotEntries) {
        this.owner = owner;
        this.ownerName = ownerName;
        this.page = page;
        this.pageCount = pageCount;
        this.slotEntries = slotEntries;
    }

    /** The event whose pane sits in {@code slot}, or {@code null} if that slot holds no event pane. */
    public @Nullable LogEntry entryAt(int slot) {
        return slotEntries.get(slot);
    }

    @Override
    public @NotNull Inventory getInventory() {
        throw new UnsupportedOperationException("LogMenuHolder does not hold an Inventory reference");
    }
}
