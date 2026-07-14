package com.enhancedechest.gui;

import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

/**
 * Marker holder for the simple 27-slot {@code /eclist} inventory menu — the alternative to the Dialog-API
 * list, selected with {@code enderchest.list-menu: inventory}. Unlike {@link EnderChestHolder} this backs a
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

    public ChestListHolder(UUID owner, @Nullable Location sourceBlock, Map<Integer, Integer> slotIndex) {
        this.owner = owner;
        this.sourceBlock = sourceBlock;
        this.slotIndex = slotIndex;
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
