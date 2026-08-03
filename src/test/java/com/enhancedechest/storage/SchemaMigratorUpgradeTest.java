package com.enhancedechest.storage;

import com.enhancedechest.storage.sql.SqliteStorage;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the one path that can damage a live database: the v1 -> v2 upgrade that adds
 * {@code last_online}. A fresh install never runs it (the dialect's CREATE already carries the column),
 * so nothing else in the suite covers it — and it only ever runs once, on a server's real data.
 */
class SchemaMigratorUpgradeTest {

    @Test
    void upgradesV1SchemaAndBackfills() throws Exception {
        Path dir = Files.createTempDirectory("ee-mig");
        Path db = dir.resolve("old.db");
        long before = System.currentTimeMillis();

        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db);
             Statement s = c.createStatement()) {
            s.execute("CREATE TABLE echest_players (player_uuid TEXT NOT NULL, username TEXT, "
                    + "edit_mode INTEGER NOT NULL DEFAULT 0, applied_default_size INTEGER NOT NULL DEFAULT 0, "
                    + "PRIMARY KEY (player_uuid))");
            s.execute("INSERT INTO echest_players VALUES ('11111111-1111-1111-1111-111111111111','OldGuy',0,0)");
            s.execute("CREATE TABLE echest_schema_meta (meta_key VARCHAR(64) NOT NULL, meta_value VARCHAR(64), PRIMARY KEY (meta_key))");
            s.execute("INSERT INTO echest_schema_meta VALUES ('version','1')");
        }

        SqliteStorage storage = new SqliteStorage(dir, "old.db", "echest_");
        storage.init();
        storage.close();

        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db);
             Statement s = c.createStatement()) {
            try (ResultSet rs = s.executeQuery("SELECT username, last_online FROM echest_players")) {
                assertTrue(rs.next(), "row survived");
                assertEquals("OldGuy", rs.getString("username"));
                long seeded = rs.getLong("last_online");
                assertTrue(seeded >= before, "backfilled to now, got " + seeded);
            }
            try (ResultSet rs = s.executeQuery("SELECT meta_value FROM echest_schema_meta WHERE meta_key='version'")) {
                assertTrue(rs.next());
                assertEquals("2", rs.getString(1), "version stamped");
            }
        }
    }
}
