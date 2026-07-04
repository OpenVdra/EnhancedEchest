package com.enhancedechest.telemetry;

import com.enhancedechest.config.PluginConfig;
import dev.faststats.Attributes;
import dev.faststats.ErrorTracker;
import dev.faststats.bukkit.BukkitContext;
import dev.faststats.data.Metric;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FastStats-backed {@link Telemetry}: one {@link BukkitContext} carrying custom metrics and error
 * tracking. Built through {@link #create}, which falls back to {@link Telemetry#NOOP} when no token
 * was baked in at build time or the SDK fails to start — so the rest of the plugin never branches.
 *
 * <p>The metric suppliers are invoked by the SDK off-thread on its own submission schedule; they only
 * read immutable {@link PluginConfig} fields, so they are thread-safe and never touch platform state,
 * per FastStats supplier rules.
 */
public final class FastStatsTelemetry implements Telemetry {

    /**
     * Minimum gap between two reports of the same (where, exception class) pair, so an outage that
     * fails every save (e.g. the database going down) produces one report per minute instead of a
     * flood. The map stays tiny — it is keyed by call site + class, not by occurrence.
     */
    private static final long ERROR_REPORT_INTERVAL_MILLIS = 60_000L;

    private final BukkitContext context;
    private final ErrorTracker errorTracker;
    private final ConcurrentHashMap<String, Long> lastErrorReport = new ConcurrentHashMap<>();

    /**
     * Builds the FastStats-backed telemetry, or {@link Telemetry#NOOP} when the build-time token is
     * absent (see {@code faststats.properties}) or SDK startup throws — telemetry must never be able
     * to take the plugin down.
     */
    public static Telemetry create(JavaPlugin plugin, PluginConfig config) {
        String token = readToken(plugin);
        if (token.isEmpty() || token.startsWith("${")) {
            return NOOP;
        }
        try {
            return new FastStatsTelemetry(plugin, token, config);
        } catch (Exception e) {
            plugin.getSLF4JLogger().warn("Failed to initialize FastStats, telemetry disabled", e);
            return NOOP;
        }
    }

    private FastStatsTelemetry(JavaPlugin plugin, String token, PluginConfig config) {
        // contextAware also captures exceptions the JVM would otherwise swallow from this plugin's
        // class loader; the anonymize rules strip server-identifying details (OS user names in file
        // paths, JDBC URLs with host/database/credentials) from everything submitted.
        errorTracker = ErrorTracker.contextAware()
                .anonymize("(?i)[A-Z]:\\\\Users\\\\[^\\\\]+", "~")
                .anonymize("/(?:home|Users)/[^/\\s]+", "~")
                .anonymize("jdbc:[^\\s\"']+", "jdbc:<redacted>");

        context = new BukkitContext.Factory(plugin, token)
                .errorTrackerService(errorTracker)
                .metrics(factory -> factory
                        .addMetric(Metric.string("storage_type",
                                () -> config.getDatabaseType().toLowerCase(Locale.ROOT)))
                        .addMetric(Metric.string("language", config::getLocale))
                        .create())
                .create();
        context.ready();
    }

    @Override
    public void error(Throwable error, String where) {
        String key = where + '|' + error.getClass().getName();
        long now = System.currentTimeMillis();
        Long last = lastErrorReport.get(key);
        if (last != null && now - last < ERROR_REPORT_INTERVAL_MILLIS) {
            return;
        }
        lastErrorReport.put(key, now);
        errorTracker.trackError(error)
                .attributes(Attributes.empty().put("where", where));
    }

    @Override
    public void shutdown() {
        context.shutdown();
    }

    /**
     * Reads the build-time FastStats token from the bundled {@code faststats.properties} (populated
     * by Gradle's processResources from the {@code FASTSTATS_TOKEN} env var or a gitignored
     * {@code secrets.properties}). Empty — or the unexpanded placeholder in a dev build — means off.
     */
    private static String readToken(JavaPlugin plugin) {
        try (InputStream in = plugin.getResource("faststats.properties")) {
            if (in == null) {
                return "";
            }
            Properties props = new Properties();
            props.load(in);
            return props.getProperty("token", "").trim();
        } catch (Exception e) {
            return "";
        }
    }
}
