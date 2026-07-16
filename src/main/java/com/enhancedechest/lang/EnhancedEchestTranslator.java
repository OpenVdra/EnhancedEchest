package com.enhancedechest.lang;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.translation.MiniMessageTranslator;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Adventure {@link net.kyori.adventure.translation.Translator} backing EnhancedEchest's per-viewer
 * localization. It is registered once on the {@link net.kyori.adventure.translation.GlobalTranslator}
 * (see {@code EnhancedEchestPlugin}), so <b>every</b> Component the plugin sends — chat, inventory
 * titles, dialogs, item names — is rendered against the recipient client's own locale at send time.
 * That is why {@link LanguageManager#get} can keep returning a locale-free {@code Component.translatable}
 * without threading a {@link Locale} through call sites.
 *
 * <p>It holds normalized MiniMessage strings keyed by translation key then {@link Locale}
 * (see {@link LanguageManager} for how the raw YAML is normalized). Translation keys are namespaced
 * ({@code enhancedechest.msg.*} / {@code enhancedechest.gui.*}) so they never collide with vanilla keys.
 *
 * <p>Lookup fallback for a requested locale: exact match → same language (any region) → the configured
 * fallback locale ({@code language:} in config) → {@code en_US} (always bundled). When
 * {@code language-auto-detect} is off the requested locale is ignored and the configured fallback is
 * used for everyone — identical to the pre-feature single-locale behavior.
 */
public final class EnhancedEchestTranslator extends MiniMessageTranslator {

    private static final Key NAME = Key.key("enhancedechest", "lang");
    /** en_US is shipped in the jar and is the last-resort locale. */
    static final Locale ALWAYS_BUNDLED = Locale.US;

    // key -> (locale -> already-normalized MiniMessage string). Swapped atomically on (re)load.
    private volatile Map<String, Map<Locale, String>> table = Map.of();
    private volatile List<Locale> available = List.of();
    private volatile Locale configFallback = Locale.US;
    private volatile boolean autoDetect = true;

    public EnhancedEchestTranslator() {
        super(MiniMessage.miniMessage());
    }

    @Override
    public Key name() {
        return NAME;
    }

    /**
     * Atomically swaps in a freshly-loaded table. Called on startup and on {@code /ee reload}; the
     * translator stays registered on the GlobalTranslator across reloads, only its contents change.
     */
    void apply(Map<String, Map<Locale, String>> table, List<Locale> available,
               Locale configFallback, boolean autoDetect) {
        this.available = List.copyOf(available);
        this.configFallback = configFallback;
        this.autoDetect = autoDetect;
        this.table = table; // published last so readers that see it also see the fields above
    }

    @Override
    protected @Nullable String getMiniMessageString(String key, Locale locale) {
        Map<Locale, String> perLocale = table.get(key);
        if (perLocale == null) {
            return null; // unknown key: leave the raw translation key visible (a missing-translation signal)
        }
        Locale wanted = autoDetect ? locale : configFallback;

        String exact = perLocale.get(wanted);
        if (exact != null) return exact;

        Locale sameLang = sameLanguage(wanted);
        if (sameLang != null) {
            String s = perLocale.get(sameLang);
            if (s != null) return s;
        }
        String fallback = perLocale.get(configFallback);
        if (fallback != null) return fallback;

        return perLocale.get(ALWAYS_BUNDLED);
    }

    /** The first loaded locale sharing {@code wanted}'s language (e.g. {@code vi} matches {@code vi_VN}). */
    private @Nullable Locale sameLanguage(Locale wanted) {
        String lang = wanted.getLanguage();
        if (lang.isEmpty()) return null;
        for (Locale l : available) {
            if (l.getLanguage().equals(lang)) return l;
        }
        return null;
    }
}
