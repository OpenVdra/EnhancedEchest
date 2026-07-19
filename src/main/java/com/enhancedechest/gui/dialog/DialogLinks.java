package com.enhancedechest.gui.dialog;

import com.enhancedechest.lang.LanguageManager;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.Locale;

/**
 * Builds the "open the documentation" button shared by the dialogs that have an online page explaining
 * them ({@code /ee config}, {@code /ee import}).
 *
 * <p>Three things are deliberately uniform across every such button, which is why they live here rather
 * than being rebuilt per dialog: the book sprite that marks it as a link rather than an action, the
 * address living in {@code gui.yml} so each language can point at its own translated page, and that
 * address never appearing on screen — a docs URL is long enough to dwarf the buttons beside it.
 */
@SuppressWarnings("UnstableApiUsage")
final class DialogLinks {

    private DialogLinks() {}

    /** Item whose sprite marks a documentation button; a book reads as "go and read" at a glance. */
    private static final String DOCS_ICON = "minecraft:knowledge_book";

    /**
     * A button that opens a documentation page in the viewer's browser.
     *
     * @param labelKey {@code gui.yml} key of the button text
     * @param descKey  {@code gui.yml} key of its tooltip
     * @param urlKey   {@code gui.yml} key holding the address to open, per locale
     */
    static ActionButton docsButton(LanguageManager lang, Locale locale, String labelKey, String descKey,
                                   String urlKey, int width) {
        return ActionButton.create(
                withSprite(DOCS_ICON, lang.getGui(locale, labelKey)),
                lang.getGui(locale, descKey),
                width,
                DialogAction.staticAction(ClickEvent.openUrl(plainText(lang, locale, urlKey))));
    }

    /**
     * Prepends an item sprite to a button label, the same way {@link ChestDialogs} shows chest icons.
     * Returns the label untouched when the material has no flat texture to draw, so a client that cannot
     * render it still gets a readable button rather than a missing-texture box.
     */
    private static Component withSprite(String materialKey, Component label) {
        Component icon = IconCatalog.sprite(materialKey);
        return icon == null ? label : icon.append(Component.text(" ")).append(label);
    }

    /**
     * Reads a {@code gui.yml} value that is a plain string rather than display text — an address, which has
     * to reach {@link ClickEvent#openUrl} as a {@code String}. Rendering it for the locale and flattening it
     * keeps the value translatable without a separate raw-string lookup in {@link LanguageManager}.
     */
    private static String plainText(LanguageManager lang, Locale locale, String key) {
        return PlainTextComponentSerializer.plainText().serialize(lang.getGui(locale, key));
    }
}
