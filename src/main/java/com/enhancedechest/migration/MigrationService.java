package com.enhancedechest.migration;

import com.enhancedechest.model.EnderChestData;
import com.enhancedechest.serialization.CodecException;
import com.enhancedechest.serialization.ContainerCodec;
import com.enhancedechest.service.ChestSessionManager;
import com.enhancedechest.storage.EnderChestStorage;
import com.tcoded.folialib.FoliaLib;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Orchestrates migration of vanilla enderchest data into the plugin's storage.
 *
 * <p>Single-location invariant: items are moved from the vanilla EC to the plugin DB with no window
 * where a player can reach them in both places.
 *
 * <p><b>Why the migration runs under the session manager's exclusivity primitives:</b> the DB write
 * targets chest #1, which may have a <i>live shared session</i> at that moment (the player raced a
 * {@code /ec} open against the join pre-check, or an admin ran {@code /ee migrate} while the chest was
 * open). Writing underneath a live session would be silently undone by that session's save on close —
 * with the vanilla EC already cleared, the migrated items would be lost. So the write phase first
 * {@link ChestSessionManager#forceCloseAll force-closes} any session of chest #1 (flushing its
 * contents, so the merge below reads the freshest state) and then runs inside
 * {@link ChestSessionManager#runExclusive}, which makes any concurrent open of chest #1 <b>wait</b>
 * behind the migration and then load the migrated contents. This reuses the same per-(owner, index)
 * serialization every other chest mutation uses — no separate lock.
 *
 * <p><b>Why merge-spill-and-flag is one atomic transaction:</b> chest #1 may already hold items when
 * the migration write runs (the player deposited during the pre-check window, or the row pre-existed
 * un-flagged), so the write <i>merges</i> the vanilla snapshot into the chest's current contents
 * instead of replacing them — and chest #1's size is <b>never</b> changed (its size is the admin /
 * permission domain): vanilla items that do not fit spill into a recoverable temp chest, exactly like
 * every other overflow in the plugin. A merge is <b>not idempotent</b> — running it twice would
 * duplicate the vanilla items — so the migrated flag, the merged contents and the overflow temp chest
 * must all become visible together: {@link EnderChestStorage#completeMigration} commits the three in
 * one transaction. No crash or concurrently-queued second migration can ever observe "merged but not
 * flagged" and merge again.
 */
public final class MigrationService {

    private final EnderChestStorage storage;
    private final ContainerCodec codec;
    private final Logger logger;
    private final ChestSessionManager sessions;
    private final FoliaLib foliaLib;

    // Runtime-tunable via /ee reload (see setTempExpiry). volatile so the value written on the main
    // thread during a reload is visible to the async thread that stamps a freshly spilled temp chest.
    /** Lifetime, in milliseconds, of the temp chest created when migrated items overflow chest #1. */
    private volatile long tempExpiryMillis;

    public MigrationService(EnderChestStorage storage, ContainerCodec codec, Logger logger,
                            ChestSessionManager sessions, FoliaLib foliaLib, long tempExpiryMillis) {
        this.storage          = storage;
        this.codec            = codec;
        this.logger           = logger;
        this.sessions         = sessions;
        this.foliaLib         = foliaLib;
        this.tempExpiryMillis = tempExpiryMillis;
    }

    /** Re-applies the runtime-tunable temp-chest lifetime after a {@code /ee reload}. Only affects temp chests stamped after this call. */
    public void setTempExpiry(long tempExpiryMillis) {
        this.tempExpiryMillis = tempExpiryMillis;
    }

    /**
     * Migrates a live online player's vanilla enderchest into their chest #1, in three phases:
     * <ol>
     *   <li><b>Entity thread</b> — snapshot the vanilla contents (cloned, so the stacks can safely
     *       cross threads);</li>
     *   <li><b>Exclusive DB phase</b> — force-close any live session of chest #1, then (behind its
     *       save) re-check the migrated flag, ensure chest #1 exists, merge the snapshot into the
     *       chest's current contents (never resizing it — overflow spills to a temp chest), and
     *       commit contents + overflow + migrated flag in one atomic transaction. The flag re-check
     *       inside the exclusive section plus the atomic flag write make any racing double-run
     *       (join + concurrent {@code /ee migrate}, or two admin runs) a no-op;</li>
     *   <li><b>Entity thread again</b> — clear the vanilla EC. The DB copy is already authoritative
     *       and flagged; if the player logged out in the meantime the stale vanilla copy simply stays
     *       behind un-cleared, unreachable in-game (the plugin intercepts every ender chest open).</li>
     * </ol>
     *
     * @return a future completing with {@code true} if migration ran to completion; {@code false} if
     *         the player was already migrated, went offline before the snapshot, or encoding failed
     */
    public CompletableFuture<Boolean> migrateOnline(Player player) {
        UUID uuid = player.getUniqueId();
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        foliaLib.getScheduler().runAtEntity(player, t -> {
            if (!player.isOnline()) {
                result.complete(false);
                return;
            }
            // Clone the snapshot: getContents() hands out live mirrors, and these stacks are read on
            // the DB executor while the player keeps playing.
            ItemStack[] mirror = player.getEnderChest().getContents();
            ItemStack[] vanilla = new ItemStack[mirror.length];
            for (int i = 0; i < mirror.length; i++) {
                vanilla[i] = mirror[i] != null ? mirror[i].clone() : null;
            }
            sessions.forceCloseAll(uuid, 1)
                    .thenCompose(v -> sessions.runExclusive(uuid, 1,
                            () -> migrateExclusive(uuid, player.getName(), vanilla)))
                    .thenCompose(ran -> ran ? clearVanilla(player)
                            : CompletableFuture.completedFuture(false))
                    .whenComplete((ran, err) -> {
                        if (err != null) result.completeExceptionally(err);
                        else result.complete(ran);
                    });
        });
        return result;
    }

    /**
     * The exclusive DB phase (runs on the async executor, serialized per (owner, chest #1) behind any
     * in-flight save). Decoding/encoding the thread-confined arrays off-thread is safe — the
     * global-thread encode rule protects live shared inventories, and nothing here is shared.
     */
    private boolean migrateExclusive(UUID uuid, String name, ItemStack[] vanilla) {
        if (storage.isMigrated(uuid)) {
            return false;
        }

        storage.ensureChest(uuid, 1, ContainerCodec.MAX_SIZE);

        int moved = countNonEmpty(vanilla);
        if (moved == 0) {
            // Nothing to move: just flag the row (single atomic UPDATE), leaving its contents alone.
            storage.setMigrated(uuid, true);
            logger.info("Migrated {} — vanilla EC was empty", name);
            return true;
        }

        EnderChestData chest = storage.loadChest(uuid, 1);
        int size = chest != null ? chest.size() : ContainerCodec.MAX_SIZE;
        ItemStack[] existing;
        if (chest != null && chest.containerData() != null && chest.containerData().length > 0) {
            try {
                existing = codec.decode(chest.containerData(), size);
            } catch (CodecException e) {
                logger.error("Cannot decode existing chest #1 for {} — migration aborted to protect stored data",
                        name, e);
                return false;
            }
        } else {
            existing = new ItemStack[size];
        }

        MergeResult merged = mergeContents(existing, vanilla);

        byte[] encoded;
        byte[] overflowBytes = null;
        int tempSize = 0;
        try {
            encoded = codec.encode(merged.contents());
            if (merged.overflow() != null) {
                overflowBytes = codec.encode(merged.overflow());
                tempSize = requiredTempSize(merged.overflow().length);
            }
        } catch (Exception e) {
            logger.error("Codec encode failed during migration for {} — migration aborted", name, e);
            return false;
        }

        storage.completeMigration(uuid, encoded, overflowBytes, tempSize,
                System.currentTimeMillis() + tempExpiryMillis);
        if (merged.overflow() != null) {
            logger.info("Migrated {} — {} item(s) moved from vanilla EC, {} spilled to a temp chest",
                    name, moved, merged.overflow().length);
        } else {
            logger.info("Migrated {} — {} item(s) moved from vanilla EC", name, moved);
        }
        return true;
    }

    /**
     * A merge outcome: chest #1's new contents (array length = the chest's unchanged size) and the
     * vanilla stacks that did not fit (compact, null when everything fit).
     */
    private record MergeResult(ItemStack[] contents, ItemStack @Nullable [] overflow) {}

    /**
     * Combines chest #1's current contents with the vanilla snapshot <b>without ever resizing the
     * chest</b> — its size is the admin / permission domain, so overflow spills instead.
     *
     * <p>Fresh chest whose size covers every used vanilla slot (the overwhelmingly common case — a
     * migration-created chest is 54 slots): the vanilla slot layout is kept as-is (item in vanilla
     * slot N lands in chest slot N).
     *
     * <p>Otherwise (chest already holds items, or it is smaller than the vanilla EC): existing items
     * stay exactly where they are, each vanilla stack fills the first free slot, and whatever no
     * longer fits is returned as overflow for the temp chest.
     */
    private static MergeResult mergeContents(ItemStack[] existing, ItemStack[] vanilla) {
        int size = existing.length;

        if (countNonEmpty(existing) == 0) {
            int lastUsed = lastNonEmpty(vanilla);
            if (lastUsed < size) {
                ItemStack[] result = new ItemStack[size];
                for (int i = 0; i <= lastUsed; i++) result[i] = vanilla[i];
                return new MergeResult(result, null);
            }
        }

        ItemStack[] result = Arrays.copyOf(existing, size);
        List<ItemStack> overflow = new ArrayList<>();
        int cursor = 0;
        for (ItemStack item : vanilla) {
            if (isEmptyStack(item)) continue;
            while (cursor < size && !isEmptyStack(result[cursor])) cursor++;
            if (cursor < size) result[cursor++] = item;
            else overflow.add(item);
        }
        return new MergeResult(result,
                overflow.isEmpty() ? null : overflow.toArray(new ItemStack[0]));
    }

    /** Smallest multiple of 9 holding {@code count} compactly-packed stacks (min 9, cap 54 — same shape as ChestSpillService). */
    private static int requiredTempSize(int count) {
        int step = ContainerCodec.SLOT_STEP;
        int needed = ((Math.max(count, 1) + step - 1) / step) * step;
        return Math.min(ContainerCodec.MAX_SIZE, needed);
    }

    private static int lastNonEmpty(ItemStack[] items) {
        int last = -1;
        for (int i = 0; i < items.length; i++) {
            if (!isEmptyStack(items[i])) last = i;
        }
        return last;
    }

    private static boolean isEmptyStack(@Nullable ItemStack item) {
        return item == null || item.getType().isAir();
    }

    /**
     * Phase three: clear the vanilla EC on the entity thread. Purely cosmetic-critical — the DB copy
     * is already written and flagged, so a player who logged out in the meantime just keeps a stale,
     * in-game-unreachable vanilla copy (never re-merged: the flag is set).
     */
    private CompletableFuture<Boolean> clearVanilla(Player player) {
        CompletableFuture<Boolean> done = new CompletableFuture<>();
        foliaLib.getScheduler().runAtEntity(player, t -> {
            if (player.isOnline()) {
                player.getEnderChest().clear();
            } else {
                logger.info("{} left before their vanilla EC could be cleared — contents already migrated",
                        player.getName());
            }
            done.complete(true);
        });
        return done;
    }

    private static int countNonEmpty(ItemStack[] items) {
        int count = 0;
        for (ItemStack item : items) {
            if (!isEmptyStack(item)) count++;
        }
        return count;
    }
}
