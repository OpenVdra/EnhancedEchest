package com.enhancedechest.config;

import com.enhancedechest.config.ConfigSchema.Field;
import com.enhancedechest.config.ConfigSchema.Section;
import com.enhancedechest.util.DurationFormat;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Reads and writes {@code config.yml} for the in-game editor ({@code /ee config}), against the key list in
 * {@link ConfigSchema}.
 *
 * <p>Saving is all-or-nothing per page: every submitted value is validated first and a single bad field
 * aborts the whole page, so the file is never left half-updated. Values that pass are written through
 * Bukkit's {@code FileConfiguration}, which rewrites the file <b>keeping every comment</b> — the admin's
 * annotated {@code config.yml} survives an edit from the menu.
 *
 * <p>After a successful write the caller runs the plugin's normal {@code reload()}, which is why the menu
 * needs no {@code /ee reload} afterwards. Connection settings are the exception: they are bound when the
 * pool is built at startup, so their fields are flagged {@link Field#restart()} and the caller warns.
 *
 * <p><b>Threading:</b> {@link #save} touches the plugin's {@code FileConfiguration} and writes a file; call
 * it on the main/global thread only (see {@link com.enhancedechest.gui.dialog.ConfigDialogs}).
 */
public final class ConfigEditor {

    private final Supplier<FileConfiguration> configSupplier;
    private final Runnable saveAction;

    /**
     * @param configSupplier the live {@code config.yml} view (normally {@code plugin::getConfig})
     * @param saveAction     writes that view back to disk (normally {@code plugin::saveConfig})
     */
    public ConfigEditor(Supplier<FileConfiguration> configSupplier, Runnable saveAction) {
        this.configSupplier = configSupplier;
        this.saveAction = saveAction;
    }

    /**
     * Outcome of a save attempt.
     *
     * @param invalid  fields whose submitted value was rejected; when non-empty <b>nothing was written</b>
     * @param changed  how many keys actually changed value (an unchanged resubmit writes nothing)
     * @param restart  whether any changed key only takes effect after a full server restart
     */
    public record SaveResult(List<Field> invalid, int changed, boolean restart) {

        public boolean ok() {
            return invalid.isEmpty();
        }
    }

    // ---- reading current values ----

    /** Current value of a boolean field, for seeding a toggle. */
    public boolean currentBoolean(Field field) {
        return configSupplier.get().getBoolean(field.path());
    }

    /**
     * Current value of a non-boolean field rendered as the text/picker initial value. Lists are joined
     * into the one comma-separated line the editor edits them as.
     */
    public String currentText(Field field) {
        FileConfiguration config = configSupplier.get();
        return switch (field.type()) {
            case STRING_LIST -> String.join(", ", config.getStringList(field.path()));
            case INTEGER -> Integer.toString(config.getInt(field.path()));
            case ENUM -> normalizeOption(field, config.getString(field.path(), ""));
            default -> {
                String value = config.getString(field.path());
                yield value == null ? "" : value;
            }
        };
    }

    /**
     * Maps a stored enum value onto one of the field's options, falling back to the first option when the
     * file holds something unrecognised — the picker has to start on a real entry.
     */
    private static String normalizeOption(Field field, String stored) {
        String value = stored == null ? "" : stored.trim().toLowerCase(Locale.ROOT);
        return field.options().contains(value) ? value : field.options().getFirst();
    }

    // ---- validating + writing ----

    /**
     * Validates every submitted value for {@code section} and, if all pass, writes the changed ones to
     * {@code config.yml} and saves the file. Values absent from {@code submitted} (a field the client did
     * not return) are left untouched.
     *
     * @param submitted raw values keyed by {@link Field#path()}
     * @return what happened; {@link SaveResult#ok()} is false when nothing was written
     */
    public SaveResult save(Section section, Map<String, String> submitted) {
        FileConfiguration config = configSupplier.get();
        List<Field> invalid = new ArrayList<>();
        // Parsed values are staged here so a failure late in the page cannot leave earlier keys written.
        Map<Field, Object> parsed = new LinkedHashMap<>();

        for (Field field : section.fields()) {
            String raw = submitted.get(field.path());
            if (raw == null) continue;
            Object value = parse(field, raw);
            if (value == null) {
                invalid.add(field);
            } else {
                parsed.put(field, value);
            }
        }
        if (!invalid.isEmpty()) {
            return new SaveResult(List.copyOf(invalid), 0, false);
        }

        int changed = 0;
        boolean restart = false;
        for (Map.Entry<Field, Object> entry : parsed.entrySet()) {
            Field field = entry.getKey();
            Object value = entry.getValue();
            if (unchanged(config, field, value)) continue;
            config.set(field.path(), value);
            changed++;
            restart |= field.restart();
        }
        if (changed > 0) {
            saveAction.run();
        }
        return new SaveResult(List.of(), changed, restart);
    }

    /** Whether the file already holds {@code value} for this key, so writing it would be a no-op. */
    private static boolean unchanged(FileConfiguration config, Field field, Object value) {
        Object current = switch (field.type()) {
            case BOOLEAN -> config.getBoolean(field.path());
            case INTEGER -> config.getInt(field.path());
            case STRING_LIST -> config.getStringList(field.path());
            default -> config.getString(field.path());
        };
        return value.equals(current);
    }

    /**
     * Parses one submitted value into what should be written, or returns null when it is invalid.
     * Booleans and pickers cannot be invalid (the client can only return a real option), so the real work
     * is bounds-checking numbers, parsing durations, and refusing blank text where blank is meaningless.
     */
    private static @Nullable Object parse(Field field, String raw) {
        String value = raw.trim();
        switch (field.type()) {
            case BOOLEAN -> {
                return Boolean.parseBoolean(value);
            }
            case ENUM -> {
                String lower = value.toLowerCase(Locale.ROOT);
                return field.options().contains(lower) ? lower : null;
            }
            case INTEGER -> {
                int number;
                try {
                    number = Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    return null;
                }
                if (number < field.min() || number > field.max()) return null;
                if (field.step() > 1 && number % field.step() != 0) return null;
                return number;
            }
            case DURATION -> {
                try {
                    DurationFormat.parse(value);
                } catch (IllegalArgumentException e) {
                    return null;
                }
                return value;
            }
            case STRING_LIST -> {
                // One comma-separated line in, a YAML list out. Blank entries are dropped, so trailing
                // commas and "a, , b" are tolerated rather than writing empty words.
                return Arrays.stream(value.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();
            }
            default -> {
                if (value.isEmpty() && !field.allowBlank()) return null;
                return value;
            }
        }
    }
}
