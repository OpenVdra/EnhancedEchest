package com.enhancedechest.gui;

import com.enhancedechest.lang.LanguageManager;
import com.enhancedechest.model.EnderChestData;
import com.enhancedechest.serialization.CodecException;
import com.enhancedechest.serialization.ContainerCodec;
import com.enhancedechest.storage.EnderChestStorage;
import com.tcoded.folialib.FoliaLib;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Owns the open and save lifecycle of the custom enderchest GUI.
 *
 * Dupe-safety contract:
 * - open() always closes any existing GUI first (synchronously, on entity thread), then waits
 *   for any in-flight async DB save to complete before loading fresh data.
 * - save() encodes inventory bytes synchronously on the calling thread (fast, < 1 ms),
 *   then flushes to DB on a daemon thread.
 * - flushPendingSaves() in onDisable() blocks until all writes finish before the DB pool closes.
 */
public final class EnderChestService {

    private final LanguageManager lang;
    private final ContainerCodec  codec;
    private final EnderChestStorage storage;
    private final Logger logger;
    private final FoliaLib foliaLib;

    /** Tracks in-flight async DB writes keyed by player UUID. */
    private final ConcurrentHashMap<UUID, CompletableFuture<Void>> pendingSaves =
            new ConcurrentHashMap<>();

    private final ExecutorService asyncExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "EnhancedEChest-db");
        t.setDaemon(true);
        return t;
    });

    public EnderChestService(LanguageManager lang, ContainerCodec codec,
                             EnderChestStorage storage, Logger logger, FoliaLib foliaLib) {
        this.lang     = lang;
        this.codec    = codec;
        this.storage  = storage;
        this.logger   = logger;
        this.foliaLib = foliaLib;
    }

    /**
     * Opens the custom ender chest GUI for the player.
     *
     * @param player      the player
     * @param sourceBlock ender chest block location if opened via right-click; null for command
     */
    public void open(Player player, @Nullable Location sourceBlock) {
        UUID uuid = player.getUniqueId();

        // runAtEntity guarantees we are on the player's entity thread for inventory operations.
        // In Folia the calling context (e.g. PlayerInteractEvent on the block's region thread)
        // may differ from the player's region thread; FoliaLib resolves this transparently.
        foliaLib.getScheduler().runAtEntity(player, outerTask -> {

            // Close existing custom GUI — fires InventoryCloseEvent → save() → encodes bytes sync.
            Inventory currentTop = player.getOpenInventory().getTopInventory();
            if (currentTop.getHolder() instanceof EnderChestHolder) {
                player.closeInventory();
            }

            // Wait for any in-flight save (registered by the closeInventory() above or a prior
            // quit-save backstop), then load fresh data from the DB on an async thread.
            CompletableFuture<Void> pending =
                    pendingSaves.getOrDefault(uuid, CompletableFuture.completedFuture(null));

            pending
                .thenCompose(v -> CompletableFuture.supplyAsync(
                        () -> storage.load(uuid), asyncExecutor))
                .thenAccept(data ->
                    // Return to the player's entity thread to safely open the inventory.
                    foliaLib.getScheduler().runAtEntity(player, innerTask -> {
                        if (!player.isOnline()) return;
                        doOpenInventory(player, uuid, data, sourceBlock);
                    }))
                .exceptionally(e -> {
                    logger.error("Failed to load enderchest for {} — aborting open",
                            player.getName(), e.getCause());
                    foliaLib.getScheduler().runAtEntity(player, innerTask -> {
                        if (player.isOnline()) player.sendMessage(lang.get("chest.load-failed"));
                    });
                    return null;
                });
        });
    }

    private void doOpenInventory(Player player, UUID uuid,
                                 @Nullable EnderChestData data, @Nullable Location sourceBlock) {
        Inventory inv = Bukkit.createInventory(
                new EnderChestHolder(uuid, sourceBlock),
                ContainerCodec.CHEST_SIZE,
                lang.getGuiTitle());

        if (data != null && data.containerData() != null && data.containerData().length > 0) {
            try {
                inv.setContents(codec.decode(data.containerData()));
            } catch (CodecException e) {
                logger.error("Codec failure for {} — aborting open to protect stored data",
                        player.getName(), e);
                player.sendMessage(lang.get("chest.codec-failed"));
                return;
            }
        }

        player.openInventory(inv);
    }

    /**
     * Saves the inventory to the database asynchronously.
     *
     * Encodes inventory bytes synchronously on the calling thread (fast byte operation),
     * then writes to DB on a daemon thread. Must be called from the player's entity thread.
     */
    public void save(EnderChestHolder holder, Inventory inventory) {
        UUID uuid = holder.getOwner();

        byte[] encoded;
        try {
            encoded = codec.encode(inventory.getContents());
        } catch (Exception e) {
            logger.error("Codec encode failure for {} — data NOT saved to prevent corruption",
                    uuid, e);
            return;
        }

        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                storage.save(uuid, encoded);
            } catch (Exception e) {
                logger.error("DB save failure for {}", uuid, e);
            }
        }, asyncExecutor);

        // Register the future so open() can wait for it before loading.
        // ConcurrentHashMap.remove(key, value) is conditional — only removes if value matches,
        // so concurrent saves for the same player don't accidentally clear each other.
        pendingSaves.put(uuid, future);
        future.whenComplete((v, e) -> pendingSaves.remove(uuid, future));
    }

    /**
     * Blocks until all in-flight async DB saves have completed.
     * Call from onDisable() before closing the storage/connection pool.
     */
    public void flushPendingSaves() {
        if (pendingSaves.isEmpty()) return;
        try {
            CompletableFuture.allOf(pendingSaves.values().toArray(new CompletableFuture[0]))
                    .get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.error("Timed out waiting for pending DB saves on shutdown — some data may be lost", e);
        }
    }

    public void shutdown() {
        flushPendingSaves();
        asyncExecutor.shutdown();
        try {
            if (!asyncExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                asyncExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            asyncExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
