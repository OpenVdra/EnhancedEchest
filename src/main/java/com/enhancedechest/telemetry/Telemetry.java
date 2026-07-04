package com.enhancedechest.telemetry;

/**
 * Plugin-facing telemetry facade. Call sites stay one-liners and never know whether FastStats is
 * active — when no project token was baked in at build time (or init fails) the {@link #NOOP}
 * instance is wired instead, so error reporting costs nothing. This interface is the only telemetry
 * type the rest of the plugin may depend on; everything {@code dev.faststats} stays inside
 * {@link FastStatsTelemetry}.
 *
 * <p>Implementations must be thread-safe: reports fire from region threads, the global bookkeeping
 * thread and the async DB executor alike.
 */
public interface Telemetry {

    /**
     * Reports a handled-but-noteworthy failure (codec corruption, DB save failure, …) to error
     * tracking, alongside the existing log line — never instead of it. {@code where} is a short
     * static label (e.g. {@code "chest.save-db"}) used to group reports; never put player data in it.
     * Uncaught exceptions are captured automatically and do not need this call.
     */
    void error(Throwable error, String where);

    /** Flushes and stops the backing service. Called once from {@code onDisable}. */
    void shutdown();

    /** Shared inert instance used when telemetry is disabled. */
    Telemetry NOOP = new Telemetry() {
        @Override public void error(Throwable error, String where) { }
        @Override public void shutdown() { }
    };
}
