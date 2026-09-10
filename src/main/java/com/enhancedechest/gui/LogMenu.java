package com.enhancedechest.gui;

import com.enhancedechest.lang.LanguageManager;
import com.enhancedechest.log.DiffLine;
import com.enhancedechest.log.LogAction;
import com.enhancedechest.log.LogEntry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Builds the {@code /ee log <player>} viewer: a 54-slot inventory whose top 45 slots hold one pane per
 * OPEN (lime) / CLOSE (red) event, newest first, and whose bottom row is navigation. A pane's name is
 * the action and time; its lore is who did it and — for a CLOSE — the material-level change summary
 * ({@code +} added to the chest, {@code −} taken out). Clicking a pane opens that event's snapshot.
 *
 * <p>All text is rendered eagerly with the viewer's {@link Locale} (the plugin's localisation invariant);
 * item names in the diff come from the client's own translations via {@link Component#translatable}.
 */
public final class LogMenu {

    /** Event panes per page — the top five rows of a double chest. */
    public static final int PAGE_SIZE = 45;

    private static final int SIZE = 54;
    private static final int CONTROL_ROW = 45;
    /** Bottom-row control slots the listener recognises: newer page, info, older page. */
    public static final int SLOT_PREV = 45;
    private static final int SLOT_INFO = 49;
    public static final int SLOT_NEXT = 53;

    /** Cap on diff lines shown in a pane's lore, so a bulk deposit can't produce an enormous tooltip. */
    private static final int MAX_DIFF_LINES = 10;

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final LanguageManager lang;

    public LogMenu(LanguageManager lang) {
        this.lang = lang;
    }

    /**
     * Builds one page of the log viewer.
     *
     * @param entries the page's events, newest first (at most {@link #PAGE_SIZE})
     * @param page      0-based page index (0 = most recent)
     * @param pageCount total pages
     */
    public Inventory build(Locale locale, UUID owner, String ownerName, List<LogEntry> entries,
                           int page, int pageCount) {
        Map<Integer, LogEntry> slotEntries = new HashMap<>();
        LogMenuHolder holder = new LogMenuHolder(owner, ownerName, page, pageCount, slotEntries);
        Component title = lang.getGui(locale, "log.title", "player", ownerName);
        Inventory inv = Bukkit.createInventory(holder, SIZE, title);

        int count = Math.min(entries.size(), PAGE_SIZE);
        for (int i = 0; i < count; i++) {
            LogEntry entry = entries.get(i);
            inv.setItem(i, paneFor(locale, entry));
            slotEntries.put(i, entry);
        }

        // Controls: newer/older arrows only when such a page exists; an info book in the middle; the rest
        // of the bottom row is inert filler so the player can't drop items into empty control cells.
        ItemStack filler = filler();
        for (int slot = CONTROL_ROW; slot < SIZE; slot++) inv.setItem(slot, filler);
        if (page > 0) {
            inv.setItem(SLOT_PREV, nav(locale, Material.SPECTRAL_ARROW, "log.nav-newer"));
        }
        if (page < pageCount - 1) {
            inv.setItem(SLOT_NEXT, nav(locale, Material.ARROW, "log.nav-older"));
        }
        inv.setItem(SLOT_INFO, info(locale, ownerName, page, pageCount));
        return inv;
    }

    private ItemStack paneFor(Locale locale, LogEntry entry) {
        boolean open = entry.action() == LogAction.OPEN;
        ItemStack item = new ItemStack(open ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();

        String time = TIME.format(Instant.ofEpochMilli(entry.ts()));
        meta.displayName(noItalic(lang.getGui(locale, open ? "log.pane-open" : "log.pane-close",
                "time", time)));

        List<Component> lore = new ArrayList<>();
        lore.add(line(lang.getGui(locale, "log.lore-actor",
                "actor", entry.actorName() != null ? entry.actorName() : "?")));
        lore.add(line(lang.getGui(locale, "log.lore-chest",
                "index", Integer.toString(entry.index()),
                "size", Integer.toString(entry.size()))));

        if (!open) {
            lore.add(Component.empty());
            if (entry.diff().isEmpty()) {
                lore.add(line(lang.getGui(locale, "log.lore-no-change")));
            } else {
                appendDiff(lore, entry.diff());
            }
        }

        lore.add(Component.empty());
        lore.add(line(lang.getGui(locale, "log.lore-click")));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** Renders the change summary: added lines green ({@code +n Item}), taken lines red ({@code −n Item}). */
    private static void appendDiff(List<Component> lore, List<DiffLine> diff) {
        int shown = Math.min(diff.size(), MAX_DIFF_LINES);
        for (int i = 0; i < shown; i++) {
            DiffLine d = diff.get(i);
            boolean added = d.delta() > 0;
            String prefix = (added ? "+" : "−") + Math.abs(d.delta()) + " ";
            lore.add(noItalic(Component.text(prefix, added ? NamedTextColor.GREEN : NamedTextColor.RED)
                    .append(itemName(d.itemKey()))));
        }
        int remaining = diff.size() - shown;
        if (remaining > 0) {
            lore.add(noItalic(Component.text("… +" + remaining, NamedTextColor.DARK_GRAY)));
        }
    }

    /** The client-localised display name of a material key, falling back to the raw key if unknown. */
    private static Component itemName(String key) {
        Material material = Material.matchMaterial(key);
        Component name = material != null
                ? Component.translatable(material.translationKey())
                : Component.text(key);
        return name.color(NamedTextColor.GRAY);
    }

    private ItemStack nav(Locale locale, Material icon, String key) {
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(noItalic(lang.getGui(locale, key)));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack info(Locale locale, String ownerName, int page, int pageCount) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(noItalic(lang.getGui(locale, "log.info",
                "player", ownerName,
                "page", Integer.toString(page + 1),
                "pages", Integer.toString(Math.max(1, pageCount)))));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack filler() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty());
        item.setItemMeta(meta);
        return item;
    }

    private static Component line(Component component) {
        return noItalic(component).colorIfAbsent(NamedTextColor.GRAY);
    }

    private static Component noItalic(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }
}
