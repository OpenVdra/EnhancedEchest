package com.enhancedechest.migration;

import com.enhancedechest.storage.EnderChestStorage.RawChestRow;
import com.enhancedechest.storage.EnderChestStorage.RawPlayerRow;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Opens an EnhancedEchest database of <i>any</i> supported dialect read-only and reads its
 * {@code players} and {@code enderchests} tables (under the active install's {@code
 * database.table-prefix}) verbatim, for the {@code /ee import} DB→DB copy. Unlike the vault-plugin
 * migrators this does no item decoding: rows are read column-for-column (including the raw
 * {@code container_data} bytes) and handed straight to
 * {@link com.enhancedechest.storage.EnderChestStorage#importRows}.
 *
 * <p>The schema is assumed to already be at the current version — the docs instruct the admin to load
 * the source with this plugin version first. No {@code SchemaMigrator} is run here; a missing column
 * surfaces as a {@link java.sql.SQLException}, which the service turns into a "source schema outdated"
 * message rather than silently importing partial data. The source's tables are assumed to use the same
 * {@code table-prefix} as the active destination, since both are the same plugin's schema.
 *
 * <p>Not thread-safe; open, {@link #readAll()} and {@link #close()} on one thread (the shared DB
 * executor). Uses the same shaded/relocated drivers as the storage layer, registered via
 * {@link Class#forName} so {@link DriverManager} can find them under Paper's plugin classloader.
 */
public final class SourceDatabaseReader implements AutoCloseable {

    /** The two tables read from the source, kept in memory (data is modest — see the import plan). */
    public record Data(List<RawPlayerRow> players, List<RawChestRow> chests) {}

    private final Connection conn;
    private final String backend;
    private final Logger log;
    private final String sqlReadPlayers;
    private final String sqlReadChests;

    private SourceDatabaseReader(Connection conn, String backend, Logger log, String tablePrefix) {
        this.conn = conn;
        this.backend = backend;
        this.log = log;
        this.sqlReadPlayers =
                "SELECT player_uuid, username, edit_mode, applied_default_size FROM " + tablePrefix + "players";
        this.sqlReadChests =
                "SELECT player_uuid, chest_index, size, custom_name, is_primary, container_data, migrated, " +
                "last_updated, kind, expires_at, icon FROM " + tablePrefix + "enderchests";
    }

    /** The source backend name (e.g. "SQLite", "MySQL"), for logging. */
    public String backend() {
        return backend;
    }

    /**
     * Opens the source database described by {@code spec}, read-only.
     *
     * @param dataFolder plugin data folder, used to resolve a relative SQLite file path
     * @param tablePrefix the active install's {@code database.table-prefix}, assumed shared with the source
     * @throws IllegalStateException if the SQLite source file does not exist
     * @throws IllegalArgumentException if the source type is unsupported
     * @throws Exception if the driver is missing or the connection fails
     */
    public static SourceDatabaseReader open(SourceSpec spec, Path dataFolder, Logger log, String tablePrefix)
            throws Exception {
        String family = SourceSpec.family(spec.type());
        return switch (family) {
            case "sqlite" -> openSqlite(spec, dataFolder, log, tablePrefix);
            case "mysql" -> openRemote(spec, "MySQL/MariaDB", "com.enhancedechest.libs.mariadb.Driver",
                    "jdbc:mariadb://" + spec.host() + ":" + spec.port() + "/" + spec.database()
                            + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8", log, tablePrefix);
            case "postgres" -> openRemote(spec, "PostgreSQL", "com.enhancedechest.libs.postgresql.Driver",
                    "jdbc:postgresql://" + spec.host() + ":" + spec.port() + "/" + spec.database(), log, tablePrefix);
            default -> throw new IllegalArgumentException("Unsupported source database type: " + spec.type());
        };
    }

    private static SourceDatabaseReader openSqlite(SourceSpec spec, Path dataFolder, Logger log, String tablePrefix)
            throws Exception {
        Path raw = Path.of(spec.sqliteFile() == null ? "" : spec.sqliteFile());
        Path file = (raw.isAbsolute() ? raw : dataFolder.resolve(raw)).toAbsolutePath().normalize();
        if (!Files.exists(file)) {
            throw new IllegalStateException("SQLite source file not found: " + file);
        }
        Class.forName("org.sqlite.JDBC");
        // open_mode=1 => SQLITE_OPEN_READONLY; lets us read even a file another process holds.
        String url = "jdbc:sqlite:" + file + "?open_mode=1";
        Connection conn = DriverManager.getConnection(url);
        setReadOnly(conn, log);
        return new SourceDatabaseReader(conn, "SQLite", log, tablePrefix);
    }

    private static SourceDatabaseReader openRemote(SourceSpec spec, String backend, String driverClass,
                                                   String url, Logger log, String tablePrefix) throws Exception {
        // Registers the relocated driver with DriverManager (its static block self-registers); needed
        // because the ServiceLoader registration isn't discovered under Paper's plugin classloader.
        Class.forName(driverClass);
        Properties props = new Properties();
        props.setProperty("user", spec.username() == null ? "" : spec.username());
        props.setProperty("password", spec.password() == null ? "" : spec.password());
        Connection conn = DriverManager.getConnection(url, props);
        setReadOnly(conn, log);
        return new SourceDatabaseReader(conn, backend, log, tablePrefix);
    }

    /** Best-effort read-only hint; harmless if the driver ignores it (we only ever SELECT anyway). */
    private static void setReadOnly(Connection conn, Logger log) {
        try {
            conn.setReadOnly(true);
        } catch (Exception e) {
            log.debug("[Import] Source connection does not support read-only mode; continuing", e);
        }
    }

    /** Reads both tables in two forward-only queries — no per-player N+1. */
    public Data readAll() throws Exception {
        return new Data(readPlayers(), readChests());
    }

    private List<RawPlayerRow> readPlayers() throws Exception {
        List<RawPlayerRow> rows = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sqlReadPlayers);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(new RawPlayerRow(
                        rs.getString("player_uuid"),
                        rs.getString("username"),
                        rs.getInt("edit_mode"),
                        rs.getInt("applied_default_size")));
            }
        }
        return rows;
    }

    private List<RawChestRow> readChests() throws Exception {
        List<RawChestRow> rows = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sqlReadChests);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                long expires = rs.getLong("expires_at");
                Long expiresAt = rs.wasNull() ? null : expires;
                rows.add(new RawChestRow(
                        rs.getString("player_uuid"),
                        rs.getInt("chest_index"),
                        rs.getInt("size"),
                        rs.getString("custom_name"),
                        rs.getInt("is_primary"),
                        rs.getBytes("container_data"),
                        rs.getInt("migrated"),
                        rs.getLong("last_updated"),
                        rs.getInt("kind"),
                        expiresAt,
                        rs.getString("icon")));
            }
        }
        return rows;
    }

    @Override
    public void close() {
        try {
            conn.close();
        } catch (Exception e) {
            log.warn("[Import] Failed to close source database connection", e);
        }
    }
}
