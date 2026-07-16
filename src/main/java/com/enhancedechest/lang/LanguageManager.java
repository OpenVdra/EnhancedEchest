package com.enhancedechest.lang;

import com.enhancedechest.config.ConfigMigrations;
import com.enhancedechest.config.PluginConfig;
import com.enhancedechest.config.YamlMigrator;
import com.enhancedechest.model.ChestKind;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.text.minimessage.translation.Argument;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.translation.GlobalTranslator;
import net.kyori.adventure.translation.Translator;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Loads the plugin's language files and exposes them as Adventure {@link Component}s.
 *
 * <p><b>Per-viewer localization.</b> Every message/label returned here is a locale-free
 * {@link Component#translatable(String) translatable Component}; the actual text is resolved by
 * {@link EnhancedEchestTranslator} (registered on the {@code GlobalTranslator}) against the recipient
 * client's own locale at send time. So a single shared inventory title, or one broadcast, renders in
 * each viewer's language with no {@link Locale} threaded through call sites. All bundled locales
 * ({@code en_US}, {@code vi_VN}) plus any the operator drops under {@code language/} are loaded up front.
 *
 * <p>At load each raw YAML value is normalized <b>once</b> into an equivalent MiniMessage string:
 * legacy {@code &} strings are converted, {@code {prefix}} is inlined, and {@code {placeholder}} tokens
 * become {@code <placeholder>} argument tags so {@link Argument#string} substitutions resolve at render
 * time. Because substitutions are passed as arguments (not spliced into the raw string), a value such as
 * a player-supplied chest name can never inject formatting into a surrounding message.
 */
public final class LanguageManager {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();

    /**
     * MiniMessage instance for <b>player-supplied</b> chest names. Unlike {@link #MINI} (used for trusted
     * language files) it resolves only cosmetic tags — colours, hex, gradients, rainbows and text
     * decorations — and deliberately omits the interactive ones ({@code <click>}, {@code <hover>},
     * {@code <insertion>}, {@code <selector>}, {@code <font>}, …). An unknown/omitted tag is left as
     * literal text (non-strict), so a name can never carry a runnable command or a fake hover, which would
     * otherwise be an exploit vector when an operator sees the name.
     */
    private static final MiniMessage NAME_MINI = MiniMessage.builder()
            .tags(TagResolver.builder()
                    .resolver(StandardTags.color())
                    .resolver(StandardTags.rainbow())
                    .resolver(StandardTags.gradient())
                    .resolver(StandardTags.decorations())
                    .resolver(StandardTags.reset())
                    .build())
            .build();

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    /** {@code {placeholder}} → {@code <placeholder>}. Placeholder names are always {@code [A-Za-z_]}. */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z_]+)}");

    private static final String NS_MSG = "enhancedechest.msg.";
    private static final String NS_GUI = "enhancedechest.gui.";

    /**
     * Locales shipped inside the jar. Their files are extracted on first run so they are always
     * available for auto-detection even before an operator touches them. Keep in sync with the
     * {@code language/} resource folders; operator-added locales are discovered from disk in addition.
     */
    private static final List<String> BUNDLED_LOCALES = List.of("en_US", "vi_VN");

    private final JavaPlugin plugin;
    private final PluginConfig config;
    private final EnhancedEchestTranslator translator = new EnhancedEchestTranslator();
    private String locale;

    /**
     * Per-(viewer locale, key) cache of already-rendered Components for the <b>zero-argument</b> locale
     * lookups used by the Dialog API / inventory-item surfaces (see the {@code get(Locale,…)} overloads).
     * Those surfaces don't auto-render, so we render eagerly — and dialogs are rebuilt on essentially every
     * navigation click, each pulling a dozen-plus static labels, so without this every rebuild re-parses
     * the same strings (the reason the single-locale build kept a parsed-Component cache). Argument-bearing
     * lookups are data-dependent and never cached. Cleared on every (re)load so a changed locale/file is
     * never served stale. Bounded by (distinct viewer locales) × (static keys).
     */
    private final Map<Locale, Map<String, Component>> renderCache = new ConcurrentHashMap<>();

    public LanguageManager(JavaPlugin plugin, PluginConfig config, String locale) {
        this.plugin = plugin;
        this.config = config;
        this.locale = locale;
        load();
    }

    public void reload(String locale) {
        this.locale = locale;
        load();
    }

    /** The Adventure translator to register on the {@code GlobalTranslator} (once, in the plugin). */
    public EnhancedEchestTranslator translator() {
        return translator;
    }

    // ------------------------------------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------------------------------------

    private void load() {
        Map<String, Map<Locale, String>> table = new HashMap<>();
        List<Locale> availableLocales = new ArrayList<>();
        boolean configuredFound = false;

        for (String folder : discoverLocaleFolders()) {
            String base = "language/" + folder + "/";

            // Extract + migrate only locales that actually ship in the jar; operator-added folders are
            // used verbatim from disk (getResource == null, so saveDefault/migrate would have nothing to do).
            if (plugin.getResource(base + "messages.yml") != null) {
                saveDefault(base + "messages.yml");
                saveDefault(base + "gui.yml");
                migrateLanguageFile(base + "messages.yml", ConfigMigrations.MESSAGES);
                migrateLanguageFile(base + "gui.yml", ConfigMigrations.GUI);
            }

            FileConfiguration messages = loadFile(base + "messages.yml");
            FileConfiguration gui = loadFile(base + "gui.yml");

            Locale loc = toLocale(folder);
            availableLocales.add(loc);
            if (folder.equalsIgnoreCase(locale)) {
                configuredFound = true;
            }

            String prefixMm = toMiniMessage(messages.getString("prefix", ""));
            ingest(table, loc, NS_MSG, messages, prefixMm);
            ingest(table, loc, NS_GUI, gui, prefixMm);
        }

        if (!configuredFound) {
            plugin.getSLF4JLogger().warn("Locale '{}' not found, falling back to en_US", locale);
        }

        Locale fallback = toLocale(configuredFound ? locale : "en_US");
        boolean autoDetect = config == null || config.isAutoDetectLanguage();
        translator.apply(table, availableLocales, fallback, autoDetect);

        // The table/fallback/toggle just changed, so any previously rendered Component is now potentially
        // stale (a locale switch, an edited file, or an auto-detect flip).
        renderCache.clear();
    }

    /** Bundled locales unioned with any {@code language/<name>/} folder present on disk. */
    private List<String> discoverLocaleFolders() {
        LinkedHashSet<String> names = new LinkedHashSet<>(BUNDLED_LOCALES);
        File langDir = new File(plugin.getDataFolder(), "language");
        File[] subs = langDir.listFiles(File::isDirectory);
        if (subs != null) {
            for (File dir : subs) {
                if (new File(dir, "messages.yml").exists() || new File(dir, "gui.yml").exists()) {
                    names.add(dir.getName());
                }
            }
        }
        return new ArrayList<>(names);
    }

    /** Adds every leaf string of {@code cfg} to {@code table} under {@code ns + dottedKey} for {@code loc}. */
    private void ingest(Map<String, Map<Locale, String>> table, Locale loc, String ns,
                        FileConfiguration cfg, String prefixMm) {
        for (String key : cfg.getKeys(true)) {
            if (!cfg.isString(key)) continue;
            String normalized = normalize(cfg.getString(key), prefixMm);
            table.computeIfAbsent(ns + key, k -> new HashMap<>()).put(loc, normalized);
        }
    }

    /**
     * Normalizes a raw YAML value into a MiniMessage string: convert legacy to MiniMessage, inline the
     * (already-normalized) prefix, then turn {@code {placeholder}} tokens into {@code <placeholder>} tags.
     */
    private String normalize(String raw, String prefixMm) {
        String mm = toMiniMessage(raw);
        if (mm.contains("{prefix}")) {
            mm = mm.replace("{prefix}", prefixMm);
        }
        return PLACEHOLDER.matcher(mm).replaceAll("<$1>");
    }

    /**
     * Auto-detects format the same way the plugin always has — a {@code '<'} means the string is already
     * MiniMessage; otherwise it is legacy {@code &} codes, converted once here to an equivalent
     * MiniMessage string (colours, {@code &#RRGGBB} hex, decorations, resets).
     */
    private String toMiniMessage(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        return raw.contains("<") ? raw : MINI.serialize(LEGACY.deserialize(raw));
    }

    private static Locale toLocale(String folder) {
        Locale parsed = Translator.parseLocale(folder);
        return parsed != null ? parsed : Locale.US;
    }

    private void saveDefault(String path) {
        File file = new File(plugin.getDataFolder(), path);
        if (!file.exists()) {
            plugin.saveResource(path, false);
        }
    }

    private void migrateLanguageFile(String relativePath, java.util.List<YamlMigrator.Rename> renames) {
        YamlMigrator.migrate(
                new File(plugin.getDataFolder(), relativePath),
                plugin.getResource(relativePath),
                renames,
                plugin.getSLF4JLogger()
        );
    }

    private FileConfiguration loadFile(String relativePath) {
        File file = new File(plugin.getDataFolder(), relativePath);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        InputStream stream = plugin.getResource(relativePath);
        if (stream != null) {
            config.setDefaults(YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)));
        }
        return config;
    }

    // ------------------------------------------------------------------------------------------------
    // Lookups (all return translatable Components resolved per-viewer by EnhancedEchestTranslator)
    // ------------------------------------------------------------------------------------------------

    /**
     * Resolves a message key. Optional {@code replacements} are name/value pairs bound to
     * {@code <name>} argument tags in the string. The {@code {prefix}} is baked in at load time.
     */
    public Component get(String key, String... replacements) {
        return translatable(NS_MSG + key, replacements);
    }

    /**
     * Resolves a GUI/dialog label from gui.yml (no prefix). Same {@code {placeholder}} → argument
     * substitution as {@link #get}. Used for the {@code /ec} list dialog and inventory-menu labels.
     */
    public Component getGui(String key, String... replacements) {
        return translatable(NS_GUI + key, replacements);
    }

    /**
     * Like {@link #get} but binds a single argument to a ready-made {@link ComponentLike} rather than a
     * plain string — for values that must carry their own formatting or interactivity (e.g. the update
     * download link, whose click event a plain-text argument could not hold).
     */
    public Component getRich(String key, String name, ComponentLike value) {
        return Component.translatable(NS_MSG + key, Argument.component(name, value));
    }

    /**
     * Locale-resolved variants of {@link #get}/{@link #getGui}/{@link #getChestLabel}, returning a
     * component already rendered for {@code locale} instead of a deferred translatable.
     *
     * <p>Needed for surfaces Paper does <b>not</b> run through the {@code GlobalTranslator} on the way to
     * the client — the <b>Dialog API</b> and inventory <b>item</b> names/lore — where a raw translatable
     * would reach the client and show as its literal key. Chat and inventory <i>titles</i> are rendered by
     * Paper per-viewer, so those keep using the deferred {@link #get}/{@link #getChestLabel} directly.
     * Rendering here is per-viewer, so callers must pass the specific viewer's {@code player.locale()}.
     */
    public Component get(Locale locale, String key, String... replacements) {
        if (replacements.length == 0) {
            return cachedRender(locale, NS_MSG + key);
        }
        return GlobalTranslator.render(get(key, replacements), locale);
    }

    public Component getGui(Locale locale, String key, String... replacements) {
        if (replacements.length == 0) {
            return cachedRender(locale, NS_GUI + key);
        }
        return GlobalTranslator.render(getGui(key, replacements), locale);
    }

    public Component getChestLabel(Locale locale, int index,
                                   @org.jetbrains.annotations.Nullable String customName, ChestKind kind) {
        // Not cached: a custom name is player text and the numbered title carries the index argument, so
        // the result is data-dependent (mirrors the un-cached single-locale getChestTitle).
        return GlobalTranslator.render(getChestLabel(index, customName, kind), locale);
    }

    /** Renders {@code fullKey} for {@code locale} once and reuses it (zero-argument keys only). */
    private Component cachedRender(Locale locale, String fullKey) {
        return renderCache
                .computeIfAbsent(locale, l -> new ConcurrentHashMap<>())
                .computeIfAbsent(fullKey, k -> GlobalTranslator.render(Component.translatable(k), locale));
    }

    private static Component translatable(String fullKey, String... replacements) {
        if (replacements.length < 2) {
            return Component.translatable(fullKey);
        }
        List<ComponentLike> args = new ArrayList<>(replacements.length / 2);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            args.add(Argument.string(replacements[i], replacements[i + 1]));
        }
        return Component.translatable(fullKey, args.toArray(new ComponentLike[0]));
    }

    /**
     * Resolves the inventory/display title for a chest. A non-blank custom name is shown verbatim
     * (player-supplied formatting via {@link #chestName}). Otherwise chest #1 uses the un-numbered base
     * title and chests 2+ use the numbered template — both localized per viewer.
     */
    public Component getChestTitle(int index, @org.jetbrains.annotations.Nullable String customName) {
        if (customName != null && !customName.isBlank()) {
            return chestName(customName);
        }
        if (index <= 1) {
            return Component.translatable(NS_GUI + "enderchest.title");
        }
        return Component.translatable(NS_GUI + "enderchest.title-numbered",
                Argument.string("index", Integer.toString(index)));
    }

    /**
     * Renders a player-supplied chest name to a display {@link Component}. When
     * {@code enderchest.features.rename-colors} is on, colour/hex/gradient formatting is applied —
     * MiniMessage when the name contains {@code <} (via the restricted {@link #NAME_MINI}, so no
     * interactive tags), otherwise legacy {@code &}/{@code &#RRGGBB} codes. When off, the name is shown
     * verbatim. Any parse error falls back to plain text, so a malformed name is never fatal.
     */
    public Component chestName(String name) {
        if (config == null || !config.isRenameColorsEnabled()) {
            return Component.text(name);
        }
        try {
            return name.contains("<") ? NAME_MINI.deserialize(name) : LEGACY.deserialize(name);
        } catch (RuntimeException e) {
            return Component.text(name);
        }
    }

    /**
     * The plain, formatting-stripped text a chest name would <i>display</i> as — used to match against the
     * rename blacklist so colour codes or MiniMessage tags can't be used to smuggle a banned word past the
     * filter (e.g. {@code ad<red>min} or {@code ad&cmin}).
     */
    public String plainChestName(String name) {
        return PLAIN.serialize(chestName(name));
    }

    /**
     * Resolves a chest's display label. Temporary chests get their own dedicated title
     * ({@code enderchest.title-temp}) rather than the numbered "Ender Chest N". Used for inventory
     * window titles and dialog buttons where a chest's kind should be visible.
     */
    public Component getChestLabel(int index, @org.jetbrains.annotations.Nullable String customName, ChestKind kind) {
        if (kind == ChestKind.TEMP) {
            return Component.translatable(NS_GUI + "enderchest.title-temp",
                    Argument.string("index", Integer.toString(index)));
        }
        return getChestTitle(index, customName);
    }
}
