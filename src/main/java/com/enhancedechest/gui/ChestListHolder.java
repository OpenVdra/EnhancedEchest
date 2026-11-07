package com.enhancedechest.gui;

import com.enhancedechest.model.ChestSummary;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Marker holder for one page of the simple {@code /eclist} inventory menu — the alternative to the Dialog-API
 * list, selected with {@code enderchest.chest-list-menu: inventory}. Unlike {@link EnderChestHolder} this backs a
 * read-only chooser, not a storage chest: clicking a chest icon opens that chest and every other
 * interaction is cancelled, so the display items can neither be taken nor items dumped into the empty
 * cells. Listeners identify it with {@code inventory.getHolder() instanceof ChestListHolder}.
 */
@Getter
public final class ChestListHolder implements InventoryHolder {

    private final UUID owner;

    /**
     * Location of the ender chest block if the menu was opened by right-clicking a block (threaded through
     * to the chest open so its lid still animates), or null when opened via the {@code /ec}/{@code /eclist}
     * command.
     */
    @Nullable
    private final Location sourceBlock;

    /** Menu slot &rarr; 1-based chest index of the chest whose icon sits in that slot. */
    private final Map<Integer, Integer> slotIndex;

    /** Page-control slot &rarr; 0-based page that arrow turns to (only the arrows shown on this page). */
    private final Map<Integer, Integer> navSlots;

    /** Every chest in display order — the snapshot page turns re-render from, so they need no DB query. */
    private final List<ChestSummary> chests;

    /** 0-based page this inventory shows. */
    private final int page;

    public ChestListHolder(UUID owner, @Nullable Location sourceBlock, Map<Integer, Integer> slotIndex,
                           Map<Integer, Integer> navSlots, List<ChestSummary> chests, int page) {
        this.owner = owner;
        this.sourceBlock = sourceBlock;
        this.slotIndex = slotIndex;
        this.navSlots = navSlots;
        this.chests = chests;
        this.page = page;
    }

    /** 0-based page a clicked slot turns to, or null if that slot is not a page arrow. */
    @Nullable
    public Integer navTargetAt(int slot) {
        return navSlots.get(slot);
    }

    /** 1-based chest index for a clicked menu slot, or null if that slot holds no chest icon. */
    @Nullable
    public Integer chestIndexAt(int slot) {
        return slotIndex.get(slot);
    }

    @Override
    public @NotNull Inventory getInventory() {
        throw new UnsupportedOperationException("ChestListHolder does not hold an Inventory reference");
    }
}
