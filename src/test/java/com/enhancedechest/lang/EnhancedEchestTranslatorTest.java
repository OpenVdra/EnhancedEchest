package com.enhancedechest.lang;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.translation.Argument;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.translation.GlobalTranslator;
import net.kyori.adventure.translation.Translator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the per-viewer localization mechanism at its core: the {@link EnhancedEchestTranslator}
 * registered on the {@code GlobalTranslator}, rendered against different client locales. Uses the
 * already-normalized MiniMessage strings that {@link LanguageManager} would produce (legacy already
 * converted, {@code {placeholder}} already turned into {@code <placeholder>}), so no Bukkit is needed.
 */
class EnhancedEchestTranslatorTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private static final Locale EN = Translator.parseLocale("en_US");
    private static final Locale VI = Translator.parseLocale("vi_VN");
    private static final Locale JA = Translator.parseLocale("ja_JP");

    // Mirrors LanguageManager's namespaced, normalized table (english + vietnamese, one arg each).
    private static final Map<String, Map<Locale, String>> TABLE = Map.of(
            "enhancedechest.msg.greet", Map.of(
                    EN, "Hello <name>!",
                    VI, "Xin chào <name>!"),
            "enhancedechest.gui.enderchest.title-numbered", Map.of(
                    EN, "Ender Chest <index>",
                    VI, "Rương Ender <index>")
    );

    private EnhancedEchestTranslator translator;

    private void register(Locale fallback, boolean autoDetect) {
        translator = new EnhancedEchestTranslator();
        translator.apply(TABLE, List.of(EN, VI), fallback, autoDetect);
        GlobalTranslator.translator().addSource(translator);
    }

    @AfterEach
    void tearDown() {
        if (translator != null) {
            GlobalTranslator.translator().removeSource(translator);
        }
    }

    private String render(String key, Locale viewer, String... args) {
        Component c = args.length == 0
                ? Component.translatable(key)
                : Component.translatable(key, Argument.string(args[0], args[1]));
        return PLAIN.serialize(GlobalTranslator.render(c, viewer));
    }

    @Test
    void rendersEachViewerLocale() {
        register(EN, true);
        assertEquals("Xin chào Steve!", render("enhancedechest.msg.greet", VI, "name", "Steve"));
        assertEquals("Hello Steve!", render("enhancedechest.msg.greet", EN, "name", "Steve"));
    }

    @Test
    void unbundledLocaleFallsBackToConfigLocale() {
        register(EN, true); // config fallback = en_US
        assertEquals("Hello Steve!", render("enhancedechest.msg.greet", JA, "name", "Steve"));
    }

    @Test
    void languageOnlyLocaleMatchesRegionVariant() {
        register(EN, true);
        // A client reporting just "vi" (no region) still resolves to the bundled vi_VN.
        assertEquals("Xin chào Steve!",
                render("enhancedechest.msg.greet", Locale.forLanguageTag("vi"), "name", "Steve"));
    }

    @Test
    void autoDetectOffForcesTheConfigLocaleForEveryone() {
        register(VI, false); // fallback = vi_VN, auto-detect off
        // Even an en_US client gets Vietnamese, matching the single-locale legacy behavior.
        assertEquals("Xin chào Steve!", render("enhancedechest.msg.greet", EN, "name", "Steve"));
    }

    @Test
    void argumentValueIsInsertedLiterallyNotReparsed() {
        register(EN, true);
        // A value carrying MiniMessage-looking text must appear verbatim, never parsed as formatting —
        // this is what makes passing chest names / player input as arguments safe.
        assertEquals("Hello <red>x!", render("enhancedechest.msg.greet", EN, "name", "<red>x"));
    }

    @Test
    void numericPlaceholderRenders() {
        register(EN, true);
        assertEquals("Ender Chest 3",
                render("enhancedechest.gui.enderchest.title-numbered", EN, "index", "3"));
    }
}
