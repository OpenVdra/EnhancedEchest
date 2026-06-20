package com.enhancedechest.storage;

import com.enhancedechest.model.ChestSummary;
import com.enhancedechest.model.EnderChestData;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * All methods execute synchronously on the calling thread.
 * No background threads, no queued writes — callers (EnderChestService) are responsible
 * for dispatching these onto an async executor and never blocking a region/main thread.
 *
 * <p>Ownership model: a player owns a chest iff a row exists for (player_uuid, chest_index).
 * Chests are created explicitly (admin command, API, or the auto-bootstrap of the first chest).
 */
public interface EnderChestStorage {

    /** Creates schema and prepares connections. Must be called once before any other method. */
    void init();

    /** Closes all connections. Safe to call even if init() was not completed. */
    void close();

    /** Returns the player's chests ordered by index. Empty list if the player owns none. */
    List<ChestSummary> listChests(UUID owner);

    /**
     * Returns the index of the chest /ec opens: the primary if one is flagged, otherwise the
     * lowest-indexed chest. Returns -1 if the player owns no chests.
     */
    int getPrimaryIndex(UUID owner);

    /** Loads a single chest, or null if no such (owner, index) row exists. */
    @Nullable EnderChestData loadChest(UUID owner, int index);

    /**
     * Updates the container bytes of an existing chest. No-op if the row was deleted
     * while open. Size, name and primary flag are never touched here.
     */
    void saveChest(UUID owner, int index, byte[] containerData);

    /**
     * Creates a new chest with the next free index (max+1, or 1 if the player has none).
     * The very first chest a player gets is automatically flagged primary.
     *
     * @return the index assigned to the new chest
     */
    int createChest(UUID owner, int size);

    /**
     * Creates a chest at a specific index if it does not already exist (used by migration).
     * No-op if the row already exists. The first chest created is flagged primary.
     */
    void ensureChest(UUID owner, int index, int size);

    /** Changes a chest's slot count. Caller validates size (multiple of 9, 9..54). */
    void resizeChest(UUID owner, int index, int size);

    /**
     * Deletes a chest. If the deleted chest was primary and others remain, the
     * lowest-indexed survivor is promoted to primary.
     */
    void deleteChest(UUID owner, int index);

    /** Sets or clears a chest's custom display name (null resets to the default numbered title). */
    void renameChest(UUID owner, int index, @Nullable String name);

    /** Makes the given chest the player's primary, clearing the flag from all others. */
    void setPrimary(UUID owner, int index);

    /** Returns true if the player's chest #1 has its migrated flag set. */
    boolean isMigrated(UUID owner);

    /** Updates the migrated flag on the player's chest #1. No-op if chest #1 does not exist. */
    void setMigrated(UUID owner, boolean migrated);
}
