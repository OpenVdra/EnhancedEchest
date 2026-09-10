package com.enhancedechest;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.util.Properties;

/**
 * Read-only access to build metadata baked into {@code build-info.properties} at build time by
 * Gradle's {@code processResources} (see {@code build.gradle.kts}).
 *
 * <p>{@link #isDevBuild(JavaPlugin)} is {@code true} only for local developer runs
 * ({@code ./gradlew runServer}) and {@code false} in the jar shipped to users, so developer-only
 * logging can be gated out of release builds.
 */
public final class BuildInfo {

    private BuildInfo() {
    }

    /**
     * Whether this jar came from a local dev run rather than a release build. Any failure to read
     * the resource is treated as a release build (the safe, quiet default).
     */
    public static boolean isDevBuild(JavaPlugin plugin) {
        try (InputStream in = plugin.getResource("build-info.properties")) {
            if (in == null) {
                return false;
            }
            Properties props = new Properties();
            props.load(in);
            return Boolean.parseBoolean(props.getProperty("dev", "false").trim());
        } catch (Exception e) {
            return false;
        }
    }
}
