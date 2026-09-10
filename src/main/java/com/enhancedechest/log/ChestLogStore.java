package com.enhancedechest.log;

import com.enhancedechest.telemetry.Telemetry;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * The chest log's own SQLite database, deliberately separate from the main chest-data database so a busy
 * audit log never contends with — or bloats — the store players' items live in, and so its retention can
 * prune freely without touching chest rows. Self-contained: it owns its connection pool and its schema,
 * whatever backend the main storage is configured to use.
 *
 * <p>One row per OPEN/CLOSE event. The snapshot column holds the {@code ContainerCodec} bytes of the
 * chest at that instant, gzip-compressed here (item NBT compresses well, ~half on typical chests) so the
 * on-disk cost stays modest; compression and decompression happen entirely inside this class, on the
 * writer thread and the async read threads, never on a tick thread.
 *
 * <p><b>Threading.</b> A single physical connection (SQLite is a single-writer file; WAL keeps readers
 * un-blocked by the writer). All writes come from the one {@link ChestLogService} worker thread; reads
 * come from the shared DB executor when a GUI page is opened. The pool serialises the rare overlap,
 * which at log volumes is invisible.
 */
public final class ChestLogStore {

    /** A stored snapshot resolved back to bytes plus the chest size needed to decode it. */
    public record SnapshotBlob(byte[] data, int size) {}

    private static final String TABLE = "echest_log";

    private final Path dbFile;
    private final Logger logger;
    private final Telemetry telemetry;
    private HikariDataSource dataSource;

    public ChestLogStore(Path dataFolder, String fileName, Logger logger, Telemetry telemetry) {
        this.dbFile = dataFolder.resolve(fileName).toAbsolutePath();
        this.logger = logger;
        this.telemetry = telemetry;
    }

    /** Opens the pool and creates the schema. Must be called before any read/write. */
    public void init() throws Exception {
        Files.createDirectories(dbFile.getParent());

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + dbFile);
        config.setDriverClassName("org.sqlite.JDBC");
        // Single-writer file: one connection is both sufficient and correct (more would fight the file
        // lock and produce SQLITE_BUSY). WAL + synchronous=NORMAL matches the main SQLite backend.
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(1);
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("synchronous", "NORMAL");
        config.setConnectionTestQuery("SELECT 1");
        config.setPoolName("EnhancedEchest-Log");
        config.setConnectionTimeout(30_000);
        config.setIdleTimeout(0);
        config.setMaxLifetime(0);
        this.dataSource = new HikariDataSource(config);

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS %s (
                        id          INTEGER PRIMARY KEY AUTOINCREMENT,
                        owner       TEXT    NOT NULL,
                        chest_index INTEGER NOT NULL,
                        actor       TEXT    NOT NULL,
                        actor_name  TEXT,
                        size        INTEGER NOT NULL,
                        action      INTEGER NOT NULL,
                        ts          INTEGER NOT NULL,
                        diff        TEXT,
                        snapshot    BLOB    NOT NULL
                    )
                    """.formatted(TABLE));
            // Newest-first paging per owner is the only list query; ts drives retention.
            stmt.execute("CREATE INDEX IF NOT EXISTS " + TABLE + "_owner_id ON " + TABLE + " (owner, id DESC)");
            stmt.execute("CREATE INDEX IF NOT EXISTS " + TABLE + "_ts ON " + TABLE + " (ts)");
        }
    }

    /** Inserts a batch of events in one transaction. Called only from the log writer thread. */
    public void insertBatch(List<LogWrite> batch) throws SQLException {
        String sql = "INSERT INTO " + TABLE
                + " (owner, chest_index, actor, actor_name, size, action, ts, diff, snapshot)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection()) {
            boolean previousAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (LogWrite w : batch) {
                    ps.setString(1, w.owner().toString());
                    ps.setInt(2, w.index());
                    ps.setString(3, w.actor().toString());
                    ps.setString(4, w.actorName());
                    ps.setInt(5, w.size());
                    ps.setInt(6, w.action().ordinal());
                    ps.setLong(7, w.ts());
                    ps.setString(8, w.diff());
                    ps.setBytes(9, gzip(w.snapshot()));
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(previousAutoCommit);
            }
        }
    }

    /** Number of log rows for an owner, for pagination. */
    public int countForOwner(UUID owner) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM " + TABLE + " WHERE owner = ?")) {
            ps.setString(1, owner.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /**
     * One page of an owner's log, newest first. Deliberately omits the snapshot blob — the list only
     * needs the headline and diff; the blob is fetched by {@link #loadSnapshot} on click.
     */
    public List<LogEntry> page(UUID owner, int offset, int limit) throws SQLException {
        String sql = "SELECT id, action, ts, actor_name, chest_index, size, diff FROM " + TABLE
                + " WHERE owner = ? ORDER BY id DESC LIMIT ? OFFSET ?";
        List<LogEntry> out = new ArrayList<>(Math.min(limit, 64));
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, owner.toString());
            ps.setInt(2, limit);
            ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new LogEntry(
                            rs.getLong(1),
                            LogAction.fromCode(rs.getInt(2)),
                            rs.getLong(3),
                            rs.getString(4),
                            rs.getInt(5),
                            rs.getInt(6),
                            decodeDiff(rs.getString(7))));
                }
            }
        }
        return out;
    }

    /** Fetches and decompresses one event's snapshot, or {@code null} if the row is gone (pruned). */
    public @Nullable SnapshotBlob loadSnapshot(long id) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT snapshot, size FROM " + TABLE + " WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                byte[] stored = rs.getBytes(1);
                int size = rs.getInt(2);
                return new SnapshotBlob(gunzip(stored), size);
            }
        }
    }

    /**
     * Applies retention: deletes events older than {@code cutoffTs}, then trims each owner to its newest
     * {@code maxPerOwner} rows. The per-owner trim uses a window function (SQLite ≥ 3.25, well below the
     * bundled driver's version) so it stays one statement rather than a per-owner scan.
     *
     * @return total rows removed
     */
    public int prune(long cutoffTs, int maxPerOwner) throws SQLException {
        int removed = 0;
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM " + TABLE + " WHERE ts < ?")) {
                ps.setLong(1, cutoffTs);
                removed += ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM " + TABLE + " WHERE id IN ("
                            + "SELECT id FROM (SELECT id, ROW_NUMBER() OVER "
                            + "(PARTITION BY owner ORDER BY id DESC) AS rn FROM " + TABLE + ") "
                            + "WHERE rn > ?)")) {
                ps.setInt(1, maxPerOwner);
                removed += ps.executeUpdate();
            }
        }
        return removed;
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    // ---- helpers ----

    private static List<DiffLine> decodeDiff(@Nullable String diff) {
        if (diff == null || diff.isEmpty()) return List.of();
        List<DiffLine> lines = new ArrayList<>();
        for (String line : diff.split("\n")) {
            DiffLine parsed = DiffLine.decode(line);
            if (parsed != null) lines.add(parsed);
        }
        return lines;
    }

    private static byte[] gzip(byte[] raw) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, raw.length / 2));
        try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
            gz.write(raw);
        } catch (IOException e) {
            // In-memory stream: an IOException here is not expected. Fall back to raw bytes wrapped in a
            // marker would complicate reads, so surface it — the writer thread logs and drops the event.
            throw new IllegalStateException("Failed to gzip snapshot", e);
        }
        return out.toByteArray();
    }

    private static byte[] gunzip(byte[] stored) {
        try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(stored))) {
            return gz.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to gunzip snapshot", e);
        }
    }
}
