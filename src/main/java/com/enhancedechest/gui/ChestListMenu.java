package com.enhancedechest.gui;

import com.enhancedechest.gui.dialog.IconCatalog;
import com.enhancedechest.lang.LanguageManager;
import com.enhancedechest.model.ChestKind;
import com.enhancedechest.model.ChestSummary;
import com.enhancedechest.util.DurationFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds the simple {@code /eclist} inventory menu — the alternative to {@code ChestDialogs}' Dialog-API
 * list, chosen with {@code enderchest.list-menu: inventory}. It is a bare chooser: one icon per owned
 * chest showing its name / slot count / expiry, and clicking an icon opens that chest directly. None of
 * the dialog's management actions (rename / set-main / icon / sort) exist here.
 *
 * <p><b>Layout.</b> Chest icons sit in an inner rectangle of {@link #INNER_COLUMNS} columns, framed by a
 * one-cell border of empty <i>padding</i> cells on every side (top/bottom rows, left/right columns) purely
 * for spacing. The menu grows a row at a time as the player owns more chests, so the icons stay centred
 * with even margins:
 * <ul>
 *   <li>up to 7 chests &rarr; 27 slots (3 rows, 1 inner row);</li>
 *   <li>up to 14 &rarr; 36 slots (4 rows, 2 inner rows);</li>
 *   <li>up to 21 &rarr; 45 slots (5 rows, 3 inner rows);</li>
 *   <li>up to 28 &rarr; 54 slots (6 rows, 4 inner rows) — the largest a chest inventory can be.</li>
 * </ul>
 * A player with more than {@link #MAX_CHESTS} chests can't fit even the 54-slot menu, so callers route
 * them back to the dialog list instead.
 *
 * <p>All text reuses the existing {@code gui.yml} dialog keys (title, slot count, expiry, main tag), so
 * this mode adds no new language strings.
 */
public final class ChestListMenu {

    /** Slots per inventory row. */
    private static final int COLUMNS = 9;

    /** Content columns per inner row (the 7 middle cells; columns 0 and 8 are left as padding). */
    private static final int INNER_COLUMNS = 7;

    /** Smallest menu: 3 rows (27 slots), one inner content row. */
    private static final int MIN_ROWS = 3;

    /** Largest menu: 6 rows (54 slots) — the maximum size of a chest inventory. */
    private static final int MAX_ROWS = 6;

    /**
     * Largest chest count the inventory chooser can show: the 54-slot menu's 4 inner rows &times; 7
     * columns = 28. Callers fall back to the dialog list above this (a 54-slot inventory can't hold more).
     */
    public static final int MAX_CHESTS = (MAX_ROWS - 2) * INNER_COLUMNS; // 28

    private final LanguageManager lang;

    public ChestListMenu(LanguageManager lang) {
        this.lang = lang;
    }

    /**
     * Builds the menu inventory for {@code owner}'s chests, sized to the chest count. Temp (expiring)
     * chests sort first — same order as the dialog list — then natural index order; only the first
     * {@link #MAX_CHESTS} are placed, so callers must gate on {@link #MAX_CHESTS} before choosing this menu
     * over the dialog.
     *
     * @param sourceBlock ender chest block the menu was opened from (for the lid animation on open), or null
     */
    public Inventory build(java.util.Locale locale, List<ChestSummary> chests, UUID owner, @Nullable Location sourceBlock) {
        List<ChestSummary> ordered = new ArrayList<>(chests);
        ordered.sort(Comparator
                .comparingInt((ChestSummary c) -> c.kind() == ChestKind.TEMP ? 0 : 1)
                .thenComparingInt(ChestSummary::index));

        int rows = rowsFor(ordered.size());
        int[] slots = contentSlots(rows);

        Map<Integer, Integer> slotIndex = new HashMap<>();
        ChestListHolder holder = new ChestListHolder(owner, sourceBlock, slotIndex);
        Inventory inv = Bukkit.createInventory(holder, rows * COLUMNS, lang.getGui(locale, "dialog.list-title"));

        int count = Math.min(ordered.size(), slots.length);
        for (int i = 0; i < count; i++) {
            ChestSummary chest = ordered.get(i);
            int slot = slots[i];
            inv.setItem(slot, iconFor(locale, chest));
            slotIndex.put(slot, chest.index());
        }
        return inv;
    }

    /** Smallest 3–6 row menu whose inner area holds every chest (capped at {@link #MAX_ROWS}). */
    private static int rowsFor(int chestCount) {
        for (int rows = MIN_ROWS; rows <= MAX_ROWS; rows++) {
            if (chestCount <= (rows - 2) * INNER_COLUMNS) {
                return rows;
            }
        }
        return MAX_ROWS;
    }

    /**
     * Content slots for an {@code rows}-row menu: the inner {@link #INNER_COLUMNS} columns of every
     * non-border row (rows {@code 1 .. rows-2}), left-to-right then top-to-bottom. The border rows/columns
     * are omitted, so every returned slot is a padded content cell.
     */
    private static int[] contentSlots(int rows) {
        int innerRows = rows - 2;
        int[] slots = new int[innerRows * INNER_COLUMNS];
        int idx = 0;
        for (int row = 1; row <= rows - 2; row++) {
            for (int col = 1; col <= INNER_COLUMNS; col++) {
                slots[idx++] = row * COLUMNS + col;
            }
        }
        return slots;
    }

    /** One chest's display item: its chosen icon (or an ender chest), named and described from gui.yml. */
    private ItemStack iconFor(java.util.Locale locale, ChestSummary chest) {
        ItemStack item = baseItem(chest);
        ItemMeta meta = item.getItemMeta();

        // Reuse the dialog's label (numbered / custom name / red temp title) and its gold main tag.
        Component name = lang.getChestLabel(locale, chest.index(), chest.customName(), chest.kind());
        if (chest.primary()) {
            name = name.append(Component.text(" ")).append(lang.getGui(locale, "dialog.main-tag"));
        }
        meta.displayName(noItalic(name));

        List<Component> lore = new ArrayList<>(2);
        lore.add(loreLine(lang.getGui(locale, "dialog.slots", "size", Integer.toString(chest.size()))));
        if (chest.expiresAt() != null) {
            String remaining = DurationFormat.formatRemaining(chest.expiresAt() - System.currentTimeMillis());
            lore.add(loreLine(lang.getGui(locale, "dialog.expires-in", "time", remaining)));
        }
        meta.lore(lore);

        item.setItemMeta(meta);
        return item;
    }

    /**
     * The chest's chosen icon as an item. Temp (overflow) chests always render as a copper chest so they
     * read as distinct from real chests. Otherwise the player-chosen icon wins; failing that, a chest that
     * carries an expiry (a time-limited normal chest granted with a duration) renders as a plain chest so
     * it reads as distinct from a permanent ender chest, and a non-expiring chest falls back to the ender
     * chest icon.
     */
    private static ItemStack baseItem(ChestSummary chest) {
        if (chest.kind() == ChestKind.TEMP) {
            return ItemStack.of(Material.COPPER_CHEST);
        }
        ItemStack icon = IconCatalog.item(chest.icon());
        if (icon != null) {
            return icon;
        }
        if (chest.expiresAt() != null) {
            return ItemStack.of(Material.CHEST);
        }
        return ItemStack.of(Material.ENDER_CHEST);
    }

    /**
     * Formats a lore line: clears the default italic styling and pins a neutral grey colour so the text
     * doesn't inherit Minecraft's default purple lore colour. {@code colorIfAbsent} leaves any explicit
     * colour from {@code gui.yml} untouched, so this only affects lines that set none.
     */
    private static Component loreLine(Component component) {
        return noItalic(component).colorIfAbsent(NamedTextColor.GRAY);
    }

    /** Clears the default italic styling Minecraft applies to item names/lore, so labels read cleanly. */
    private static Component noItalic(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }
}
