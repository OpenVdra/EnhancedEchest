package com.enhancedechest.service;

import com.enhancedechest.model.ChestKind;
import com.enhancedechest.model.ChestSummary;
import com.enhancedechest.model.EnderChestData;
import com.enhancedechest.serialization.CodecException;
import com.enhancedechest.serialization.ContainerCodec;
import com.enhancedechest.storage.EnderChestStorage;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Item-moving chest operations: shrink-spill, delete (spill or force), bulk delete, and the reverse
 * move that pulls a temp chest back into a newly granted chest ({@link #reclaimTempInto}). Each first
 * force-closes every viewer's GUI via {@link ChestSessionManager#forceCloseAll} (flushing the live
 * session), then runs the load-decode-split exclusively per (owner, index) via
 * {@link ChestSessionManager#runExclusive} so no concurrent open sees a half-applied state — keeping
 * the swaps dupe-safe even with multiple concurrent viewers. Drives {@code /ee resize}, {@code /ee
 * delete}, and the expiry sweeper.
 */
public final class ChestSpillService {

    private final ChestSessionManager sessions;
    private final EnderChestStorage storage;
    private final ContainerCodec codec;
    private final StorageGateway storageGateway;

    // Runtime-tunable via /ee reload (see setTempConfig). volatile so the values written on the main
    // thread during a reload are visible to the async threads that stamp a freshly spilled temp chest.
    /** Lifetime, in milliseconds, of a temp chest created when items spill on shrink/delete/expire. */
    private volatile long tempExpiryMillis;
    /** Config {@code temp-enderchest.auto-reclaim}: gates {@link #reclaimTempInto}. */
    private volatile boolean autoReclaim;

    public ChestSpillService(ChestSessionManager sessions, EnderChestStorage storage,
                             ContainerCodec codec, StorageGateway storageGateway,
                             long tempExpiryMillis, boolean autoReclaim) {
        this.sessions         = sessions;
        this.storage          = storage;
        this.codec            = codec;
        this.storageGateway   = storageGateway;
        this.tempExpiryMillis = tempExpiryMillis;
        this.autoReclaim      = autoReclaim;
    }

    /**
     * Re-applies the runtime-tunable temp-chest settings after a {@code /ee reload}. Only affects temp
     * chests stamped (and reclaims started) <i>after</i> this call, so it is dupe-safe to set on the main
     * thread while async storage work is pending.
     */
    public void setTempConfig(long tempExpiryMillis, boolean autoReclaim) {
        this.tempExpiryMillis = tempExpiryMillis;
        this.autoReclaim      = autoReclaim;
    }

    /**
     * Resizes a chest, spilling any cut-off items into a temp chest if it is shrunk below its used
     * slots. Force-closes every viewer's GUI first (flushing the live session), then runs the
     * load-decode-split exclusively per (owner, index) so no concurrent open sees a half-applied state.
     * A grow, or a shrink that loses no items, is a plain resize.
     */
    public CompletableFuture<Void> resizeOrSpill(UUID owner, int index, int newSize) {
        return sessions.forceCloseAll(owner, index).thenCompose(v -> sessions.runExclusive(owner, index, () -> {
            EnderChestData data = storage.loadChest(owner, index);
            if (data == null) return null;

            ItemStack[] all = decodeAll(data);
            // Nothing occupies a slot at or beyond newSize → a plain resize loses no items.
            if (lastUsedSlot(all) < newSize) {
                storage.resizeChest(owner, index, newSize);
                return null;
            }

            ItemStack[] visible  = Arrays.copyOfRange(all, 0, newSize);
            ItemStack[] overflow = Arrays.copyOfRange(all, newSize, all.length);
            byte[] visibleBytes  = codec.encode(visible);
            byte[] overflowBytes = codec.encode(overflow);
            storage.spillShrink(owner, index, newSize, visibleBytes, overflowBytes,
                    requiredTempSize(overflow), System.currentTimeMillis() + tempExpiryMillis);
            return null;
        }));
    }

    /**
     * Removes a chest. With {@code force} the row is hard-deleted (items lost immediately); otherwise
     * any items are spilled into a temp chest first. Force-closes every viewer's GUI, then performs the
     * delete exclusively per (owner, index) so the swap is dupe-safe. Used by {@code /ee delete} and
     * by the expiry sweeper (NORMAL → spill, TEMP → force).
     */
    public CompletableFuture<Void> removeChest(UUID owner, int index, boolean force) {
        return sessions.forceCloseAll(owner, index).thenCompose(v -> sessions.runExclusive(owner, index, () -> {
            if (force) {
                storage.deleteChest(owner, index);
                return null;
            }
            EnderChestData data = storage.loadChest(owner, index);
            byte[] items = null;
            int tempSize = 0;
            if (data != null && data.containerData() != null && data.containerData().length > 0
                    && lastUsedSlot(decodeAll(data)) >= 0) {
                // Reuse the already-encoded bytes; the temp chest mirrors the original chest's size.
                items = data.containerData();
                tempSize = data.size();
            }
            storage.spillRemove(owner, index, items, tempSize,
                    System.currentTimeMillis() + tempExpiryMillis);
            return null;
        }));
    }

    /**
     * The inverse of a spill: moves one temp chest's items <b>whole</b> into the freshly created,
     * still-empty chest at {@code targetIndex}, then deletes the temp chest. Called right after a new
     * chest is granted (a permission reconcile, {@code /ee add}), so a player who lost a chest and got
     * its items parked in a temp chest gets them back automatically when a chest of at least that size
     * comes back. A no-op (completing {@code false}) when the feature is off, when the target is gone or
     * is itself a temp chest, or when no temp chest fits.
     *
     * <p><b>One temp chest, one target, no repacking.</b> Only a temp chest whose slot count is {@code <=}
     * the target's is eligible, and its stored bytes are copied verbatim, so every item lands on the exact
     * slot it already occupied. Items are never split across several chests and an existing layout is
     * never reshuffled — a partially fitting temp chest is left alone rather than merged in. When several
     * temp chests fit, the one expiring soonest is taken first: it is the one closest to being lost.
     *
     * <p>Dupe-safety follows the same model as the spills above, widened to the two chests involved:
     * both GUIs are force-closed (flushing any live session), then the swap runs behind the pending saves
     * of <i>both</i> keys via {@link ChestSessionManager#runExclusiveAcross}, and the storage call
     * re-checks every precondition under the cache lock.
     */
    public CompletableFuture<Boolean> reclaimTempInto(UUID owner, int targetIndex) {
        if (!autoReclaim) {
            return CompletableFuture.completedFuture(false);
        }
        return storageGateway.listChestsAsync(owner).thenCompose(chests -> {
            ChestSummary target = chests.stream()
                    .filter(c -> c.index() == targetIndex)
                    .findFirst()
                    .orElse(null);
            if (target == null || target.kind() == ChestKind.TEMP) {
                return CompletableFuture.completedFuture(false);
            }
            ChestSummary temp = chests.stream()
                    .filter(c -> c.kind() == ChestKind.TEMP && c.size() <= target.size())
                    .min(SOONEST_EXPIRY)
                    .orElse(null);
            if (temp == null) {
                return CompletableFuture.completedFuture(false);
            }
            int tempIndex = temp.index();
            List<ChestSessionManager.ChestRef> refs = List.of(
                    new ChestSessionManager.ChestRef(owner, tempIndex),
                    new ChestSessionManager.ChestRef(owner, targetIndex));
            return sessions.forceCloseAll(owner, tempIndex)
                    .thenCompose(v -> sessions.forceCloseAll(owner, targetIndex))
                    .thenCompose(v -> sessions.runExclusiveAcross(refs,
                            () -> storage.reclaimTemp(owner, tempIndex, targetIndex)));
        });
    }

    /** Reclaim order: the temp chest closest to expiry first, then the lowest index. */
    private static final Comparator<ChestSummary> SOONEST_EXPIRY =
            Comparator.comparingLong((ChestSummary c) -> c.expiresAt() == null ? Long.MAX_VALUE : c.expiresAt())
                    .thenComparingInt(ChestSummary::index);

    /**
     * Empties a chest's contents (admin "clear chest" action), keeping the chest itself (size, name,
     * icon, kind). Force-closes every viewer's GUI first so the live session can't re-save the old
     * contents over the wipe, then clears exclusively per (owner, index) so the swap is dupe-safe.
     */
    public CompletableFuture<Void> clearChest(UUID owner, int index) {
        return sessions.forceCloseAll(owner, index).thenCompose(v ->
                sessions.runExclusive(owner, index, () -> {
                    storage.clearChestContents(owner, index);
                    return null;
                }));
    }

    /**
     * Sorts a chest's contents in place: identical items are merged into full stacks and everything is
     * reordered by item type (then by descending stack size), packed from the first slot. Force-closes
     * every viewer's GUI first (so the live session can't re-save the old order over the sort), then runs
     * the load-sort-save exclusively per (owner, index) so the swap is dupe-safe. A no-op for a missing or
     * empty chest. The chest's size, name, icon and kind are untouched — only the item layout changes.
     */
    public CompletableFuture<Void> sortChest(UUID owner, int index) {
        return sessions.forceCloseAll(owner, index).thenCompose(v ->
                sessions.runExclusive(owner, index, () -> {
                    EnderChestData data = storage.loadChest(owner, index);
                    if (data == null || data.containerData() == null || data.containerData().length == 0) {
                        return null;
                    }
                    ItemStack[] sorted = sortItems(decodeAll(data), data.size());
                    storage.saveChest(owner, index, codec.encode(sorted));
                    return null;
                }));
    }

    /**
     * Builds the sorted layout for a chest of {@code size} slots: collects every non-empty stack, merges
     * similar stacks up to their max size, sorts the result by material key then descending amount, and
     * packs them from slot 0. Merging only ever reduces the slot count, so the result always fits in the
     * original size.
     */
    private static ItemStack[] sortItems(ItemStack[] items, int size) {
        // Only the chest's real slots (0..size) hold reachable items; anything decoded beyond that is a
        // phantom the live inventory never loads, so ignoring it keeps the merged count <= size and means
        // the pack loop below can never drop a reachable item.
        List<ItemStack> stacks = new ArrayList<>();
        int limit = Math.min(size, items.length);
        for (int i = 0; i < limit; i++) {
            if (!isEmpty(items[i])) stacks.add(items[i].clone());
        }
        List<ItemStack> merged = mergeSimilar(stacks);
        merged.sort(SORT_ORDER);

        ItemStack[] result = new ItemStack[size];
        Arrays.fill(result, ItemStack.empty());
        for (int i = 0; i < merged.size() && i < size; i++) {
            result[i] = merged.get(i);
        }
        return result;
    }

    /**
     * Combines stacks that {@link ItemStack#isSimilar(ItemStack) match} (same type and metadata) into full
     * stacks, respecting each item's max stack size. Items with NBT (enchantments, custom names, …) only
     * merge with truly identical items, so nothing is ever lost or mixed across distinct items.
     */
    private static List<ItemStack> mergeSimilar(List<ItemStack> stacks) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack stack : stacks) {
            int remaining = stack.getAmount();
            int max = stack.getMaxStackSize();
            for (ItemStack existing : out) {
                if (remaining <= 0) break;
                if (existing.getAmount() < max && existing.isSimilar(stack)) {
                    int move = Math.min(max - existing.getAmount(), remaining);
                    existing.setAmount(existing.getAmount() + move);
                    remaining -= move;
                }
            }
            while (remaining > 0) {
                int take = Math.min(max, remaining);
                ItemStack copy = stack.clone();
                copy.setAmount(take);
                out.add(copy);
                remaining -= take;
            }
        }
        return out;
    }

    /** Sort order: group by material key (alphabetical), fuller stacks first within a group. */
    private static final Comparator<ItemStack> SORT_ORDER =
            Comparator.comparing((ItemStack it) -> it.getType().getKey().toString())
                    .thenComparing(Comparator.comparingInt(ItemStack::getAmount).reversed());

    /**
     * Bulk-removes the {@code count} highest-numbered NORMAL chests a player owns, spilling (or
     * force-discarding) each, and completes with the number actually removed. The player's <i>first</i>
     * chest — the lowest-indexed NORMAL chest — is always protected, so a player can never be left with
     * no chests; deleting fewer than {@code count} when that is all that is eligible is not an error.
     * Temp chests are ignored (they are transient and expire on their own).
     *
     * <p>Targets are snapshotted up front, then deleted sequentially: a spilling delete creates a fresh
     * temp chest at a higher index, but those are not in the target list so they are never re-touched.
     * Each per-index delete still serializes behind its own pending saves and force-closes open GUIs,
     * so the bulk op is dupe-safe.
     */
    public CompletableFuture<Integer> removeNewestChests(UUID owner, int count, boolean force) {
        return storageGateway.listChestsAsync(owner).thenCompose(chests -> {
            // Only NORMAL chests are eligible; sorted ascending so element 0 is the protected first chest.
            List<ChestSummary> normal = chests.stream()
                    .filter(c -> c.kind() == ChestKind.NORMAL)
                    .sorted(Comparator.comparingInt(ChestSummary::index))
                    .toList();
            if (normal.size() <= 1) {
                return CompletableFuture.completedFuture(0);
            }
            // Everything except the protected first chest, newest (highest index) first, capped at count.
            List<Integer> targets = normal.subList(1, normal.size()).stream()
                    .map(ChestSummary::index)
                    .sorted(Comparator.reverseOrder())
                    .limit(Math.max(0, count))
                    .toList();
            CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
            for (int index : targets) {
                chain = chain.thenCompose(v -> removeChest(owner, index, force));
            }
            return chain.thenApply(v -> targets.size());
        });
    }

    /** Decodes a chest's contents to a full MAX_SIZE array so all stored slots are visible. */
    private ItemStack[] decodeAll(EnderChestData data) {
        if (data.containerData() == null || data.containerData().length == 0) {
            ItemStack[] empty = new ItemStack[ContainerCodec.MAX_SIZE];
            Arrays.fill(empty, ItemStack.empty());
            return empty;
        }
        try {
            return codec.decode(data.containerData(), ContainerCodec.MAX_SIZE);
        } catch (CodecException e) {
            // Abort the spill rather than risk losing items to a bad decode; surfaces as a failure.
            throw new RuntimeException("Codec failure during spill for chest " + data.index(), e);
        }
    }

    /** Highest slot index holding a non-empty item, or -1 if the array is entirely empty. */
    private static int lastUsedSlot(ItemStack[] items) {
        for (int i = items.length - 1; i >= 0; i--) {
            if (!isEmpty(items[i])) return i;
        }
        return -1;
    }

    /** Smallest valid chest size (multiple of 9, 9..54) that holds every non-empty slot in {@code items}. */
    private static int requiredTempSize(ItemStack[] items) {
        int last = lastUsedSlot(items);
        if (last < 0) return ContainerCodec.SLOT_STEP;
        int needed = ((last / ContainerCodec.SLOT_STEP) + 1) * ContainerCodec.SLOT_STEP;
        return Math.max(ContainerCodec.SLOT_STEP, Math.min(ContainerCodec.MAX_SIZE, needed));
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir();
    }
}
