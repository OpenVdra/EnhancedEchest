package com.enhancedechest.config;

import java.util.List;

/**
 * Declarative description of every {@code config.yml} key the in-game editor ({@code /ee config}) may
 * change: which section it belongs to, what kind of value it holds, and whether changing it needs a full
 * server restart.
 *
 * <p>This is the <b>single source of truth</b> for the editor. {@link ConfigEditor} validates and writes
 * against it and {@link com.enhancedechest.gui.dialog.ConfigDialogs} builds its forms from it, so adding a
 * new editable setting is one entry here plus its {@code gui.yml} label — no dialog or writer code.
 *
 * <p>Deliberately <b>not</b> exposed: nothing outside {@code config.yml} (language files have their own
 * files) and no key whose value the plugin sanitizes into a different shape than the admin typed.
 */
public final class ConfigSchema {

    private ConfigSchema() {}

    /**
     * Widest integer range still edited with a slider (see {@link Field#usesSlider()}). At 100 stops a
     * drag already moves in single units across the whole track; past that, aiming at a specific number
     * becomes guesswork and a text field is kinder.
     */
    private static final int MAX_SLIDER_STEPS = 100;

    /** What kind of value a key holds, which decides its input widget and how it is validated. */
    public enum FieldType {
        /** {@code true} / {@code false} — rendered as a toggle. */
        BOOLEAN,
        /** A whole number, bounded by {@link Field#min()} / {@link Field#max()}. */
        INTEGER,
        /** Free text; blank is allowed only when {@link Field#allowBlank()}. */
        TEXT,
        /** A duration in the plugin's own format ({@code 30s}, {@code 5m}, {@code 1d_2h}). */
        DURATION,
        /** One of {@link Field#options()} — rendered as a picker, so an invalid value is impossible. */
        ENUM,
        /** A YAML list of strings, edited as one comma-separated line. */
        STRING_LIST
    }

    /**
     * One editable key.
     *
     * @param path      full {@code config.yml} path, e.g. {@code enderchest.features.sort}
     * @param type      value kind, deciding the widget and the validation
     * @param labelKey  {@code gui.yml} key of the field's label (under {@code dialog.}). Dialog inputs
     *                  carry no tooltip, so the label itself states the accepted format or range
     * @param options   allowed values for {@link FieldType#ENUM}, empty otherwise
     * @param min       lowest accepted value for {@link FieldType#INTEGER}, ignored otherwise
     * @param max       highest accepted value for {@link FieldType#INTEGER}, ignored otherwise
     * @param step      accepted multiple for {@link FieldType#INTEGER} (1 = any), ignored otherwise
     * @param allowBlank whether an empty {@link FieldType#TEXT} value is accepted (passwords, server-id)
     * @param restart   whether the plugin must be fully restarted before the new value takes effect
     */
    public record Field(String path, FieldType type, String labelKey, List<String> options,
                        int min, int max, int step, boolean allowBlank, boolean restart) {

        static Field bool(String path, String labelKey) {
            return new Field(path, FieldType.BOOLEAN, labelKey, List.of(), 0, 0, 1, false, false);
        }

        static Field integer(String path, String labelKey, int min, int max) {
            return new Field(path, FieldType.INTEGER, labelKey, List.of(), min, max, 1, false, false);
        }

        static Field text(String path, String labelKey) {
            return new Field(path, FieldType.TEXT, labelKey, List.of(), 0, 0, 1, false, false);
        }

        static Field duration(String path, String labelKey) {
            return new Field(path, FieldType.DURATION, labelKey, List.of(), 0, 0, 1, false, false);
        }

        static Field options(String path, String labelKey, String... values) {
            return new Field(path, FieldType.ENUM, labelKey, List.of(values), 0, 0, 1, false, false);
        }

        static Field list(String path, String labelKey) {
            return new Field(path, FieldType.STRING_LIST, labelKey, List.of(), 0, 0, 1, true, false);
        }

        /** Same field, but its value only takes effect after a full server restart. */
        Field needsRestart() {
            return new Field(path, type, labelKey, options, min, max, step, allowBlank, true);
        }

        /** Same field, but an empty value is accepted (and written as an empty string). */
        Field blankAllowed() {
            return new Field(path, type, labelKey, options, min, max, step, true, restart);
        }

        /** Same integer field, but only multiples of {@code step} are accepted. */
        Field stepping(int step) {
            return new Field(path, type, labelKey, options, min, max, step, allowBlank, restart);
        }

        /** How many distinct values this integer field accepts, i.e. how many stops a slider would have. */
        public int steps() {
            return (max - min) / step + 1;
        }

        /**
         * Whether this key is edited with a slider rather than a typed number. A slider cannot be aimed:
         * dragging to an exact value gets harder the wider the range, so only short ranges get one and
         * anything longer stays a text field. That is why a port (65535 stops) is typed while the chest
         * size (6 stops) is dragged, even though both are integers.
         */
        public boolean usesSlider() {
            return type == FieldType.INTEGER && steps() <= MAX_SLIDER_STEPS;
        }

        /**
         * This key's identifier as a dialog input. A config path can't be used directly: the client only
         * accepts {@code [A-Za-z0-9_]} in an input key (it doubles as a macro variable name), so the dots
         * and hyphens are folded to underscores. The mapping stays unique because no two schema paths
         * differ only by a separator.
         */
        public String inputKey() {
            return path.replaceAll("[^A-Za-z0-9_]", "_");
        }
    }

    /**
     * One page of the editor. Sections exist to keep each dialog short enough to read without scrolling;
     * {@code config.yml} is far too long for a single form.
     *
     * @param id       stable identifier, used in the button click routing
     * @param titleKey {@code gui.yml} key of the page title (its body is the same key + {@code -body})
     * @param fields   the keys shown on this page, in display order
     */
    public record Section(String id, String titleKey, List<Field> fields) {

