package com.enhancedechest.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the 1.0.11 {@code gui.yml} label renames actually migrate an existing install: an old key's
 * value is carried to the new {@code -label} key and the old key is dropped. Guards against a typo in a
 * {@link ConfigMigrations#GUI} rename path (which would silently reset a translator's text to default).
 */
class ConfigMigrationsGuiTest {

    // Minimal bundled-default content: just the new keys the migrator fills in / renames against.
    private static final String DEFAULTS = String.join("\n",
            "dialog:",
            "  import-sqlite-file-label: 'SQLite file'",
            "  import-host-label: 'Host'",
            "  import-database-label: 'Database name'",
            "  import-username-label: 'Username'",
            "  import-password-label: 'Password'");

    @Test
    void renamesImportLabelKeysKeepingTheUserValue() throws Exception {
        // An existing install still on the old key names, one with a customized (translated) value.
        File file = File.createTempFile("gui", ".yml");
        file.deleteOnExit();
        Files.writeString(file.toPath(), String.join("\n",
                "dialog:",
                "  import-host: 'Máy chủ tuỳ chỉnh'",
                "  import-username: 'Tài khoản'"));

        boolean changed = YamlMigrator.migrate(
                file,
                new ByteArrayInputStream(DEFAULTS.getBytes(StandardCharsets.UTF_8)),
                ConfigMigrations.GUI,
                LoggerFactory.getLogger(ConfigMigrationsGuiTest.class));

        assertTrue(changed, "migration should report a change");

        YamlConfiguration result = YamlConfiguration.loadConfiguration(file);
        // Old keys gone, values carried to the new -label keys.
        assertFalse(result.contains("dialog.import-host"), "old key must be removed");
        assertFalse(result.contains("dialog.import-username"), "old key must be removed");
        assertEquals("Máy chủ tuỳ chỉnh", result.getString("dialog.import-host-label"));
        assertEquals("Tài khoản", result.getString("dialog.import-username-label"));
        // Keys the user never had are backfilled from defaults under the new name.
        assertEquals("Password", result.getString("dialog.import-password-label"));
    }

    @Test
    void freshInstallWithNewKeysIsUnchanged() throws Exception {
        File file = File.createTempFile("gui", ".yml");
        file.deleteOnExit();
        Files.writeString(file.toPath(), DEFAULTS);

        boolean changed = YamlMigrator.migrate(
                file,
                new ByteArrayInputStream(DEFAULTS.getBytes(StandardCharsets.UTF_8)),
                ConfigMigrations.GUI,
                LoggerFactory.getLogger(ConfigMigrationsGuiTest.class));

        assertFalse(changed, "a file already on the new keys needs no migration");
    }
}
