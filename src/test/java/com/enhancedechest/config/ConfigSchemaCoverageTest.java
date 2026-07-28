package com.enhancedechest.config;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the one thing {@link ConfigSchema} cannot check itself: a field entry is just two strings, so
 * a mistyped config path silently edits a key that does not exist, and a mistyped label key reaches the
 * player as a raw {@code dialog.config-...} string in whichever locale was missed. Both survive
 * compilation and only show up when someone opens {@code /ee config} in that language.
 */
class ConfigSchemaCoverageTest {

    private static final List<String> BUNDLED_LOCALES = List.of("en_US", "vi_VN");
    private static final Path RESOURCES = Path.of("src", "main", "resources");

    @Test
    void everyEditableFieldExistsInConfigYml() throws Exception {
        Set<String> paths = flatten(loadYaml(RESOURCES.resolve("config.yml")), "");
        List<String> missing = new ArrayList<>();
        for (ConfigSchema.Section section : ConfigSchema.SECTIONS) {
            for (ConfigSchema.Field field : section.fields()) {
                if (!paths.contains(field.path())) missing.add(field.path());
            }
        }
        assertEquals(List.of(), missing, "schema paths absent from config.yml");
    }

    @Test
    void everyLabelExistsInEveryBundledLocale() throws Exception {
        for (String locale : BUNDLED_LOCALES) {
            Set<String> keys = flatten(loadYaml(
                    RESOURCES.resolve("language").resolve(locale).resolve("gui.yml")), "");
            List<String> missing = new ArrayList<>();
            for (ConfigSchema.Section section : ConfigSchema.SECTIONS) {
                if (!keys.contains(section.titleKey())) missing.add(section.titleKey());
                if (!keys.contains(section.bodyKey())) missing.add(section.bodyKey());
                for (ConfigSchema.Field field : section.fields()) {
                    if (!keys.contains(field.labelKey())) missing.add(field.labelKey());
                }
            }
            assertEquals(List.of(), missing, "gui.yml keys missing from locale " + locale);
        }
    }

    /** Dialog input keys double as client macro variable names, so they must stay unique after folding. */
    @Test
    void inputKeysAreUniqueAndClientSafe() {
        Set<String> seen = new LinkedHashSet<>();
        for (ConfigSchema.Section section : ConfigSchema.SECTIONS) {
            for (ConfigSchema.Field field : section.fields()) {
                String key = field.inputKey();
                assertTrue(key.matches("[A-Za-z0-9_]+"),
                        "input key rejected by the client: " + key + " (from " + field.path() + ")");
                assertTrue(seen.add(key), "two schema paths fold to the same input key: " + key);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadYaml(Path path) throws Exception {
        try (InputStream in = Files.newInputStream(path)) {
            return (Map<String, Object>) new Yaml().load(in);
        }
    }

    /** Every leaf and branch as a dotted path, so both {@code backup} and {@code backup.keep} match. */
    @SuppressWarnings("unchecked")
    private static Set<String> flatten(Map<String, Object> node, String prefix) {
        Set<String> paths = new LinkedHashSet<>();
        if (node == null) return paths;
        node.forEach((key, value) -> {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            paths.add(path);
            if (value instanceof Map<?, ?> child) {
                paths.addAll(flatten((Map<String, Object>) child, path));
            }
        });
        return paths;
    }
}
