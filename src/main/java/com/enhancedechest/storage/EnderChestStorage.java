package com.enhancedechest.storage;

import com.enhancedechest.model.EnderChestData;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * All methods execute synchronously on the calling thread.
 * No background threads, no queued writes — callers are responsible for
 * only invoking from the main server thread.
 */
public interface EnderChestStorage {

    /** Creates schema and prepares connections. Must be called once before any other method. */
    void init();

    /** Closes all connections. Safe to call even if init() was not completed. */
    void close();

    /** Returns the stored data for the player, or null if no row exists yet. */
    @Nullable EnderChestData load(UUID uuid);

    /**
     * Upserts container bytes for the player.
     * The migrated flag is preserved for existing rows (not reset on save).
     */
    void save(UUID uuid, byte[] containerData);

    /** Returns true if a row exists and its migrated flag is set. */
    boolean isMigrated(UUID uuid);

    /**
     * Updates the migrated flag. If no row exists yet this is a no-op
     * (migration service always calls save() first).
     */
    void setMigrated(UUID uuid, boolean migrated);
}
