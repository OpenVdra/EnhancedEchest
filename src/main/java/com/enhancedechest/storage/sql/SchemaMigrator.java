package com.enhancedechest.storage.sql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;

/**
 * Versioned, forward-only schema migrator shared by every SQL dialect.
 *
 * <p>The base tables are created by each dialect's {@code CREATE TABLE IF NOT EXISTS} statements (run
 * first, in {@link AbstractSqlStorage#init()}), which already carry every current column — so a
 * <b>fresh</b> install lands on the latest schema directly. This migrator's job is the <b>existing</b>
 * install: a database created by an older plugin version whose tables are missing columns added later, or
 * (occasionally, e.g. the 1.0.4 {@code player_settings} → {@code players} merge) renamed/merged entirely.
 * It brings such a database up to {@link #CURRENT_VERSION} by running the {@link #STEPS} whose version is
 * newer than the one recorded in {@code schema_meta}, then stamps the new version.
 *
 * <p><b>Safety.</b> Every step is written to be idempotent: additive column steps first check
 * {@link #columnExists}, and a table-merge step first checks {@link #tableExists} for the old table, both
 * via JDBC {@link DatabaseMetaData} (portable across SQLite / MySQL / MariaDB / PostgreSQL), skipping the
 * DDL when there is nothing left to do. That makes the steps safe to run even when the {@code schema_meta}
 * version is missing or behind (e.g. on a fresh install where the CREATE already added the column, or if a
 * previous run was interrupted before the version was stamped). The recorded version is an optimisation
 * and an audit trail, not the sole guard.
 *
 * <p>All SQL here is intentionally the portable subset: {@code CREATE TABLE IF NOT EXISTS} and
 * {@code ALTER TABLE ... ADD COLUMN ... NOT NULL DEFAULT <const>} are accepted verbatim by all four
 * engines, and {@code INTEGER} is a valid column type on each. New tables with dialect-specific column
 * types belong in the per-dialect {@code CREATE} statements instead (they are safe on existing installs
 * via {@code IF NOT EXISTS}); only cross-version <i>alterations</i> of existing tables live here.
 */
final class SchemaMigrator {

    /** The schema version this build expects. Bump this and add a {@link Step} when the schema changes. */
    static final int CURRENT_VERSION = 1;

    private static final Logger log = LoggerFactory.getLogger("EnhancedEchest");

    private static final String META_TABLE = "schema_meta";
    private static final String VERSION_KEY = "version";

    // schema_meta is a tiny key/value table. VARCHAR(64) is accepted by every engine (SQLite treats it as
    // TEXT), so this one CREATE is portable and needs no per-dialect variant.
    private static final String CREATE_META =
            "CREATE TABLE IF NOT EXISTS " + META_TABLE + " (" +
            "  meta_key   VARCHAR(64) NOT NULL," +
            "  meta_value VARCHAR(64)," +
            "  PRIMARY KEY (meta_key)" +
            ")";

    /**
     * One forward migration. {@link #apply} runs against an existing database whose schema is older than
     * {@link #version}; it must be idempotent (guard additive DDL with {@link #columnExists}).
     */
    private interface Step {
        int version();
        void apply(Connection conn) throws SQLException;
    }

    // Ordered list of every migration ever shipped. Never edit or reorder a released step — only append.
    private static final List<Step> STEPS = List.of(
            new Step() {
                @Override public int version() { return 1; }
                @Override public void apply(Connection conn) throws SQLException {
                    // 1.0.4: pre-1.0.4 installs have a `player_settings` table holding only edit_mode.
                    // 1.0.4 renames/merges it into `players`, which also carries the permission-managed
                    // base-chest size baseline (applied_default_size) and the name index (username). The
                    // dialect's CREATE TABLE IF NOT EXISTS above already created `players` (empty) by the
                    // time this runs, so merge the old rows into it before dropping the old table, rather
                    // than trying to rename onto an existing name. The NOT EXISTS guard and DROP TABLE IF
                    // EXISTS make this safe to retry after a crash between the merge and the drop.
                    if (tableExists(conn, "player_settings")) {
                        // A locally-tested pre-merge build of this same release may already have added
                        // applied_default_size to the old table; carry it over too when present so no data
                        // is lost. A genuine pre-1.0.4 install only ever had edit_mode.
                        boolean hadApplied = columnExists(conn, "player_settings", "applied_default_size");
                        String cols = hadApplied ? "player_uuid, edit_mode, applied_default_size"
                                                  : "player_uuid, edit_mode";
                        try (Statement stmt = conn.createStatement()) {
                            stmt.execute(
                                    "INSERT INTO players (" + cols + ") " +
                                    "SELECT " + cols + " FROM player_settings ps " +
                                    "WHERE NOT EXISTS (SELECT 1 FROM players p WHERE p.player_uuid = ps.player_uuid)");
                            stmt.execute("DROP TABLE IF EXISTS player_settings");
                        }
                    }
                    addColumnIfMissing(conn, "players", "applied_default_size", "INTEGER NOT NULL DEFAULT 0");
                    // No default (NULL = no name recorded yet). A column added by ALTER always lands at
                    // the physical end of the table regardless of this call's position in the source —
                    // portable ALTER TABLE ADD COLUMN has no "add after X" clause on SQLite/PostgreSQL —
                    // so only a fresh install (via the CREATE TABLE above) gets username as column 2; an
                    // upgraded database keeps it physically last. Harmless: every DML here addresses
                    // columns by name, never by position.
                    addColumnIfMissing(conn, "players", "username", "VARCHAR(48)");
                }
            }
    );

