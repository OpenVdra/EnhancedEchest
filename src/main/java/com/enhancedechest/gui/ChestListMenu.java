package com.enhancedechest.gui;

import com.enhancedechest.gui.dialog.IconCatalog;
import com.enhancedechest.lang.LanguageManager;
import com.enhancedechest.model.ChestKind;
import com.enhancedechest.model.ChestSummary;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.translation.Argument;
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
 * list, chosen with {@code enderchest.chest-list-menu: inventory}. It is a bare chooser: one icon per owned
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
 * A player with more than {@link #PAGE_SIZE} chests gets paged 54-slot menus instead: the same 4 &times; 7
 * icon area per page, with previous / next arrows and a page indicator in the bottom border row. Page
 * turns re-render from the ordered snapshot kept on {@link ChestListHolder} rather than re-querying
 * storage; that is safe because clicking an icon still goes through {@code ChestOpener.openChest}, which
 * re-validates the chest against storage.
 *
 * <p>Chest text reuses the {@code gui.yml} dialog keys (title, slot count, expiry, main tag); only the page
 * controls have keys of their own ({@code dialog.list-prev-page} / {@code list-next-page} /
 * {@code list-page} / {@code list-total}).
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
     * Chests per page: the 54-slot menu's 4 inner rows &times; 7 columns = 28. Up to this many chests fit
     * one page, laid out exactly as before pagination existed (sized to the count, no controls).
     */
    public static final int PAGE_SIZE = (MAX_ROWS - 2) * INNER_COLUMNS; // 28

    /** Bottom-row control slots of a paged (54-slot) menu: previous · page info · next, centred. */
    private static final int SLOT_PREV = 48;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_NEXT = 50;

    private final LanguageManager lang;

    public ChestListMenu(LanguageManager lang) {
        this.lang = lang;
    }

    /**
     * Display order: temp (expiring) chests first — same order as the dialog list — then natural index
     * order. The holder keeps this ordered snapshot so page turns re-render without re-sorting.
     */
    public static List<ChestSummary> order(List<ChestSummary> chests) {
        List<ChestSummary> ordered = new ArrayList<>(chests);
        ordered.sort(Comparator
                .comparingInt((ChestSummary c) -> c.kind() == ChestKind.TEMP ? 0 : 1)
                .thenComparingInt(ChestSummary::index));
        return List.copyOf(ordered);
    }

    /** First page of the menu for an unordered chest list; see {@link #buildPage}. */
    public Inventory build(java.util.Locale locale, List<ChestSummary> chests, UUID owner, @Nullable Location sourceBlock) {
        return buildPage(locale, order(chests), owner, sourceBlock, 0);
    }

    /**
     * Builds one page of the menu for {@code owner}'s chests. A single page (≤ {@link #PAGE_SIZE} chests)
     * is sized to the chest count and has no controls; more chests than that switch to fixed 54-slot pages
     * with previous / next arrows (each shown only when that page exists) and a page indicator in the
     * bottom border row. {@code page} is clamped so an out-of-range request still lands on a real page.
     *
     * @param ordered     chests already in display order ({@link #order})
     * @param page        0-based page to show
     * @param sourceBlock ender chest block the menu was opened from (for the lid animation on open), or null
     */
    public Inventory buildPage(java.util.Locale locale, List<ChestSummary> ordered, UUID owner,
                               @Nullable Location sourceBlock, int page) {
        int pageCount = Math.max(1, (ordered.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, pageCount - 1));
        boolean paged = pageCount > 1;

        int from = page * PAGE_SIZE;
        List<ChestSummary> pageChests = ordered.subList(from, Math.min(ordered.size(), from + PAGE_SIZE));

        int rows = paged ? MAX_ROWS : rowsFor(pageChests.size());
        int[] slots = contentSlots(rows);

        Map<Integer, Integer> slotIndex = new HashMap<>();
        Map<Integer, Integer> navSlots = new HashMap<>();
        ChestListHolder holder = new ChestListHolder(owner, sourceBlock, slotIndex, navSlots, ordered, page);
        Inventory inv = Bukkit.createInventory(holder, rows * COLUMNS, lang.getGui(locale, "dialog.list-title"));

        for (int i = 0; i < pageChests.size(); i++) {
            ChestSummary chest = pageChests.get(i);
            int slot = slots[i];
            inv.setItem(slot, iconFor(locale, chest));
            slotIndex.put(slot, chest.index());
        }

        if (paged) {
            if (page > 0) {
                inv.setItem(SLOT_PREV, nav(locale, "dialog.list-prev-page"));
                navSlots.put(SLOT_PREV, page - 1);
            }
            if (page < pageCount - 1) {
                inv.setItem(SLOT_NEXT, nav(locale, "dialog.list-next-page"));
                navSlots.put(SLOT_NEXT, page + 1);
            }
            inv.setItem(SLOT_INFO, pageInfo(locale, page, pageCount, ordered.size()));
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

    /** A page arrow, named from gui.yml. */
    private ItemStack nav(java.util.Locale locale, String key) {
        ItemStack item = ItemStack.of(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(noItalic(lang.getGui(locale, key)));
        item.setItemMeta(meta);
        return item;
    }

    /** The page indicator between the arrows: "Page x/y" plus the total chest count. */
    private ItemStack pageInfo(java.util.Locale locale, int page, int pageCount, int total) {
        ItemStack item = ItemStack.of(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(noItalic(lang.getGui(locale, "dialog.list-page",
                "page", Integer.toString(page + 1),
                "pages", Integer.toString(pageCount))));
        meta.lore(List.of(loreLine(lang.getGui(locale, "dialog.list-total", "count", Integer.toString(total)))));
        item.setItemMeta(meta);
        return item;
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
            Component remaining = lang.duration(chest.expiresAt() - System.currentTimeMillis());
            lore.add(loreLine(lang.getGuiArgs(locale, "dialog.expires-in", Argument.component("time", remaining))));
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