        /** The {@code gui.yml} key of this section's body text. */
        public String bodyKey() {
            return titleKey + "-body";
        }

        /** Whether any field on this page needs a restart, i.e. the page shows the restart warning. */
        public boolean hasRestartFields() {
            return fields.stream().anyMatch(Field::restart);
        }
    }

    /** Every editor page, in the order the root menu lists them. */
    public static final List<Section> SECTIONS = List.of(
            new Section("general", "dialog.config-general", List.of(
                    Field.text("language", "dialog.config-language"),
                    Field.bool("language-auto-detect", "dialog.config-language-auto-detect"))),

            new Section("enderchest", "dialog.config-enderchest", List.of(
                    Field.integer("enderchest.default-size", "dialog.config-default-size", 9, 54).stepping(9),
                    Field.options("enderchest.list-menu", "dialog.config-list-menu", "dialog", "inventory"),
                    Field.bool("enderchest.shift-click-list", "dialog.config-shift-click-list"),
                    Field.bool("permission-chests.enabled", "dialog.config-permission-chests"),
                    Field.bool("migration.enabled", "dialog.config-migration"))),

            new Section("features", "dialog.config-features", List.of(
                    Field.bool("enderchest.features.rename", "dialog.config-feature-rename"),
                    Field.bool("enderchest.features.icon", "dialog.config-feature-icon"),
                    Field.bool("enderchest.features.sort", "dialog.config-feature-sort"),
                    Field.duration("enderchest.features.sort-cooldown", "dialog.config-sort-cooldown"),
                    Field.bool("enderchest.features.rename-colors", "dialog.config-rename-colors"),
                    Field.list("enderchest.features.rename-blacklist", "dialog.config-rename-blacklist"))),

            new Section("temp", "dialog.config-temp", List.of(
                    Field.duration("temp-enderchest.expiry", "dialog.config-temp-expiry"),
                    Field.duration("temp-enderchest.check-interval", "dialog.config-temp-check-interval"),
                    Field.bool("temp-enderchest.deny-sound.enabled", "dialog.config-temp-deny-sound"),
                    Field.text("temp-enderchest.deny-sound.key", "dialog.config-temp-deny-sound-key"),
                    Field.bool("temp-enderchest.join-notify.enabled", "dialog.config-temp-join-notify"),
                    Field.bool("temp-enderchest.join-notify.sound.enabled", "dialog.config-temp-join-sound"),
                    Field.text("temp-enderchest.join-notify.sound.key", "dialog.config-temp-join-sound-key"))),

            new Section("backup", "dialog.config-backup", List.of(
                    Field.bool("backup.enabled", "dialog.config-backup-enabled"),
                    Field.duration("backup.interval", "dialog.config-backup-interval"),
                    Field.integer("backup.keep", "dialog.config-backup-keep", 0, 1000),
                    Field.bool("backup.on-startup", "dialog.config-backup-on-startup"),
                    Field.text("backup.folder", "dialog.config-backup-folder"))),

            // Split in two so neither form runs longer than the screen: which backend and how often it is
            // written here, the credentials to reach a remote one on its own page. Only autosave-interval
            // applies live — every other database key is bound when the pool is built at startup, so it is
            // marked restart-required (see EnhancedEchestPlugin#reload).
            new Section("database", "dialog.config-database", List.of(
                    Field.duration("database.autosave-interval", "dialog.config-autosave-interval"),
                    Field.options("database.type", "dialog.config-db-type",
                            "sqlite", "mysql", "mariadb", "postgres").needsRestart(),
                    Field.text("database.table-prefix", "dialog.config-table-prefix").needsRestart(),
                    Field.text("database.sqlite-file", "dialog.config-sqlite-file").needsRestart())),

            new Section("database-connection", "dialog.config-db-connection", List.of(
                    Field.text("database.host", "dialog.config-db-host").needsRestart(),
                    Field.integer("database.port", "dialog.config-db-port", 1, 65535).needsRestart(),
                    Field.text("database.database", "dialog.config-db-name").needsRestart(),
                    Field.text("database.username", "dialog.config-db-username").needsRestart(),
                    Field.text("database.password", "dialog.config-db-password").blankAllowed().needsRestart(),
                    Field.options("database.ssl", "dialog.config-db-ssl",
                            "disable", "require", "verify-full").needsRestart(),
                    Field.integer("database.pool-size", "dialog.config-db-pool-size", 1, 100).needsRestart())),

            new Section("cross-server", "dialog.config-cross-server", List.of(
                    Field.bool("cross-server.enabled", "dialog.config-cross-enabled").needsRestart(),
                    Field.text("cross-server.server-id", "dialog.config-cross-server-id")
                            .blankAllowed().needsRestart(),
                    Field.text("cross-server.redis.host", "dialog.config-redis-host").needsRestart(),
                    Field.integer("cross-server.redis.port", "dialog.config-redis-port", 1, 65535).needsRestart(),
                    Field.text("cross-server.redis.password", "dialog.config-redis-password")
                            .blankAllowed().needsRestart(),
                    Field.bool("cross-server.redis.ssl", "dialog.config-redis-ssl").needsRestart(),
                    Field.integer("cross-server.redis.database", "dialog.config-redis-database", 0, 15)
                            .needsRestart(),
                    Field.text("cross-server.redis.key-prefix", "dialog.config-redis-key-prefix").needsRestart()))
    );

    /** Looks up a section by its {@link Section#id()}, or null when the id is unknown. */
    public static Section section(String id) {
        return SECTIONS.stream().filter(s -> s.id().equals(id)).findFirst().orElse(null);
    }
}
