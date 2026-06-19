package com.enhancedechest.storage.sql;

import com.enhancedechest.model.EnderChestData;
import com.enhancedechest.storage.EnderChestStorage;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jetbrains.annotations.Nullable;

import java.sql.*;
import java.util.UUID;

public abstract class AbstractSqlStorage implements EnderChestStorage {

    // These three queries use standard SQL compatible with all supported dialects.
    private static final String SQL_LOAD =
            "SELECT container_data, migrated, last_updated FROM enderchests WHERE player_uuid = ?";

    private static final String SQL_IS_MIGRATED =
            "SELECT migrated FROM enderchests WHERE player_uuid = ?";

    private static final String SQL_SET_MIGRATED =
            "UPDATE enderchests SET migrated = ? WHERE player_uuid = ?";

    protected final HikariDataSource dataSource;

    // Dialect-specific SQL injected by subclasses to avoid calling abstract methods
    // from the constructor (which would access uninitialized subclass state).
    private final String sqlInit;
    private final String sqlUpsert;

    protected AbstractSqlStorage(HikariConfig config, String sqlInit, String sqlUpsert) {
        this.dataSource = new HikariDataSource(config);
        this.sqlInit = sqlInit;
        this.sqlUpsert = sqlUpsert;
    }

    @Override
    public void init() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sqlInit);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database schema", e);
        }
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    @Override
    public @Nullable EnderChestData load(UUID uuid) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_LOAD)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                byte[] data = rs.getBytes("container_data");
                boolean migrated = rs.getInt("migrated") != 0;
                long lastUpdated = rs.getLong("last_updated");
                return new EnderChestData(uuid, data, migrated, lastUpdated);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load data for " + uuid, e);
        }
    }

    @Override
    public void save(UUID uuid, byte[] containerData) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlUpsert)) {
            ps.setString(1, uuid.toString());
            ps.setBytes(2, containerData);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save data for " + uuid, e);
        }
    }

    @Override
    public boolean isMigrated(UUID uuid) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_IS_MIGRATED)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt("migrated") != 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check migrated flag for " + uuid, e);
        }
    }

    @Override
    public void setMigrated(UUID uuid, boolean migrated) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_SET_MIGRATED)) {
            ps.setInt(1, migrated ? 1 : 0);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update migrated flag for " + uuid, e);
        }
    }
}