    private SchemaMigrator() {}

    /**
     * Brings {@code dataSource}'s schema up to {@link #CURRENT_VERSION}, running only the steps newer than
     * the recorded version. Called once from {@link AbstractSqlStorage#init()} after the base tables exist.
     * A failure is fatal (rethrown) — running on a half-migrated schema would be worse than not starting.
     */
    static void migrate(DataSource dataSource) {
        try (Connection conn = dataSource.getConnection()) {
            ensureMetaTable(conn);
            int from = readVersion(conn);
            if (from >= CURRENT_VERSION) {
                return; // already current — the common path on every restart after the first.
            }
            for (Step step : STEPS) {
                if (step.version() > from) {
                    step.apply(conn);
                    writeVersion(conn, step.version());
                    log.info("Applied database schema migration to v{}.", step.version());
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to migrate database schema", e);
        }
    }

    private static void ensureMetaTable(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(CREATE_META);
        }
    }

    /**
     * Reads the recorded schema version, or {@code 0} when {@code schema_meta} has no version row. A
     * pre-migrator database (created before this table existed) reads {@code 0} and gets every step; its
     * additive steps then no-op on any column the old CREATE already had.
     */
    private static int readVersion(Connection conn) throws SQLException {
        try (var ps = conn.prepareStatement(
                "SELECT meta_value FROM " + META_TABLE + " WHERE meta_key = ?")) {
            ps.setString(1, VERSION_KEY);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return 0;
                try {
                    return Integer.parseInt(rs.getString(1));
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
        }
    }

    /** Upserts the recorded schema version (UPDATE-else-INSERT — portable, no dialect upsert syntax). */
    private static void writeVersion(Connection conn, int version) throws SQLException {
        try (var ps = conn.prepareStatement(
                "UPDATE " + META_TABLE + " SET meta_value = ? WHERE meta_key = ?")) {
            ps.setString(1, Integer.toString(version));
            ps.setString(2, VERSION_KEY);
            if (ps.executeUpdate() > 0) return;
        }
        try (var ps = conn.prepareStatement(
                "INSERT INTO " + META_TABLE + " (meta_key, meta_value) VALUES (?, ?)")) {
            ps.setString(1, VERSION_KEY);
            ps.setString(2, Integer.toString(version));
            ps.executeUpdate();
        }
    }

    /**
     * Adds {@code column} to {@code table} with the given {@code columnDef} (type + constraints, e.g.
     * {@code "INTEGER NOT NULL DEFAULT 0"}) only if it is not already present. The existence check makes
     * the ALTER safe on a fresh install (where the CREATE already added the column) and on a re-run.
     */
    private static void addColumnIfMissing(Connection conn, String table, String column, String columnDef)
            throws SQLException {
        if (columnExists(conn, table, column)) {
            return;
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + columnDef);
        } catch (SQLException e) {
            // Backstop against a driver under-reporting columns in metadata: if the ALTER failed because
            // the column is in fact already present (duplicate-column error), tolerate it — the step is
            // idempotent. Rethrow any other failure (a genuine schema problem).
            if (!columnExists(conn, table, column)) {
                throw e;
            }
        }
    }

    /**
     * True if {@code table} has a column named {@code column} (case-insensitive), using JDBC metadata so
     * it works on every dialect without engine-specific catalog queries. The table/column names are
     * lower-cased as created; engines fold or store them differently, so both sides are compared
     * case-insensitively to be robust across SQLite, MySQL/MariaDB and PostgreSQL.
     */
    private static boolean columnExists(Connection conn, String table, String column) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        // Pass null pattern for the column so drivers that are picky about pattern matching still return
        // the full column set; filter in Java for a reliable case-insensitive comparison.
        try (ResultSet rs = meta.getColumns(conn.getCatalog(), null, table, null)) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("COLUMN_NAME"))) {
                    return true;
                }
            }
        }
        // Some drivers store the identifier upper-cased in metadata; retry with an upper-cased table name
        // before concluding the column is absent (a false "absent" would trigger a duplicate-column ALTER).
        try (ResultSet rs = meta.getColumns(conn.getCatalog(), null,
                table.toUpperCase(Locale.ROOT), null)) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("COLUMN_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * True if a table named {@code table} exists (case-insensitive, with the same upper-case retry as
     * {@link #columnExists}), using JDBC metadata so it works on every dialect. Used to guard the
     * one-time 1.0.4 {@code player_settings} → {@code players} merge against re-running once the old
     * table is gone.
     */
    private static boolean tableExists(Connection conn, String table) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getTables(conn.getCatalog(), null, table, null)) {
            if (rs.next()) {
                return true;
            }
        }
        try (ResultSet rs = meta.getTables(conn.getCatalog(), null, table.toUpperCase(Locale.ROOT), null)) {
            return rs.next();
        }
    }
}
