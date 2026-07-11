package com.enhancedechest.storage.sql;

import com.enhancedechest.storage.EnderChestStorage.RawChestRow;
import com.enhancedechest.storage.EnderChestStorage.RawPlayerRow;
import com.enhancedechest.storage.StorageBackend;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC-backed {@link StorageBackend} shared by all three dialects.
 *
 * <p>Since the in-memory {@code CachedStorage} became the authoritative store, this class holds
 * <b>no per-row write DML</b>: its job is schema creation/migration, per-player reads on a cache miss,
 * the batched flush of dirty rows (autosave/quit/shutdown), the handful of whole-database read
 * questions the cache cannot answer alone (expiry candidates, chest count, name resolution), and the
 * verbatim {@code /ee import} copy. All SQL below is standard and valid for SQLite, MySQL/MariaDB and PostgreSQL; only the
 * schema-creation statements differ per dialect and are injected by subclasses. Flush upserts are
 * portable delete-then-insert, so no dialect-specific {@code ON CONFLICT} / {@code ON DUPLICATE KEY}
 * is needed.
 */
public abstract class AbstractSqlStorage implements StorageBackend {

    /** Rows per JDBC batch flush, to bound memory while still collapsing round-trips. */
    private static final int BATCH_SIZE = 1000;

    protected final HikariDataSource dataSource;

    /** Prepended to every table name (see {@link com.enhancedechest.config.PluginConfig#getTablePrefix()}). */
    protected final String tablePrefix;

    // Dialect-specific schema-creation statements injected by subclasses to avoid calling abstract
    // methods from the constructor (which would access uninitialized subclass state). Each entry is
    // one CREATE TABLE; they are executed in order on init().
    private final String[] schemaStatements;

    private final String sqlDeleteChest;
    private final String sqlDeletePlayer;
    private final String sqlInsertPlayer;
    private final String sqlInsertChest;
    private final String sqlLoadPlayerChests;
    private final String sqlLoadAllPlayers;
    private final String sqlLoadOnePlayer;
    private final String sqlFindExpired;
    private final String sqlCountChests;
    private final String sqlNameFind;

    protected AbstractSqlStorage(HikariConfig config, String tablePrefix, String... schemaStatements) {
        this.dataSource = new HikariDataSource(config);
        this.tablePrefix = tablePrefix;
        this.schemaStatements = schemaStatements;

        String chests = tablePrefix + "enderchests";
        String players = tablePrefix + "players";

        this.sqlDeleteChest = "DELETE FROM " + chests + " WHERE player_uuid = ? AND chest_index = ?";
        this.sqlDeletePlayer = "DELETE FROM " + players + " WHERE player_uuid = ?";
        // Verbatim full-row inserts: every column is written from the row as-is (no defaults relied on).
        // Shared by the /ee import copy and the flush write-back, reused as one batched PreparedStatement
        // per table under a single transaction.
        this.sqlInsertPlayer = "INSERT INTO " + players
                + " (player_uuid, username, edit_mode, applied_default_size) VALUES (?, ?, ?, ?)";
        this.sqlInsertChest = "INSERT INTO " + chests + " "
                + "(player_uuid, chest_index, size, custom_name, is_primary, container_data, migrated, last_updated, kind, expires_at, icon) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        this.sqlLoadPlayerChests = "SELECT player_uuid, chest_index, size, custom_name, is_primary, container_data, migrated, "
                + "last_updated, kind, expires_at, icon FROM " + chests + " WHERE player_uuid = ?";
        this.sqlLoadAllPlayers = "SELECT player_uuid, username, edit_mode, applied_default_size FROM " + players;
        this.sqlLoadOnePlayer = sqlLoadAllPlayers + " WHERE player_uuid = ?";
        this.sqlFindExpired = "SELECT player_uuid, chest_index, kind FROM " + chests + " "
                + "WHERE expires_at IS NOT NULL AND expires_at <= ?";
        this.sqlCountChests = "SELECT COUNT(*) FROM " + chests;
        this.sqlNameFind = "SELECT player_uuid FROM " + players + " WHERE username IS NOT NULL AND LOWER(username) = LOWER(?)";
    }

