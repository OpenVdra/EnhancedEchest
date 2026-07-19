package com.enhancedechest.gui.dialog;

import com.enhancedechest.config.ConfigEditor;
import com.enhancedechest.config.ConfigSchema;
import com.enhancedechest.config.ConfigSchema.Field;
import com.enhancedechest.config.ConfigSchema.Section;
import com.enhancedechest.lang.LanguageManager;
import com.enhancedechest.scheduler.Scheduler;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * The in-game {@code config.yml} editor ({@code /ee config}): a root menu of sections, and one form per
 * section built straight from {@link ConfigSchema}. Sibling of {@link ChestDialogs} — all Dialog API usage
 * stays inside this package so a Paper breaking change is confined to it.
 *
 * <p>{@code config.yml} is far too long for one form, so each {@link Section} is its own page (General,
 * Ender chests, Features, …). A page's widget per key follows its {@link ConfigSchema.FieldType}: toggles
 * for booleans, a picker for enums (so an invalid value can't be submitted at all), and text for the rest.
 *
 * <p>Save is per page and all-or-nothing: {@link ConfigEditor#save} validates every field first, so one bad
 * value rejects the page and writes nothing. A rejected page is re-shown <b>with what the admin typed</b>
 * still in it, next to a chat message naming the bad fields, so nothing has to be retyped. A successful
 * save writes {@code config.yml} (comments intact) and runs the plugin's normal reload, so the new values
 * are live without {@code /ee reload}; keys that are bound at startup ({@link Field#restart()}) are called
 * out with a "restart required" notice instead of pretending they applied.
 *
 * <p>Every label is rendered eagerly with the viewer's locale ({@code lang.getGui(locale, …)}), because
 * Paper does not run the GlobalTranslator over the Dialog API.
 */
@SuppressWarnings("UnstableApiUsage")
public final class ConfigDialogs {

    // One content width for the whole editor, so the body text, the inputs and the button row below them
    // all end on the same edge instead of each stopping at its own width. Every two-column button row is
    // half of it, which is why these are exact halves rather than round numbers.
    private static final int CONTENT_WIDTH = 320;
    private static final int HALF_WIDTH = CONTENT_WIDTH / 2;
    private static final int FIELD_MAX_LENGTH = 256;

    /** Slider caption: the field label, then its current value, e.g. "Default chest size: 54". */
    private static final String SLIDER_LABEL_FORMAT = "%s: %s";

    private final ConfigEditor editor;
    private final LanguageManager lang;
    private final Scheduler scheduler;
    private final Logger logger;
    private final Runnable reloadPlugin;

    /**
     * @param reloadPlugin the plugin's own {@code reload()}, run on the global thread after a successful
     *                     write so the new values apply without {@code /ee reload}
     */
    public ConfigDialogs(ConfigEditor editor, LanguageManager lang, Scheduler scheduler, Logger logger,
                         Runnable reloadPlugin) {
        this.editor = editor;
        this.lang = lang;
        this.scheduler = scheduler;
        this.logger = logger;
        this.reloadPlugin = reloadPlugin;
    }

    // ---- root menu ----

    /** Shows the section menu to an admin. */
    public void openRoot(Player admin) {
        scheduler.runAtEntity(admin, task -> {
            if (admin.isOnline()) admin.showDialog(rootDialog(admin.locale()));
        });
    }

    /** The root menu: one button per {@link ConfigSchema#SECTIONS section}, plus Close. */
    private Dialog rootDialog(Locale locale) {
        List<ActionButton> buttons = new ArrayList<>(ConfigSchema.SECTIONS.size());
        for (Section section : ConfigSchema.SECTIONS) {
            buttons.add(ActionButton.create(
                    lang.getGui(locale, section.titleKey()),
                    lang.getGui(locale, section.bodyKey()),
                    HALF_WIDTH,
                    click((view, audience) -> {
                        if (audience instanceof Player p) showSection(p, section, Map.of());
                    })));
        }
        // Last in the grid, so it reads as a footer rather than as another section.
        buttons.add(DialogLinks.docsButton(lang, locale, "dialog.config-docs", "dialog.config-docs-desc",
                "dialog.config-docs-url", HALF_WIDTH));

        // The exit button sits alone on its own centred row, so it takes the half width of a single
        // section button rather than the full content width — at full width it reads as a banner.
        ActionButton close = ActionButton.create(lang.getGui(locale, "dialog.close"), null,
                HALF_WIDTH, click((view, audience) -> { /* dismiss only */ }));

        return Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(lang.getGui(locale, "dialog.config-title"))
                        .body(List.of(DialogBody.plainMessage(lang.getGui(locale, "dialog.config-body"), CONTENT_WIDTH)))
                        .build())
                .type(DialogType.multiAction(buttons, close, 2)));
    }

    // ---- section form ----

    /**
     * Shows one section's form on the admin's thread.
     *
     * @param overrides values to seed the inputs with instead of what is in the file; empty for a fresh
     *                  open, and the rejected submission when a save bounced (so nothing is retyped)
     */
    private void showSection(Player admin, Section section, Map<String, String> overrides) {
        scheduler.runAtEntity(admin, task -> {
            if (admin.isOnline()) admin.showDialog(sectionDialog(admin.locale(), section, overrides));
        });
    }

    /** Builds a section's form: one input per field, then Save and Back. */
    private Dialog sectionDialog(Locale locale, Section section, Map<String, String> overrides) {
        List<DialogInput> inputs = new ArrayList<>(section.fields().size());
        for (Field field : section.fields()) {
            inputs.add(input(locale, field, overrides.get(field.path())));
        }

        // A page holding startup-bound keys says so up front, so nobody expects a live effect from
        // changing a database host. The other pages state the opposite: saving applies immediately.
        Component body = lang.getGui(locale,
                section.hasRestartFields() ? "dialog.config-section-body-restart" : "dialog.config-section-body");

        ActionButton save = ActionButton.create(lang.getGui(locale, "dialog.config-save"),
                lang.getGui(locale, "dialog.config-save-desc"), HALF_WIDTH,
                click((view, audience) -> {
                    if (audience instanceof Player p) submit(p, section, view);
                }));
        ActionButton back = ActionButton.create(lang.getGui(locale, "dialog.back"), null, HALF_WIDTH,
                click((view, audience) -> {
                    if (audience instanceof Player p) openRoot(p);
                }));

        return Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(lang.getGui(locale, section.titleKey()))
                        .body(List.of(DialogBody.plainMessage(body, CONTENT_WIDTH)))
                        .inputs(inputs)
                        .build())
                .type(DialogType.multiAction(List.of(save, back), null, 2)));
    }

    /**
     * Builds the widget for one key: a toggle for booleans, a picker for enums (the client can then only
     * return a valid option), and a text field for everything else. The initial value is {@code override}
     * when re-showing a rejected page, otherwise what {@code config.yml} currently holds.
     */
    private DialogInput input(Locale locale, Field field, String override) {
        Component label = lang.getGui(locale, field.labelKey());
        if (field.type() == ConfigSchema.FieldType.BOOLEAN) {
            boolean initial = override != null ? Boolean.parseBoolean(override) : editor.currentBoolean(field);
            return DialogInput.bool(field.inputKey(), label).initial(initial).build();
        }
        String initial = override != null ? override : editor.currentText(field);
        if (field.usesSlider()) {
            // Short integer ranges get a slider, which makes an out-of-range or non-numeric submission
            // impossible. LABEL_FORMAT appends the live value to the label, so the number stays readable
            // while dragging.
            return DialogInput.numberRange(field.inputKey(), label, field.min(), field.max())
                    .width(CONTENT_WIDTH)
                    .labelFormat(SLIDER_LABEL_FORMAT)
                    .initial(parseFloat(initial, field.min()))
                    .step((float) field.step())
                    .build();
        }
        if (field.type() == ConfigSchema.FieldType.ENUM) {
            List<SingleOptionDialogInput.OptionEntry> entries = new ArrayList<>(field.options().size());
            for (String option : field.options()) {
                entries.add(SingleOptionDialogInput.OptionEntry.create(
                        option, Component.text(option), option.equals(initial)));
            }
            return DialogInput.singleOption(field.inputKey(), label, entries)
                    .width(CONTENT_WIDTH)
                    .build();
        }
        return DialogInput.text(field.inputKey(), label)
                .width(CONTENT_WIDTH)
                .initial(initial)
                .maxLength(FIELD_MAX_LENGTH)
                .build();
    }

    // ---- saving ----

    /**
     * Handles a Save click: reads the form, then writes and reloads on the global thread.
     *
     * <p>The write and the reload must share one thread hop — {@link ConfigEditor#save} mutates the
     * plugin's {@code FileConfiguration} and {@code reloadPlugin} re-reads it, and both touch live services.
     * The file is a few kilobytes, so the write itself is not worth pushing off-thread and splitting.
     */
    private void submit(Player admin, Section section, DialogResponseView view) {
        Map<String, String> submitted = readForm(section, view);
        scheduler.runNextTick(task -> {
            ConfigEditor.SaveResult result;
            try {
                result = editor.save(section, submitted);
            } catch (Exception e) {
                logger.error("Failed to save config.yml from the /ee config menu", e);
                if (admin.isOnline()) admin.sendMessage(lang.get("config.save-failed"));
                return;
            }
            if (!result.ok()) {
                // Nothing was written. Name the bad fields and hand the page back with the typed values.
                Component names = joinLabels(admin.locale(), result.invalid());
                if (admin.isOnline()) {
                    admin.sendMessage(lang.getRich("config.invalid", "fields", names));
                }
                showSection(admin, section, submitted);
                return;
            }
            if (result.changed() == 0) {
                if (admin.isOnline()) admin.sendMessage(lang.get("config.no-changes"));
                openRoot(admin);
                return;
            }
            // Apply live. This is what makes /ee reload unnecessary after an edit from the menu.
            reloadPlugin.run();
            if (admin.isOnline()) {
                admin.sendMessage(lang.get("config.saved", "count", Integer.toString(result.changed())));
                if (result.restart()) {
                    admin.sendMessage(lang.get("config.restart-required"));
                }
            }
            openRoot(admin);
        });
    }

    /**
     * Reads every field of the shown page out of the response. A field the client did not return is left
     * out entirely, so {@link ConfigEditor#save} leaves that key untouched rather than blanking it.
     */
    private static Map<String, String> readForm(Section section, DialogResponseView view) {
        Map<String, String> values = new LinkedHashMap<>();
        for (Field field : section.fields()) {
            if (field.type() == ConfigSchema.FieldType.BOOLEAN) {
                Boolean value = view.getBoolean(field.inputKey());
                if (value != null) values.put(field.path(), value.toString());
            } else if (field.usesSlider()) {
                // A slider reports a float even when every stop is a whole number, so round it back to
                // the integer the config key actually holds.
                Float value = view.getFloat(field.inputKey());
                if (value != null) values.put(field.path(), Integer.toString(Math.round(value)));
            } else {
                // Both text inputs and the option picker come back as text (the picker's option id).
                String value = view.getText(field.inputKey());
                if (value != null) values.put(field.path(), value);
            }
        }
        return values;
    }

    /** Comma-joins the labels of the rejected fields for the "invalid value" chat message. */
    private Component joinLabels(Locale locale, List<Field> fields) {
        Component joined = Component.empty();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) joined = joined.append(Component.text(", "));
            joined = joined.append(lang.getGui(locale, fields.get(i).labelKey()));
        }
        return joined;
    }

    /**
     * Reads a slider's starting position, falling back to the range's low end when the stored value is not
     * a number — a hand-edited {@code config.yml} must not stop the page from opening.
     */
    private static float parseFloat(String value, int fallback) {
        try {
            return Float.parseFloat(value.trim());
        } catch (NumberFormatException | NullPointerException e) {
            return fallback;
        }
    }

    /** Same helper as {@link ChestDialogs}: wraps a click body into a custom-click dialog action. */
    private static DialogAction click(BiConsumer<DialogResponseView,
            net.kyori.adventure.audience.Audience> body) {
        return DialogAction.customClick(body::accept, ClickCallback.Options.builder().build());
    }
}
