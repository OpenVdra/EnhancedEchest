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

    private static final String VERSION_KEY = "version";

    /**
     * One forward migration. {@link #apply} runs against an existing database whose schema is older than
     * {@link #version}; it must be idempotent (guard additive DDL with {@link #columnExists}). Receives
     * the active table prefix since table names are no longer fixed literals.
     */
    private interface Step {
        int version();
        void apply(Connection conn, String prefix) throws SQLException;
    }

    // Ordered list of every migration ever shipped. Never edit or reorder a released step — only append.
    private static final List<Step> STEPS = List.of(
            new Step() {
                @Override public int version() { return 1; }
                @Override public void apply(Connection conn, String prefix) throws SQLException {
                    // 1.0.4: pre-1.0.4 installs have a `player_settings` table holding only edit_mode.
                    // 1.0.4 renames/merges it into `players` (now `<prefix>players`), which also carries
                    // the permission-managed base-chest size baseline (applied_default_size) and the name
                    // index (username). The dialect's CREATE TABLE IF NOT EXISTS above already created the
                    // players table (empty) by the time this runs, so merge the old rows into it before
                    // dropping the old table, rather than trying to rename onto an existing name. The NOT
                    // EXISTS guard and DROP TABLE IF EXISTS make this safe to retry after a crash between
                    // the merge and the drop. `player_settings` itself predates the prefix and is always
                    // bare — it is merged away entirely, never carried forward under a prefixed name.
                    String players = prefix + "players";
                    if (tableExists(conn, "player_settings")) {
                        // A locally-tested pre-merge build of this same release may already have added
                        // applied_default_size to the old table; carry it over too when present so no data
                        // is lost. A genuine pre-1.0.4 install only ever had edit_mode.
                        boolean hadApplied = columnExists(conn, "player_settings", "applied_default_size");
                        String cols = hadApplied ? "player_uuid, edit_mode, applied_default_size"
                                                  : "player_uuid, edit_mode";
                        try (Statement stmt = conn.createStatement()) {
                            stmt.execute(
                                    "INSERT INTO " + players + " (" + cols + ") " +
                                    "SELECT " + cols + " FROM player_settings ps " +
                                    "WHERE NOT EXISTS (SELECT 1 FROM " + players + " p WHERE p.player_uuid = ps.player_uuid)");
                            stmt.execute("DROP TABLE IF EXISTS player_settings");
                        }
                    }
                    addColumnIfMissing(conn, players, "applied_default_size", "INTEGER NOT NULL DEFAULT 0");
                    // No default (NULL = no name recorded yet). A column added by ALTER always lands at
                    // the physical end of the table regardless of this call's position in the source —
                    // portable ALTER TABLE ADD COLUMN has no "add after X" clause on SQLite/PostgreSQL —
                    // so only a fresh install (via the CREATE TABLE above) gets username as column 2; an
                    // upgraded database keeps it physically last. Harmless: every DML here addresses
                    // columns by name, never by position.
                    addColumnIfMissing(conn, players, "username", "VARCHAR(48)");
                }
            }
    );

    private SchemaMigrator() {}

    /**
     * Renames the plugin's bare, pre-prefix table names (from installs created before {@code
     * database.table-prefix} existed) to their current prefixed names, so the CREATE TABLE IF NOT EXISTS
     * that runs right after this doesn't instead create empty tables alongside the old, populated ones.
     * Called once from {@link AbstractSqlStorage#init()}, <b>before</b> the dialect's CREATE statements.
     *
     * <p>Guarded per table by {@code tableExists(old) && !tableExists(new)}, so it is a no-op once the
     * rename has already happened, and a no-op entirely when {@code prefix} is empty (old name == new
     * name). {@code ALTER TABLE ... RENAME TO ...} is accepted by SQLite, MySQL/MariaDB and PostgreSQL
     * alike, so no dialect branching is needed. Failures are logged and swallowed per table — worst case
     * the next CREATE TABLE IF NOT EXISTS makes a fresh empty table and the old, un-renamed one is left
     * orphaned for an admin to investigate, rather than aborting startup.
     */
    static void renameLegacyTables(DataSource dataSource, String prefix) {
        try (Connection conn = dataSource.getConnection()) {
            boolean renamedChests = renameIfNeeded(conn, "enderchests", prefix + "enderchests");
            renameIfNeeded(conn, "players", prefix + "players");
            renameIfNeeded(conn, "schema_meta", prefix + "schema_meta");
            if (renamedChests) {
                // The old idx_enderchests_expires index travels with the table under its old, unprefixed
                // name across the rename (every dialect keeps an index attached to its table, not its
                // name), so leaving it behind would make ensureIndexes create a second, redundant index
                // under the new prefixed name right after this.
                dropIndexIfPresent(conn, prefix + "enderchests", "idx_enderchests_expires");
            }
        } catch (SQLException e) {
            log.warn("Failed to check for legacy (pre-table-prefix) tables to rename", e);
        }
    }

    /** @return true if a rename actually happened (i.e. the old bare table existed and needed one) */
    private static boolean renameIfNeeded(Connection conn, String oldName, String newName) {
        if (oldName.equals(newName)) {
            return false;
        }
        try {
            if (tableExists(conn, oldName) && !tableExists(conn, newName)) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("ALTER TABLE " + oldName + " RENAME TO " + newName);
                }
                log.info("Renamed existing table '{}' to '{}' to match database.table-prefix.", oldName, newName);
                return true;
            }
        } catch (SQLException e) {
            log.warn("Failed to rename table '{}' to '{}'", oldName, newName, e);
        }
        return false;
    }

    /**
     * Drops {@code indexName} on {@code table} if present, tolerating it not existing at all (an install
     * old enough to predate the index). SQLite and PostgreSQL accept a bare {@code DROP INDEX name};
     * MySQL/MariaDB require the table too, so that form is tried second, on failure of the first.
     * Best-effort like {@link #ensureIndexes}: a failure here only costs one redundant index, never
     * correctness, so it is logged and swallowed rather than aborting startup.
     */
    private static void dropIndexIfPresent(Connection conn, String table, String indexName) {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DROP INDEX " + indexName);
            log.info("Dropped legacy index '{}' (superseded by the prefixed table-prefix index).", indexName);
            return;
        } catch (SQLException sqliteOrPostgresStyleFailed) {
            // fall through to the MySQL/MariaDB form below
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DROP INDEX " + indexName + " ON " + table);
            log.info("Dropped legacy index '{}' (superseded by the prefixed table-prefix index).", indexName);
        } catch (SQLException probablyDidNotExist) {
            log.debug("Legacy index '{}' not dropped (likely never existed): {}",
                    indexName, probablyDidNotExist.getMessage());
        }
    }

    /**
     * Brings {@code dataSource}'s schema up to {@link #CURRENT_VERSION}, running only the steps newer than
     * the recorded version. Called once from {@link AbstractSqlStorage#init()} after the base tables exist.
     * A failure is fatal (rethrown) — running on a half-migrated schema would be worse than not starting.
     */
    static void migrate(DataSource dataSource, String prefix) {
        String metaTable = prefix + "schema_meta";
        try (Connection conn = dataSource.getConnection()) {
            ensureMetaTable(conn, metaTable);
            int from = readVersion(conn, metaTable);
            if (from < CURRENT_VERSION) {                  // skip the version loop on the common restart path
                for (Step step : STEPS) {
                    if (step.version() > from) {
                        step.apply(conn, prefix);
                        writeVersion(conn, metaTable, step.version());
                        log.info("Applied database schema migration to v{}.", step.version());
                    }
                }
            }
            ensureIndexes(conn, prefix);                   // best-effort, every start, idempotent
        } catch (SQLException e) {
            throw new RuntimeException("Failed to migrate database schema", e);
        }
    }

    /**
     * Best-effort creation of the supporting indexes, run on every start (not version-gated) so installs
     * that predate an index still gain it. Currently just the expiry-sweep index: {@code findExpired}
     * runs every sweep with a range predicate on {@code expires_at}, and without an index that is a full
     * table scan — on SQLite the container blobs are stored inline, so the scan reads most of the DB file
     * every few minutes on a large roster. A missing index only costs sweep performance, never
     * correctness, so any failure here is logged and swallowed rather than aborting startup.
     *
     * <p>Portability: {@code CREATE INDEX IF NOT EXISTS} is accepted by SQLite, MariaDB and PostgreSQL;
     * stock MySQL rejects the {@code IF NOT EXISTS}, so on that first failure we retry with a plain
     * {@code CREATE INDEX} and tolerate the duplicate-index error a subsequent start then raises.
     */
    private static void ensureIndexes(Connection conn, String prefix) {
        // Index name carries the prefix too: SQLite/PostgreSQL require index names to be unique
        // database-wide (unlike MySQL, which scopes them per-table), so two prefixes never collide.
        // Reuse of the historical unprefixed name (pre-prefix installs) is handled by renameLegacyTables
        // leaving the table itself intact — the index is just recreated under the new name if missing.
        String indexName = prefix + "idx_enderchests_expires";
        String table = prefix + "enderchests";
        String ddl = "CREATE INDEX %s" + indexName + " ON " + table + " (expires_at)";
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(String.format(ddl, "IF NOT EXISTS "));
        } catch (SQLException ifNotExistsUnsupported) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(String.format(ddl, ""));      // stock MySQL: no IF NOT EXISTS
            } catch (SQLException probablyAlreadyExists) {
                log.debug("Expiry index not created (likely already present): {}",
                        probablyAlreadyExists.getMessage());
            }
        }
    }

    private static void ensureMetaTable(Connection conn, String metaTable) throws SQLException {
        // A tiny key/value table. VARCHAR(64) is accepted by every engine (SQLite treats it as TEXT), so
        // this one CREATE is portable and needs no per-dialect variant.
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS " + metaTable + " (" +
                    "  meta_key   VARCHAR(64) NOT NULL," +
                    "  meta_value VARCHAR(64)," +
                    "  PRIMARY KEY (meta_key)" +
                    ")");
        }
    }

    /**
     * Reads the recorded schema version, or {@code 0} when the meta table has no version row. A
     * pre-migrator database (created before this table existed) reads {@code 0} and gets every step; its
     * additive steps then no-op on any column the old CREATE already had.
     */
    private static int readVersion(Connection conn, String metaTable) throws SQLException {
        try (var ps = conn.prepareStatement(
                "SELECT meta_value FROM " + metaTable + " WHERE meta_key = ?")) {
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
    private static void writeVersion(Connection conn, String metaTable, int version) throws SQLException {
        try (var ps = conn.prepareStatement(
                "UPDATE " + metaTable + " SET meta_value = ? WHERE meta_key = ?")) {
            ps.setString(1, Integer.toString(version));
            ps.setString(2, VERSION_KEY);
            if (ps.executeUpdate() > 0) return;
        }
        try (var ps = conn.prepareStatement(
                "INSERT INTO " + metaTable + " (meta_key, meta_value) VALUES (?, ?)")) {
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
