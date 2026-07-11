package com.enhancedechest.config;

import com.enhancedechest.util.DurationFormat;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.Locale;

@Getter
public final class PluginConfig {

    // Language
    private String locale;

    // Ender chest
    private int defaultSize;

    // Chest-management features (global toggles; read live by the dialogs, so volatile for cross-thread
    // visibility after a /ee reload mutates them on the main thread).
    private volatile boolean renameEnabled;
    private volatile boolean iconEnabled;
    private volatile boolean sortEnabled;
    /** Minimum gap between two sorts by the same player, to keep the Sort button from being spammed. */
    private volatile long sortCooldownMillis;
    /**
     * Disallowed words for custom chest names, stored lowercased. A typed name is rejected when its
     * lowercased form contains any of these as a substring. Read live by the rename dialog, so volatile.
     */
    private volatile List<String> renameBlacklist;
    /**
     * Whether players may use colour/hex/gradient formatting in custom chest names ({@code &} codes,
     * {@code &#RRGGBB} hex, and cosmetic MiniMessage tags). Interactive MiniMessage tags are always
     * stripped regardless. Read live by the name renderer, so volatile.
     */
    private volatile boolean renameColorsEnabled;

    // Permission-granted chests (enhancedechest.additional_amount.<count>.slot.<size>)
    private boolean permissionChestsEnabled;

    // Temporary chests (overflow on shrink/delete/expire)
    private long tempExpiryMillis;
    private long expiryCheckIntervalMillis;
    /** Sound played when a player tries to deposit into a take-only temp chest; null when disabled. */
    private Sound tempDenySound;

    // Database — common
    private String databaseType;
    /** How often the in-memory data is written back to the database (also written once at shutdown). */
    private long autosaveIntervalMillis;
    /**
     * Prepended to every table this plugin creates (e.g. {@code echest_enderchests}), so the plugin's
     * data is easy to tell apart from other plugins' tables and safe to keep in a database shared with
     * them. Sanitized to {@code [A-Za-z0-9_]} since it is concatenated directly into DDL/DML — table
     * names can't be bound as JDBC parameters.
     */
    private String tablePrefix;

    // SQLite
    private String sqliteFile;

    // MySQL / MariaDB
    private String dbHost;
    private int dbPort;
    private String dbName;
    private String dbUsername;
    private String dbPassword;
    private int dbPoolSize;

    // Auto-backup (SQLite only)
    private boolean backupEnabled;
    private long backupIntervalMillis;
    private int backupKeep;
    private boolean backupOnStartup;
    private String backupFolder;

    // Migration
    @Setter
    private boolean migrationEnabled;

    public PluginConfig(FileConfiguration config) {
        reload(config);
    }

    public void reload(FileConfiguration config) {
        locale = config.getString("language", "en_US");

        defaultSize = sanitizeSize(config.getInt("enderchest.default-size", 54));

        renameEnabled      = config.getBoolean("enderchest.features.rename", true);
        iconEnabled        = config.getBoolean("enderchest.features.icon", true);
        sortEnabled        = config.getBoolean("enderchest.features.sort", false);
        sortCooldownMillis = parseDuration(config.getString("enderchest.features.sort-cooldown", "10s"), "10s");
        renameBlacklist    = config.getStringList("enderchest.features.rename-blacklist").stream()
                .filter(s -> s != null && !s.isBlank())
                .map(s -> s.toLowerCase(Locale.ROOT).trim())
                .toList();
        renameColorsEnabled = config.getBoolean("enderchest.features.rename-colors", true);

        permissionChestsEnabled = config.getBoolean("permission-chests.enabled", true);

        tempExpiryMillis          = parseDuration(config.getString("temp-enderchest.expiry", "24h"), "24h");
        expiryCheckIntervalMillis = parseDuration(config.getString("temp-enderchest.check-interval", "5m"), "5m");
        tempDenySound             = parseSound(config);

        databaseType = config.getString("database.type", "sqlite");
        tablePrefix  = sanitizeTablePrefix(config.getString("database.table-prefix", "echest_"));
        sqliteFile   = config.getString("database.sqlite-file", "enderchests.db");
        // Clamped to at least 30s so a typo can never turn the autosave into a busy loop.
        autosaveIntervalMillis = Math.max(30_000L,
                parseDuration(config.getString("database.autosave-interval", "5m"), "5m"));

        dbHost     = config.getString("database.host", "localhost");
        dbPort     = config.getInt("database.port", 3306);
        dbName     = config.getString("database.database", "enhancedechest");
        dbUsername = config.getString("database.username", "root");
        dbPassword = config.getString("database.password", "");
        dbPoolSize = config.getInt("database.pool-size", 10);

        backupEnabled        = config.getBoolean("backup.enabled", true);
        backupIntervalMillis = parseDuration(config.getString("backup.interval", "6h"), "6h");
        backupKeep           = config.getInt("backup.keep", 10);
        backupOnStartup      = config.getBoolean("backup.on-startup", false);
        backupFolder         = config.getString("backup.folder", "backups");

        migrationEnabled = config.getBoolean("migration.enabled", false);
    }

    /**
     * Builds the temp-chest deny sound from config. Returns {@code null} when disabled; falls back to
     * the default villager "no" sound if the configured key is malformed.
     */
    private static Sound parseSound(FileConfiguration config) {
        if (!config.getBoolean("temp-enderchest.deny-sound.enabled", true)) {
            return null;
        }
        String rawKey = config.getString("temp-enderchest.deny-sound.key", "minecraft:entity.villager.no");
        Key key;
        try {
            key = Key.key(rawKey);
        } catch (IllegalArgumentException e) {
            key = Key.key("minecraft:entity.villager.no");
        }
        return Sound.sound(key, Sound.Source.MASTER, 1.0f, 1.0f);
    }

    /**
     * Keeps only {@code [A-Za-z0-9_]} from a configured table prefix (table names are concatenated into
     * SQL directly, never bound as a JDBC parameter) and falls back to {@code echest_} if that leaves
     * nothing usable.
     */
    private static String sanitizeTablePrefix(String value) {
        String cleaned = value == null ? "" : value.replaceAll("[^A-Za-z0-9_]", "");
        return cleaned.isEmpty() ? "echest_" : cleaned;
    }

    /** Parses a duration string, falling back to {@code fallback} (and ultimately a safe value) on error. */
    private static long parseDuration(String value, String fallback) {
        try {
            return DurationFormat.parse(value);
        } catch (IllegalArgumentException e) {
            return DurationFormat.parse(fallback);
        }
    }

    /**
     * True when a proposed custom chest name is disallowed by {@code enderchest.features.rename-blacklist}.
     * Matching is case-insensitive substring: a name is blocked if its lowercased form contains any
     * configured word. A null/blank name (clearing the name) is always allowed.
     */
    public boolean isNameBlocked(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        List<String> blacklist = renameBlacklist;
        if (blacklist.isEmpty()) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        for (String banned : blacklist) {
            if (lower.contains(banned)) {
                return true;
            }
        }
        return false;
    }

    /** True if size is a positive multiple of 9 and at most 54. */
    public static boolean isValidSize(int size) {
        return size >= 9 && size <= 54 && size % 9 == 0;
    }

    /** Clamps an arbitrary configured size to the nearest valid value (9..54, multiple of 9). */
    public static int sanitizeSize(int size) {
        int rounded = Math.round(size / 9.0f) * 9;
        return Math.max(9, Math.min(54, rounded == 0 ? 9 : rounded));
    }
}
