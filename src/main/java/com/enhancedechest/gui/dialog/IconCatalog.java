package com.enhancedechest.gui.dialog;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.object.ObjectContents;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Catalog of pickable chest icons, backed by the server's {@link Material} registry, plus helpers to
 * render a chosen icon as an Adventure <i>sprite object component</i> — the icon shown <b>inside</b>
 * Dialog action buttons (since {@code 1.21.9} / Adventure 4.25.0, no resource pack required).
 *
 * <p><b>Atlases.</b> A sprite object draws one stitched texture from a named atlas. Vanilla splits
 * these: block textures live in {@code minecraft:blocks} under {@code block/<id>}, item textures in
 * {@code minecraft:items} under {@code item/<id>}. The icon a material shows in the inventory is its
 * item texture when one exists (e.g. doors, boats), otherwise its block texture (e.g. planks). Many
 * derived blocks (slabs, stairs, fences, walls, signs, …) have <i>no</i> flat texture at all — they are
 * drawn from a block model — so they cannot be a sprite and are simply not offered as icons.
 *
 * <p><b>No missing-texture boxes.</b> The exact set of flat textures that exist in the client is
 * bundled at {@code icons/valid-icon-sprites.txt} (generated from the client jar). A material is only
 * catalogued, and a stored icon only rendered, when its resolved sprite is in that set — so the picker
 * never shows a purple/black missing-texture sprite.
 *
 * <p><b>Performance.</b> The valid-sprite set and the full, name-sorted catalog (~2k entries, each with
 * a precomputed display name, lower-cased search name and a reusable sprite {@link Component}) are built
 * <b>once</b>, lazily, and cached immutably. Building any picker page is then just a sub-list — no
 * Material scan and no component allocation per render. Stored-icon sprites are memoized by key too.
 *
 * <p><b>Client-locale search.</b> {@link #search(String, Locale)} also matches a query against the
 * item's <i>localized</i> name for the viewer's client locale, e.g. a Vietnamese client can type
 * "kim cương" and still find Diamond. This only works for locales with a bundled name table at
 * {@code icons/lang/<locale>.json} (lowercase Minecraft locale id, e.g. {@code en_us.json},
 * {@code vi_vn.json}) — currently just the two locales this plugin's own messages support. For any
 * other client locale, search silently falls back to the English name only (the label itself still
 * renders correctly via {@link Component#translatable}, regardless of whether search covers it — see
 * {@link Entry#name()}). Each table is generated once from Mojang's official
 * {@code assets/minecraft/lang/<locale>.json}, filtered down to just {@code item.minecraft.*} /
 * {@code block.minecraft.*} keys. To add another locale: fetch that file (via the version manifest →
 * asset index → {@code resources.download.minecraft.net}, or extract {@code en_us.json} straight out of
 * the client jar, since it alone ships inside the jar rather than as a separate asset object), filter it
 * the same way, and drop it in as {@code icons/lang/<locale>.json} — no code change needed. See
 * {@code docs/} for the full runbook.
 *
 * <p><b>Server-owner override, no rebuild needed.</b> {@link #setExternalLangDir} points the catalog at
 * {@code plugins/EnhancedEchest/icons/lang/} (wired once from {@code EnhancedEchestPlugin#onEnable}); a
 * same-named file dropped there is preferred over the bundled classpath resource, so an admin can add a
 * locale this plugin doesn't ship, or override/fix a bundled one, purely by dropping a JSON file in while
 * the server is running — no plugin update required. Per-locale results are cached after first lookup
 * (like the bundled path), so a file added or edited after the server started only takes effect once
 * {@link #reloadLocaleNames()} is called, which {@code /ee reload} does automatically.
 */
public final class IconCatalog {

    private static final String VALID_SPRITES_RESOURCE = "/icons/valid-icon-sprites.txt";
    private static final String LANG_RESOURCE_PREFIX = "/icons/lang/";
    private static final Key ATLAS_BLOCKS = Key.key("minecraft", "blocks");
    private static final Key ATLAS_ITEMS = Key.key("minecraft", "items");
    /** computeIfAbsent-cacheable stand-in for "no bundled lang file for this locale". */
    private static final Map<String, String> NO_LOCALE_NAMES = Map.of();

    /**
     * One pickable icon: a material with its precomputed display name, reusable sprite component, and
     * a translatable name component. {@code displayName}/{@code lowerName} are a server-derived English
     * name used only for search matching (the server can't know what text a translation key resolves to
     * client-side); {@code name} is what's actually shown to the player — a {@code Component.translatable}
     * that the Minecraft client resolves using its own language file, so it renders in the viewer's
     * client-side language exactly like the item's name in an inventory tooltip.
     */
    public record Entry(Material material, String key, String displayName, String lowerName, Component sprite,
                         Component name) {}

    private static volatile List<Entry> catalog;
    private static volatile Set<String> validSprites;
    private static final Map<String, Component> SPRITE_CACHE = new ConcurrentHashMap<>();
    /** Per-locale {@code translationKey -> lowercased localized name}, lazily loaded and cached. */
    private static final Map<String, Map<String, String>> LOCALE_NAMES = new ConcurrentHashMap<>();
    private static volatile @Nullable Path externalLangDir;
    private static volatile @Nullable Logger externalLangLogger;

    private IconCatalog() {}

    /**
     * Points locale-name lookups at an on-disk override directory (an admin/dev drop-in folder), checked
     * before the bundled classpath resource for each locale. Called once from
     * {@code EnhancedEchestPlugin#onEnable} with {@code plugins/EnhancedEchest/icons/lang/}; the directory
     * itself need not exist yet, it's only touched lazily on the first search that needs a given locale.
     */
    public static void setExternalLangDir(Path dir, Logger logger) {
        externalLangDir = dir;
        externalLangLogger = logger;
    }

    /**
     * Drops every cached locale-name table so the next search re-reads from disk/classpath, picking up any
     * file added or edited since the server started. Cheap to call (no work happens until a search actually
     * needs a locale again) — wired into {@code /ee reload}.
     */
    public static void reloadLocaleNames() {
        LOCALE_NAMES.clear();
    }

    /** Lazily builds and returns the immutable, name-sorted list of pickable icons. */
    public static List<Entry> all() {
        List<Entry> local = catalog;
        if (local == null) {
            synchronized (IconCatalog.class) {
                local = catalog;
                if (local == null) {
                    local = build();
                    catalog = local;
                }
            }
        }
        return local;
    }

    /**
     * Filters the catalog by a case-insensitive substring, matched against the server-derived English
     * name and — when a bundled lang table exists for {@code viewerLocale} — the item's client-localized
     * name too (blank query = full list). {@code viewerLocale} may be null (no localized matching, same
     * as the old English-only behavior).
     */
    public static List<Entry> search(@Nullable String query, @Nullable Locale viewerLocale) {
        if (query == null || query.isBlank()) {
            return all();
        }
        String q = query.trim().toLowerCase(Locale.ROOT);
        Map<String, String> localized = viewerLocale == null ? NO_LOCALE_NAMES : localeNames(viewerLocale);
        List<Entry> out = new ArrayList<>();
        for (Entry e : all()) {
            if (e.lowerName().contains(q)) {
                out.add(e);
                continue;
            }
            String localName = localized.get(e.material().translationKey());
            if (localName != null && localName.contains(q)) {
                out.add(e);
            }
        }
        return out;
    }

    /** Sprite object component for a stored icon key, or null if the key is null/unknown/unrenderable. */
    public static @Nullable Component sprite(@Nullable String materialKey) {
        Material m = material(materialKey);
        if (m == null) {
            return null;
        }
        // computeIfAbsent can't cache nulls; guard the lookup separately so unrenderable keys return null.
        Component cached = SPRITE_CACHE.get(m.getKey().toString());
        if (cached != null) {
            return cached;
        }
        Component built = spriteFor(m);
        if (built != null) {
            SPRITE_CACHE.put(m.getKey().toString(), built);
        }
        return built;
    }

    /** A real item icon for the detail-dialog body, or null if the key is null/unknown/not an item. */
    public static @Nullable ItemStack item(@Nullable String materialKey) {
        Material m = material(materialKey);
        if (m == null || !m.isItem()) {
            return null;
        }
        return ItemStack.of(m);
    }

    /** Resolves a material key string (e.g. {@code minecraft:diamond}) to a Material, or null. */
    public static @Nullable Material material(@Nullable String materialKey) {
        if (materialKey == null || materialKey.isBlank()) {
            return null;
        }
        return Material.matchMaterial(materialKey);
    }

    private static List<Entry> build() {
        List<Entry> list = new ArrayList<>();
        for (Material m : Material.values()) {
            // Only real, current item forms make sensible icons; skip legacy duplicates and AIR.
            if (m.isLegacy() || m == Material.AIR || !m.isItem()) {
                continue;
            }
            Component sprite = spriteFor(m);
            if (sprite == null) {
                continue; // no flat texture exists → would render as a missing-texture box, so omit it.
            }
            String display = displayName(m);
            Component name = Component.translatable(m);
            list.add(new Entry(m, m.getKey().toString(), display, display.toLowerCase(Locale.ROOT), sprite, name));
        }
        list.sort(Comparator.comparing(Entry::displayName));
        return List.copyOf(list);
    }

    /**
     * Localized {@code translationKey -> lowercased name} table for one client locale, cached forever
     * after first lookup (including an empty map for locales with no bundled table, so a repeated miss
     * doesn't re-touch the classloader). Locale keys are lowercased Minecraft-style ids ({@code en_us},
     * {@code vi_vn}, from {@link Locale#toString()}) matching the bundled {@code icons/lang/*.json} files.
     */
    private static Map<String, String> localeNames(Locale locale) {
        String key = locale.toString().toLowerCase(Locale.ROOT);
        return LOCALE_NAMES.computeIfAbsent(key, IconCatalog::loadLocaleNames);
    }

    private static Map<String, String> loadLocaleNames(String localeKey) {
        Path dir = externalLangDir;
        if (dir != null) {
            Path external = dir.resolve(localeKey + ".json");
            if (Files.isRegularFile(external)) {
                try (Reader reader = Files.newBufferedReader(external, StandardCharsets.UTF_8)) {
                    return parseLocaleNames(reader);
                } catch (IOException | RuntimeException e) {
                    // Admin-provided file: a mistake here (bad JSON, wrong encoding) must not break the
                    // picker for every player, so warn and fall through to the bundled table instead of
                    // throwing. reloadLocaleNames() (/ee reload) re-attempts once the file is fixed.
                    Logger log = externalLangLogger;
                    if (log != null) {
                        log.warn("Failed to load {}, falling back to the bundled table (if any): {}",
                                external, e.toString());
                    }
                }
            }
        }
        String resource = LANG_RESOURCE_PREFIX + localeKey + ".json";
        try (InputStream in = IconCatalog.class.getResourceAsStream(resource)) {
            if (in == null) {
                return NO_LOCALE_NAMES; // no bundled table for this locale — search falls back to English.
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return parseLocaleNames(reader);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load icon lang file " + resource, e);
        }
    }

    private static Map<String, String> parseLocaleNames(Reader reader) {
        JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
        Map<String, String> map = new HashMap<>(obj.size() * 2);
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
            map.put(e.getKey(), e.getValue().getAsString().toLowerCase(Locale.ROOT));
        }
        return Map.copyOf(map);
    }

    /**
     * Builds the sprite object component for a material, or null if it has no flat texture. Prefers the
     * item texture (the inventory icon for things like doors and boats) over the block texture.
     */
    private static @Nullable Component spriteFor(Material m) {
        NamespacedKey nk = m.getKey();
        String id = nk.getKey();
        Set<String> valid = validSprites();
        if (valid.contains("item/" + id)) {
            return Component.object(ObjectContents.sprite(ATLAS_ITEMS, Key.key(nk.getNamespace(), "item/" + id)));
        }
        if (valid.contains("block/" + id)) {
            return Component.object(ObjectContents.sprite(ATLAS_BLOCKS, Key.key(nk.getNamespace(), "block/" + id)));
        }
        return null;
    }

    /** Lazily loads the bundled set of flat texture names ({@code block/<id>} / {@code item/<id>}). */
    private static Set<String> validSprites() {
        Set<String> local = validSprites;
        if (local == null) {
            synchronized (IconCatalog.class) {
                local = validSprites;
                if (local == null) {
                    local = loadValidSprites();
                    validSprites = local;
                }
            }
        }
        return local;
    }

    private static Set<String> loadValidSprites() {
        Set<String> set = new HashSet<>(4096);
        try (InputStream in = IconCatalog.class.getResourceAsStream(VALID_SPRITES_RESOURCE)) {
            if (in == null) {
                return set; // resource missing → empty catalog rather than a wall of missing textures.
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) {
                        set.add(trimmed);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load icon sprite list " + VALID_SPRITES_RESOURCE, e);
        }
        return set;
    }

    /** {@code acacia_boat} -> {@code Acacia Boat}. */
    private static String displayName(Material m) {
        String[] parts = m.getKey().getKey().split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }
}