    @Override
    public void init() {
        // Renames any pre-existing bare-named tables (enderchests/players/schema_meta, or tables from a
        // previously-configured prefix) to the current prefix before CREATE TABLE IF NOT EXISTS below can
        // create empty ones under the new names — see SchemaMigrator.renameLegacyTables.
        SchemaMigrator.renameLegacyTables(dataSource, tablePrefix);
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            for (String ddl : schemaStatements) {
                stmt.execute(ddl);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database schema", e);
        }
        // Bring an existing (older) database up to the current schema version. On a fresh install the
        // CREATE statements above already carry every column, so the migrator's guarded steps no-op.
        SchemaMigrator.migrate(dataSource, tablePrefix);
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    // ---- reads (per-player cache-miss loads + the few whole-database questions) ----

    @Override
    public List<RawPlayerRow> loadAllPlayers() {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlLoadAllPlayers);
             ResultSet rs = ps.executeQuery()) {
            List<RawPlayerRow> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(readPlayerRow(rs));
            }
            return rows;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load all player rows", e);
        }
    }

    @Override
    public List<RawChestRow> loadChests(String playerUuid) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlLoadPlayerChests)) {
            ps.setString(1, playerUuid);
            try (ResultSet rs = ps.executeQuery()) {
                List<RawChestRow> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(readChestRow(rs));
                }
                return rows;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load chest rows for " + playerUuid, e);
        }
    }

    @Override
    public RawPlayerRow loadPlayer(String playerUuid) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlLoadOnePlayer)) {
            ps.setString(1, playerUuid);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? readPlayerRow(rs) : null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load player row for " + playerUuid, e);
        }
    }

    @Override
    public List<ExpiredKey> findExpired(long now) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlFindExpired)) {
            ps.setLong(1, now);
            try (ResultSet rs = ps.executeQuery()) {
                List<ExpiredKey> keys = new ArrayList<>();
                while (rs.next()) {
                    keys.add(new ExpiredKey(rs.getString("player_uuid"),
                            rs.getInt("chest_index"), rs.getInt("kind")));
                }
                return keys;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query expired chests", e);
        }
    }

    @Override
    public long countChests() {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlCountChests);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count chest rows", e);
        }
    }

    @Override
    public String findUuidByName(String name) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlNameFind)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to resolve player name " + name, e);
        }
    }

    /** Maps the current result-set row of any {@code SQL_LOAD_*_CHESTS} query. */
    private static RawChestRow readChestRow(ResultSet rs) throws SQLException {
        return new RawChestRow(
                rs.getString("player_uuid"),
                rs.getInt("chest_index"),
                rs.getInt("size"),
                rs.getString("custom_name"),
                rs.getInt("is_primary"),
                rs.getBytes("container_data"),
                rs.getInt("migrated"),
                rs.getLong("last_updated"),
                rs.getInt("kind"),
                getNullableLong(rs, "expires_at"),
                rs.getString("icon"));
    }

    /** Maps the current result-set row of any {@code SQL_LOAD_*_PLAYER(S)} query. */
    private static RawPlayerRow readPlayerRow(ResultSet rs) throws SQLException {
        return new RawPlayerRow(
                rs.getString("player_uuid"),
                rs.getString("username"),
                rs.getInt("edit_mode"),
                rs.getInt("applied_default_size"));
    }

    // ---- flush (autosave / shutdown write-back) ----

    @Override
    public void flushChests(List<RawChestRow> upserts, List<ChestKey> deletes) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Upserts are portable delete-then-insert: clear the key first (no-op for a brand-new
                // row), then write the full row via the same verbatim insert the import path uses.
                try (PreparedStatement ps = conn.prepareStatement(sqlDeleteChest)) {
                    int pending = 0;
                    for (RawChestRow c : upserts) {
                        ps.setString(1, c.playerUuid());
                        ps.setInt(2, c.chestIndex());
                        ps.addBatch();
                        if (++pending >= BATCH_SIZE) { ps.executeBatch(); pending = 0; }
                    }
                    for (ChestKey k : deletes) {
                        ps.setString(1, k.playerUuid());
                        ps.setInt(2, k.chestIndex());
                        ps.addBatch();
                        if (++pending >= BATCH_SIZE) { ps.executeBatch(); pending = 0; }
                    }
                    if (pending > 0) ps.executeBatch();
                }
                batchChests(conn, upserts);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to flush chest rows to the database", e);
        }
    }

    @Override
    public void flushPlayers(List<RawPlayerRow> rows) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(sqlDeletePlayer)) {
                    int pending = 0;
                    for (RawPlayerRow p : rows) {
                        ps.setString(1, p.playerUuid());
                        ps.addBatch();
                        if (++pending >= BATCH_SIZE) { ps.executeBatch(); pending = 0; }
                    }
                    if (pending > 0) ps.executeBatch();
                }
                batchPlayers(conn, rows);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to flush player rows to the database", e);
        }
    }

    // ---- database-to-database import (/ee import) ----

    @Override
    public int[] importRows(List<RawPlayerRow> players, List<RawChestRow> chests) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int playerCount = batchPlayers(conn, players);
                int chestCount = batchChests(conn, chests);
                conn.commit();
                return new int[]{playerCount, chestCount};
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to import rows into the active database", e);
        }
    }

    // ---- shared batched inserts (operate on a caller-managed connection) ----

    private int batchPlayers(Connection conn, List<RawPlayerRow> players) throws SQLException {
        int pending = 0, total = 0;
        try (PreparedStatement ps = conn.prepareStatement(sqlInsertPlayer)) {
            for (RawPlayerRow p : players) {
                ps.setString(1, p.playerUuid());
                if (p.username() == null) ps.setNull(2, Types.VARCHAR); else ps.setString(2, p.username());
                ps.setInt(3, p.editMode());
                ps.setInt(4, p.appliedDefaultSize());
                ps.addBatch();
                total++;
                if (++pending >= BATCH_SIZE) {
                    ps.executeBatch();
                    pending = 0;
                }
            }
            if (pending > 0) ps.executeBatch();
        }
        return total;
    }

    private int batchChests(Connection conn, List<RawChestRow> chests) throws SQLException {
        int pending = 0, total = 0;
        try (PreparedStatement ps = conn.prepareStatement(sqlInsertChest)) {
            for (RawChestRow c : chests) {
                ps.setString(1, c.playerUuid());
                ps.setInt(2, c.chestIndex());
                ps.setInt(3, c.size());
                if (c.customName() == null) ps.setNull(4, Types.VARCHAR); else ps.setString(4, c.customName());
                ps.setInt(5, c.isPrimary());
                // setBytes(null) binds SQL NULL cleanly across all three dialects (avoids the
                // bytea/blob type guess setNull would need for the container column).
                ps.setBytes(6, c.containerData());
                ps.setInt(7, c.migrated());
                ps.setLong(8, c.lastUpdated());
                ps.setInt(9, c.kind());
                if (c.expiresAt() == null) ps.setNull(10, Types.BIGINT); else ps.setLong(10, c.expiresAt());
                if (c.icon() == null) ps.setNull(11, Types.VARCHAR); else ps.setString(11, c.icon());
                ps.addBatch();
                total++;
                if (++pending >= BATCH_SIZE) {
                    ps.executeBatch();
                    pending = 0;
                }
            }
            if (pending > 0) ps.executeBatch();
        }
        return total;
    }

    /** Reads a nullable BIGINT column as a boxed Long (null when SQL NULL). */
    private static Long getNullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
