package com.enhancedechest.service;

import com.enhancedechest.config.PluginConfig;
import com.enhancedechest.model.ChestKind;
import com.enhancedechest.model.ChestSummary;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Grants and revokes ender chests based on a player's permissions. A permission encodes both a count
 * and a slot size as {@code enhancedechest.additional_amount.<count>.slot.<size>} (e.g.
 * {@code enhancedechest.additional_amount.2.slot.54} → two 54-slot chests). All matching permissions
 * <i>stack</i> (summed per size), so a player gets the total of every matching permission.
 *
 * <p>The grants live as {@link ChestKind#PERM} chests: to the player they behave exactly like a NORMAL
 * chest (open/rename/icon/set-main), but admin commands never touch them and they are managed entirely
 * by {@link #reconcile} — the diff between the player's permission-derived target and the PERM chests
 * they currently own. {@link ChestOpener} runs the reconcile on open, reusing the chest list it already
 * fetched so the common case (target already matches) issues zero extra queries.
 *
 * <p>The player's base chest is inviolable: reconcile bootstraps a NORMAL chest #1 before granting any
 * PERM chest, and never deletes a NORMAL chest, so a player can never be left with no chests.
 *
 * <p>All deletions/shrinks go through {@link ChestSpillService}, so any items that no longer fit spill
 * into a temp chest (recoverable from {@code /eclist}) rather than being lost.
 */
public final class PermissionChestService {

    /** Common prefix of every grant node — a cheap {@link String#startsWith} prefilter (see below). */
    private static final String PERM_PREFIX = "enhancedechest.additional_amount.";

    /** Matches {@code enhancedechest.additional_amount.<count>.slot.<size>}; group 1 = count, 2 = size. */
    private static final Pattern PERM_PATTERN =
            Pattern.compile("^enhancedechest\\.additional_amount\\.(\\d+)\\.slot\\.(\\d+)$");

    /** Prefix of the base-chest size override node — a cheap {@link String#startsWith} prefilter. */
    private static final String DEFAULT_SIZE_PREFIX = "enhancedechest.default_size.";

    /** Matches {@code enhancedechest.default_size.<size>}; group 1 = size. Largest matching size wins. */
    private static final Pattern DEFAULT_SIZE_PATTERN =
            Pattern.compile("^enhancedechest\\.default_size\\.(\\d+)$");

    private final StorageGateway storageGateway;
    private final ChestSpillService spillService;

    // Runtime-tunable via /ee reload (see setConfig). volatile so values written on the main thread
    // during a reload are visible to the async/entity threads that read them.
    /** Master switch: when false, reconcile is a no-op (existing PERM chests are left untouched). */
    private volatile boolean enabled;
    /**
     * Config {@code enderchest.default-size}: the base NORMAL chest's size when the player has no
     * {@code default_size} permission, and the size a revoked base chest shrinks back to.
     */
    private volatile int defaultSize;

    // One reconcile per owner at a time: a second open while a reconcile is in flight skips reconcile
    // and uses the list it already has. Reconcile is idempotent and serialized per (owner, index) by
    // ChestSpillService, so skipping a concurrent run never loses or corrupts state.
    private final ConcurrentHashMap<UUID, CompletableFuture<?>> inFlight = new ConcurrentHashMap<>();

    public PermissionChestService(StorageGateway storageGateway, ChestSpillService spillService,
                                  boolean enabled, int defaultSize) {
        this.storageGateway = storageGateway;
        this.spillService   = spillService;
        this.enabled        = enabled;
        this.defaultSize    = defaultSize;
    }

    /** Re-applies the runtime-tunable values after a {@code /ee reload}. Dupe-safe (only future work). */
    public void setConfig(boolean enabled, int defaultSize) {
        this.enabled     = enabled;
        this.defaultSize = defaultSize;
    }

    /**
     * Computes the player's permission-derived target as {@code size → count}, summing every matching
     * permission. Must be called on the player's entity thread ({@code getEffectivePermissions}).
     * Returns an empty map when the feature is disabled.
     */
    public Map<Integer, Integer> resolveDesired(Player player) {
        if (!enabled) return Map.of();
        Map<Integer, Integer> desired = new HashMap<>();
        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            if (!info.getValue()) continue;
            // This runs on the region/main thread over the player's full effective-permission set (often
            // hundreds of nodes with LuckPerms). A cheap startsWith prefilter skips the regex Matcher
            // allocation for the ~all of them that aren't grant nodes.
            String node = info.getPermission();
            if (!node.startsWith(PERM_PREFIX)) continue;
            Matcher m = PERM_PATTERN.matcher(node);
            if (!m.matches()) continue;
            int count;
            int size;
            try {
                count = Integer.parseInt(m.group(1));
                size  = Integer.parseInt(m.group(2));
            } catch (NumberFormatException e) {
                continue; // overflowed int in the permission node — ignore it
            }
            if (count < 1 || !PluginConfig.isValidSize(size)) continue;
            desired.merge(size, count, Integer::sum);
        }
        return desired;
    }

    /**
     * Resolves the player's base-chest size override from their {@code enhancedechest.default_size.<size>}
     * permissions: the <b>largest</b> valid size they hold, or {@code 0} when they hold none (base stays at
     * the config default, and shrinks back to it if a permission was revoked). Always active — the
     * override is a pure permission feature with no config toggle.
     *
     * <p>Like {@link #resolveDesired}, this must run on the player's entity thread
     * ({@code getEffectivePermissions}) and uses a cheap {@link String#startsWith} prefilter to skip the
     * regex for the many nodes that are not size overrides.
     */
    public int resolveDefaultTarget(Player player) {
        int max = 0;
        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            if (!info.getValue()) continue;
            String node = info.getPermission();
            if (!node.startsWith(DEFAULT_SIZE_PREFIX)) continue;
            Matcher m = DEFAULT_SIZE_PATTERN.matcher(node);
            if (!m.matches()) continue;
            int size;
            try {
                size = Integer.parseInt(m.group(1));
            } catch (NumberFormatException e) {
                continue; // overflowed int in the permission node — ignore it
            }
            if (!PluginConfig.isValidSize(size)) continue;
            if (size > max) max = size;
        }
        return max;
    }

    /**
     * Reconciles both the player's PERM chests (against their permission-derived {@code desired} target)
     * <b>and</b> the base NORMAL chest's size (against the {@code default_size} permission), then completes
     * with the up-to-date chest list. When nothing needs to change, returns the passed-in list with no DB
     * writes (fast path).
     *
     * <p><b>PERM chests.</b> The diff keeps as many items in place as possible: existing PERM chests
     * already at a desired size are kept untouched; surplus PERM chests are resized in place to fill a
     * still-missing size (preserving their items, name and icon — only a shrink spills the overflow); any
     * remaining missing sizes create fresh empty chests; and any true surplus is removed with its items
     * spilled to temp.
     *
     * <p><b>Base chest size.</b> Driven by {@code defaultTarget} (from {@link #resolveDefaultTarget}) and
     * the persisted {@code appliedDefault} baseline:
     * <ul>
     *   <li>{@code defaultTarget > 0} — the player holds a {@code default_size} permission: the base chest
     *       is resized to it (grow, or shrink spilling the overflow to a temp chest);</li>
     *   <li>{@code defaultTarget == 0} with {@code appliedDefault > 0} — the permission was just revoked:
     *       the base chest shrinks back to the config default (spilling any overflow);</li>
     *   <li>{@code defaultTarget == 0} with {@code appliedDefault == 0} — never permission-managed: the
     *       base chest is left alone (its size is the admin/config domain).</li>
     * </ul>
     * A base chest bootstrapped here is created directly at the permission size when one applies, so a
     * brand-new player with the permission never needs a follow-up resize. Persisting the new baseline is
     * the caller's job (it equals {@code defaultTarget}); this method only moves items.
     *
     * @param defaultTarget  the base-chest size override (0 = no {@code default_size} permission held)
     * @param appliedDefault the persisted {@code applied_default_size} baseline (last permission-set size)
     * @param chests         the player's current chests (already fetched by the caller)
     */
    public CompletableFuture<List<ChestSummary>> reconcile(UUID owner, Map<Integer, Integer> desired,
                                                           int defaultTarget, int appliedDefault,
                                                           List<ChestSummary> chests) {
        // PERM-chest reconcile disabled AND base-size override inert (no permission, no baseline): never
        // mutate. The base-size override itself has no toggle — it is inert only when nothing applies.
        boolean baseManaged = defaultTarget > 0 || (defaultTarget == 0 && appliedDefault > 0);
        if (!enabled && !baseManaged) {
            return CompletableFuture.completedFuture(chests);
        }

        boolean hasNormal = chests.stream().anyMatch(c -> c.kind() == ChestKind.NORMAL);
        List<ChestSummary> existing = chests.stream()
                .filter(c -> c.kind() == ChestKind.PERM)
                .sorted(Comparator.comparingInt(ChestSummary::index))
                .toList();
        Map<Integer, Integer> existMulti = new HashMap<>();
        for (ChestSummary c : existing) existMulti.merge(c.size(), 1, Integer::sum);

        // The base chest is the lowest-indexed NORMAL chest — the one the default_size permission governs.
        ChestSummary base = chests.stream()
                .filter(c -> c.kind() == ChestKind.NORMAL)
                .min(Comparator.comparingInt(ChestSummary::index))
                .orElse(null);
        // Size a freshly bootstrapped base chest at the permission size when one applies (else config default).
        int bootstrapSize = defaultTarget > 0 ? defaultTarget : defaultSize;
        // Size an existing base chest should be moved to (>0), or -1 when it must be left alone.
        int baseTarget = defaultTarget > 0 ? defaultTarget
                : (defaultTarget == 0 && appliedDefault > 0 ? defaultSize : -1);
        boolean baseResizeNeeded = base != null && baseTarget > 0 && base.size() != baseTarget;

        boolean needsBase = !hasNormal;
        // The PERM diff only runs when that feature is enabled; when it is off we still may touch the base.
        boolean permChanged = enabled && (needsBase || !existMulti.equals(desired));

        // Fast path: nothing to do for the base or the PERM chests — no writes, no re-list.
        if (!needsBase && !baseResizeNeeded && !permChanged) {
            return CompletableFuture.completedFuture(chests);
        }

        // Guard against a concurrent reconcile for the same owner: skip and use the current list.
        CompletableFuture<List<ChestSummary>> result = new CompletableFuture<>();
        if (inFlight.putIfAbsent(owner, result) != null) {
            return CompletableFuture.completedFuture(chests);
        }

        final int baseIndex = base != null ? base.index() : -1;
        try {
            // Bootstrap the inviolable base NORMAL chest first, if the player somehow has none. It is
            // created directly at the permission size when one applies, so no follow-up resize is needed.
            CompletableFuture<Void> chain = needsBase
                    ? storageGateway.createChestAsync(owner, bootstrapSize, null).thenApply(created -> null)
                    : CompletableFuture.completedFuture(null);

            // Grow/shrink the existing base chest to its permission-derived size (shrink spills to a temp
            // chest, exactly like a PERM-chest shrink). Runs before the PERM diff; order is irrelevant since
            // each op serializes per (owner, index) in ChestSpillService.
            if (baseResizeNeeded) {
                chain = chain.thenCompose(v -> spillService.resizeOrSpill(owner, baseIndex, baseTarget));
            }

            // The PERM-chest diff only applies when that feature is enabled.
            if (!enabled) {
                chain.thenCompose(v -> storageGateway.listChestsAsync(owner))
                        .whenComplete((list, ex) -> {
                            inFlight.remove(owner, result);
                            if (ex != null) result.completeExceptionally(ex);
                            else result.complete(list);
                        });
                return result;
            }

            // 1) Keep every existing PERM chest already at a still-desired size (untouched, zero risk).
            Map<Integer, Integer> remaining = new HashMap<>(desired);
            List<ChestSummary> leftover = new ArrayList<>();
            for (ChestSummary c : existing) {
                int cnt = remaining.getOrDefault(c.size(), 0);
                if (cnt > 0) remaining.put(c.size(), cnt - 1);
                else leftover.add(c);
            }
            // 2) Flatten the still-missing sizes into a list of needed slots.
            List<Integer> needed = new ArrayList<>();
            remaining.forEach((size, cnt) -> {
                for (int k = 0; k < cnt; k++) needed.add(size);
            });
            // 3) Pair surplus chests (lowest index first) with missing sizes → resize in place (keeps
            //    items/name/icon; shrink spills overflow). Out of surplus → create a fresh empty chest.
            int i = 0;
            for (int size : needed) {
                if (i < leftover.size()) {
                    int index = leftover.get(i++).index();
                    chain = chain.thenCompose(v -> spillService.resizeOrSpill(owner, index, size));
                } else {
                    chain = chain.thenCompose(v ->
                            storageGateway.createPermChestAsync(owner, size).thenApply(x -> null));
                }
            }
            // 4) True surplus (no missing size left to absorb it) → remove with spill, highest index first.
            for (int j = leftover.size() - 1; j >= i; j--) {
                int index = leftover.get(j).index();
                chain = chain.thenCompose(v -> spillService.removeChest(owner, index, false));
            }

            chain.thenCompose(v -> storageGateway.listChestsAsync(owner))
                    .whenComplete((list, ex) -> {
                        inFlight.remove(owner, result);
                        if (ex != null) result.completeExceptionally(ex);
                        else result.complete(list);
                    });
        } catch (RuntimeException e) {
            inFlight.remove(owner, result);
            throw e;
        }
        return result;
    }
}
