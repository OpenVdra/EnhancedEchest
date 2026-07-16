package com.enhancedechest.config;

import java.util.List;

/**
 * Central registry for all YAML key renames across plugin versions.
 * <p>
 * When a config or language key is renamed, add a new {@link YamlMigrator.Rename} entry here.
 * On startup the migrator reads the user's old key value, writes it under the new key name,
 * and removes the old key — all automatically.
 * <p>
 * Example: if "database.type" is renamed to "storage.backend" in 1.2.0, add:
 * <pre>
 *   new YamlMigrator.Rename("database.type", "storage.backend")
 * </pre>
 * Order matters only when renames chain (A→B then B→C). List entries in the order they happened.
 */
public final class ConfigMigrations {

    private ConfigMigrations() {}

    /** Renames applied to {@code config.yml}. */
    public static final List<YamlMigrator.Rename> CONFIG = List.of(
            // -- 1.x.x renames go here --
    );

    /** Renames applied to {@code language/<locale>/messages.yml}. */
    public static final List<YamlMigrator.Rename> MESSAGES = List.of(
            // -- 1.x.x renames go here --
    );

    /** Renames applied to {@code language/<locale>/gui.yml}. */
    public static final List<YamlMigrator.Rename> GUI = List.of(
            // 1.0.11: import dialog field labels gained the -label suffix, matching name-label /
            // icon-search-label, so every input-label key is named consistently.
            new YamlMigrator.Rename("dialog.import-sqlite-file", "dialog.import-sqlite-file-label"),
            new YamlMigrator.Rename("dialog.import-host",        "dialog.import-host-label"),
            new YamlMigrator.Rename("dialog.import-database",    "dialog.import-database-label"),
            new YamlMigrator.Rename("dialog.import-username",    "dialog.import-username-label"),
            new YamlMigrator.Rename("dialog.import-password",    "dialog.import-password-label")
    );
}
