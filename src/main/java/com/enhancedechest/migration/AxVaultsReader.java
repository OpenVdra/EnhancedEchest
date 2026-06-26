package com.enhancedechest.migration;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reads an AxVaults (Artillex-Studios) database directly and decodes its vault contents into
 * Bukkit {@link ItemStack} arrays, ready to be re-encoded into EnhancedEchest's own storage.
 *
 * <p>AxVaults stores one row per (player, vault) in {@code axvaults_data(id, uuid, storage, icon)}.
 * The {@code storage} blob is the AxAPI {@code Serializers.ITEM_ARRAY} framing — a big-endian
 * {@code int} slot count, then per slot a {@code unsigned short} byte length (0 = empty) followed by
 * that many bytes. Each item's bytes are gzip-compressed NBT with a {@code DataVersion} tag, exactly
 * what Paper's {@link ItemStack#serializeAsBytes()} produces, so each one decodes via
 * {@link ItemStack#deserializeBytes(byte[])} (which also runs the DataFixerUpper across versions).
 *
 * <p>Supports both AxVaults file backends: SQLite ({@code data.db}) and H2 ({@code data.mv.db}).
 * SQLite is opened read-only so it can be read while the source server still holds the file; an H2
 * file is exclusively locked while AxVaults runs, so H2 migration requires the source server to be
 * stopped first.
 *
 * <p>Not thread-safe; create, use and {@link #close()} on a single thread (the migration runs on the
 * shared DB executor). Item (de)serialization is read-only against the frozen server registries and
 * is safe off the main thread.
 */
public final class AxVaultsReader implements AutoCloseable {

    /** A single AxVaults vault as decoded for migration. */
    public record VaultRow(UUID owner, int id, ItemStack[] items, @Nullable String iconMaterial) {}

    /** Guards against a corrupt slot count blowing up memory. AxVaults vaults top out at 54 slots. */
    private static final int MAX_SLOTS = 1024;

    private final Connection conn;
    private final String backend;
    private final Logger log;

    private AxVaultsReader(Connection conn, String backend, Logger log) {
        this.conn = conn;
        this.backend = backend;
        this.log = log;
    }

    /** The detected backend name ("SQLite" or "H2"), for logging. */
    public String backend() {
        return backend;
    }

    /**
     * Opens the AxVaults database found in {@code axVaultsFolder} (typically {@code plugins/AxVaults}).
     * Prefers SQLite ({@code data.db}) when present, otherwise H2 ({@code data.mv.db}).
     *
     * @throws IllegalStateException if no AxVaults database file is found in the folder
     * @throws Exception             if the driver is missing or the connection fails
     */
    public static AxVaultsReader open(Path axVaultsFolder, Logger log) throws Exception {
        Path sqlite = axVaultsFolder.resolve("data.db");
        Path h2 = axVaultsFolder.resolve("data.mv.db");

        if (Files.exists(sqlite)) {
            Class.forName("org.sqlite.JDBC");
            // open_mode=1 => SQLITE_OPEN_READONLY; lets us read while the source server is live.
            String url = "jdbc:sqlite:" + sqlite.toAbsolutePath() + "?open_mode=1";
            return new AxVaultsReader(DriverManager.getConnection(url), "SQLite", log);
        }

        if (Files.exists(h2)) {
            loadH2Driver();
            // Strip the ".mv.db" suffix: H2 URLs name the base file. Read-only, no file lock.
            String base = h2.toAbsolutePath().toString();
            base = base.substring(0, base.length() - ".mv.db".length());
            String url = "jdbc:h2:file:" + base + ";ACCESS_MODE_DATA=r;FILE_LOCK=NO;IFEXISTS=TRUE";
            return new AxVaultsReader(DriverManager.getConnection(url, "", ""), "H2", log);
        }

        throw new IllegalStateException("No AxVaults database found in " + axVaultsFolder
                + " (looked for data.db / data.mv.db)");
    }

    /** Loads the relocated, shaded H2 driver; falls back to the unrelocated name for dev runs. */
    private static void loadH2Driver() throws ClassNotFoundException {
        try {
            Class.forName("com.enhancedechest.libs.h2.Driver");
        } catch (ClassNotFoundException e) {
            Class.forName("org.h2.Driver");
        }
    }

    /** Reads and decodes every player's vaults, keyed by owner UUID and ordered by vault id. */
    public Map<UUID, List<VaultRow>> readAll() throws Exception {
        Map<UUID, List<VaultRow>> byOwner = new LinkedHashMap<>();
        String sql = "SELECT id, uuid, storage, icon FROM axvaults_data ORDER BY uuid, id;";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                VaultRow row = readRow(rs);
                if (row != null) {
                    byOwner.computeIfAbsent(row.owner(), k -> new ArrayList<>()).add(row);
                }
            }
        }
        return byOwner;
    }

    /** Reads and decodes a single player's vaults, ordered by vault id. Empty if the player has none. */
    public List<VaultRow> read(UUID owner) throws Exception {
        List<VaultRow> rows = new ArrayList<>();
        String sql = "SELECT id, uuid, storage, icon FROM axvaults_data WHERE uuid = ? ORDER BY id;";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, owner.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    VaultRow row = readRow(rs);
                    if (row != null) rows.add(row);
                }
            }
        }
        return rows;
    }

    /** Maps one result-set row to a {@link VaultRow}, or null if it is unreadable (logged, skipped). */
    private @Nullable VaultRow readRow(ResultSet rs) throws Exception {
        int id = rs.getInt("id");
        String uuidStr = rs.getString("uuid");
        byte[] blob = rs.getBytes("storage");
        String icon = rs.getString("icon");

        UUID owner;
        try {
            owner = UUID.fromString(uuidStr);
        } catch (Exception e) {
            log.warn("[AxVaults] Skipping vault #{} with unparseable owner UUID '{}'", id, uuidStr);
            return null;
        }

        try {
            ItemStack[] items = decodeStorage(blob);
            return new VaultRow(owner, id, items, icon);
        } catch (Exception e) {
            log.warn("[AxVaults] Failed to decode vault #{} of {} — skipping this vault", id, owner, e);
            return null;
        }
    }

    /**
     * Decodes the AxVaults {@code storage} blob into an item array. Empty slots come back as null;
     * the caller fits the array into a chest. Returns an empty array for a null/empty blob.
     */
    static ItemStack[] decodeStorage(@Nullable byte[] blob) throws Exception {
        if (blob == null || blob.length == 0) {
            return new ItemStack[0];
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(blob))) {
            int length = in.readInt();
            if (length < 0 || length > MAX_SLOTS) {
                throw new IllegalArgumentException("Unreasonable slot count " + length + " in AxVaults blob");
            }
            ItemStack[] items = new ItemStack[length];
            for (int i = 0; i < length; i++) {
                int size = in.readUnsignedShort();
                if (size == 0) {
                    items[i] = null; // empty / air slot
                    continue;
                }
                byte[] itemBytes = new byte[size];
                in.readFully(itemBytes);
                items[i] = ItemStack.deserializeBytes(itemBytes);
            }
            return items;
        }
    }

    @Override
    public void close() {
        try {
            conn.close();
        } catch (Exception e) {
            log.warn("[AxVaults] Failed to close source database connection", e);
        }
    }
}
