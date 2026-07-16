package com.enhancedechest.lang;

import com.enhancedechest.config.PluginConfig;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.translation.GlobalTranslator;
import net.kyori.adventure.translation.Translator;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * End-to-end check of the real load pipeline: {@link LanguageManager} reads the bundled
 * {@code language/*} resource files, normalizes them, and (once its translator is on the
 * {@code GlobalTranslator}) renders per client locale. This is the regression guard for the class of
 * bug where a component reaches the client as its raw translation key — here we prove the server side
 * resolves the key for both bundled locales and falls back for an unbundled one.
 */
class LanguageManagerIntegrationTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private ServerMock server;
    private JavaPlugin plugin;
    private LanguageManager lang;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("EnhancedEchest");

        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("language", "en_US");
        cfg.set("language-auto-detect", true);
        PluginConfig config = new PluginConfig(cfg);

        lang = new LanguageManager(plugin, config, "en_US");
        GlobalTranslator.translator().addSource(lang.translator());
    }

    @AfterEach
    void tearDown() {
        GlobalTranslator.translator().removeSource(lang.translator());
        MockBukkit.unmock();
    }

    private String rendered(String key, Locale viewer) {
        return PLAIN.serialize(GlobalTranslator.render(lang.getGui(key), viewer));
    }

    @Test
    void guiKeyResolvesPerLocaleFromTheRealFiles() {
        Locale en = Translator.parseLocale("en_US");
        Locale vi = Translator.parseLocale("vi_VN");

        String enTitle = rendered("dialog.list-title", en);
        String viTitle = rendered("dialog.list-title", vi);

        // The exact bundled strings (en_US/vi_VN gui.yml -> dialog.list-title).
        assertEquals("Your Ender Chests", enTitle);
        assertEquals("Các Rương Ender Của Bạn", viTitle);
        // Crucially: the raw key must NOT leak through (the reported dialog bug).
        assertFalse(enTitle.contains("enhancedechest."), "raw key leaked: " + enTitle);
    }

    @Test
    void unbundledLocaleFallsBackToConfigLocale() {
        Locale ja = Translator.parseLocale("ja_JP");
        assertEquals("Your Ender Chests", rendered("dialog.list-title", ja));
    }
}
