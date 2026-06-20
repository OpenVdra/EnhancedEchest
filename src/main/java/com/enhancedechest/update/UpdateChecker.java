package com.enhancedechest.update;

import com.tcoded.folialib.FoliaLib;
import lombok.Getter;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UpdateChecker {

    public static final String MODRINTH_PAGE = "https://modrinth.com/plugin/xgEWccga";
    private static final String MODRINTH_API  = "https://api.modrinth.com/v2/project/xgEWccga/version";
    private static final Pattern VERSION_PATTERN =
            Pattern.compile("\"version_number\"\\s*:\\s*\"([^\"]+)\"");

    @Getter private final String currentVersion;
    private final Logger log;

    @Getter private volatile String latestVersion;
    @Getter private volatile boolean updateAvailable = false;

    public UpdateChecker(String currentVersion, Logger log) {
        this.currentVersion = currentVersion;
        this.log = log;
    }

    public void checkAsync(FoliaLib foliaLib) {
        foliaLib.getScheduler().runAsync(task -> this.performCheck());
    }

    private void performCheck() {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(MODRINTH_API))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "EnhancedEChest/" + currentVersion + " (update-check)")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return;

            Matcher matcher = VERSION_PATTERN.matcher(response.body());
            if (!matcher.find()) return;

            latestVersion = matcher.group(1);
            if (isNewer(latestVersion, currentVersion)) {
                updateAvailable = true;
                printUpdateBanner();
            }
        } catch (Exception e) {
            log.warn("[UpdateChecker] Could not reach Modrinth: {}", e.getMessage());
        }
    }

    private void printUpdateBanner() {
        String sep = "——————————————[ EnhancedEChest ]——————————————";
        log.warn("> {}", sep);
        log.warn(">");
        log.warn(">   UPDATE AVAILABLE!");
        log.warn(">   Current  : {}", currentVersion);
        log.warn(">   Latest   : {}", latestVersion);
        log.warn(">   Download : {}", MODRINTH_PAGE);
        log.warn(">");
        log.warn("> {}", sep);
    }

    // Returns true if `latest` is strictly newer than `current`.
    private static boolean isNewer(String latest, String current) {
        int[] l = parseVersion(latest);
        int[] c = parseVersion(current);
        int len = Math.max(l.length, c.length);
        for (int i = 0; i < len; i++) {
            int lv = i < l.length ? l[i] : 0;
            int cv = i < c.length ? c[i] : 0;
            if (lv > cv) return true;
            if (lv < cv) return false;
        }
        return false;
    }

    private static int[] parseVersion(String version) {
        // Strip pre-release suffixes (-SNAPSHOT, -RC1, etc.) before splitting.
        version = version.replaceAll("[^0-9.].*", "");
        String[] parts = version.split("\\.");
        int[] nums = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try { nums[i] = Integer.parseInt(parts[i]); } catch (NumberFormatException ignored) {}
        }
        return nums;
    }
}
