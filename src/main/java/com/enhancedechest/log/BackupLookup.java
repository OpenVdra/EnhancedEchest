package com.enhancedechest.log;

import org.jetbrains.annotations.Nullable;
import org.sqlite.SQLiteConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Reads one chest out of the scheduled database backups ({@code backups/enderchests-yyyyMMdd-HHmmss.db},
 * written by {@code BackupService} with SQLite {@code VACUUM INTO}). Used by the {@code /ee log} "before"
 * view when the log itself has no earlier visit of that chest: the newest backup taken before the visit
 * started shows what the chest held then. Backups are opened read-only and never modified.
 */
public final class BackupLookup {

    /** A chest found in a backup: its encoded contents, slot count and when the backup was taken. */
    public record Found(byte[] data, int size, long takenAt) {}

    private static final Pattern NAME = Pattern.compile("enderchests-(\\d{8}-\\d{6})(?:-\\d+)?\\.db");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private BackupLookup() {}

    /**
     * The chest from the newest backup taken strictly before {@code beforeMillis}, or {@code null} if no
     * backup is that old or none of them has the chest. Walks backups newest first and stops at the first
     * that has the row.
     */
    public static @Nullable Found find(Path backupDir, UUID owner, int index, long beforeMillis) throws Exception {
        if (!Files.isDirectory(backupDir)) return null;
        record Candidate(Path path, long takenAt) {}
        List<Candidate> candidates = new ArrayList<>();
        try (Stream<Path> files = Files.list(backupDir)) {
            for (Path p : (Iterable<Path>) files::iterator) {
                Matcher m = NAME.matcher(p.getFileName().toString());
                if (!m.matches()) continue;
                long at = LocalDateTime.parse(m.group(1), STAMP).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                if (at < beforeMillis) candidates.add(new Candidate(p, at));
            }
        }
        candidates.sort(Comparator.comparingLong(Candidate::takenAt).reversed());
        SQLiteConfig config = new SQLiteConfig();
        config.setReadOnly(true);
        for (Candidate c : candidates) {
            try (Connection conn = config.createConnection("jdbc:sqlite:" + c.path().toAbsolutePath());
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT size, container_data FROM echest_enderchests WHERE player_uuid = ? AND chest_index = ? LIMIT 1")) {
                ps.setString(1, owner.toString());
                ps.setInt(2, index);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) continue;
                    byte[] data = rs.getBytes(2);
                    return new Found(data == null ? new byte[0] : data, rs.getInt(1), c.takenAt());
                }
            }
        }
        return null;
    }
}
