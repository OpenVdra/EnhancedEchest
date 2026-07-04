package com.enhancedechest.service;

import com.enhancedechest.config.PluginConfig;
import com.enhancedechest.gui.EnderChestHolder;
import com.enhancedechest.gui.dialog.ChestDialogs;
import com.enhancedechest.gui.dialog.ChestDialogs.DetailContext;
import com.enhancedechest.lang.LanguageManager;
import com.enhancedechest.migration.DatabaseImportService;
import com.enhancedechest.migration.SourceSpec;
import com.enhancedechest.model.ChestKind;
import com.enhancedechest.model.ChestSummary;
import com.enhancedechest.storage.EnderChestStorage;
import com.enhancedechest.util.DurationFormat;
import com.tcoded.folialib.FoliaLib;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Decides <i>what</i> to open for {@code /ec}, {@code /eclist}, right-click and the admin {@code /ee
 * view}, and orchestrates the management dialogs. The actual attach-to-shared-session work is delegated
 * to {@link ChestSessionManager#open} (the single dupe-safe funnel); this class is the GUI-flow layer
 * on top of it.
 *
 * <p>Open routing for {@code /ec} / right-click ({@link #open}):
 * <ul>
 *   <li>0 or 1 normal chest and no temp chest — opens that chest directly (creating chest #1 if the
 *       player owns none);</li>
 *   <li>2+ normal chests, no temp chest, an explicit main is set <i>and</i> the player may use it —
 *       opens the main directly;</li>
 *   <li>otherwise — opens the management list dialog so the player picks (or sets a main).</li>
 * </ul>
 * A main is never auto-assigned at creation, so a multi-chest player who has not chosen one always
 * lands on the management dialog. {@code /eclist} always reaches the dialog regardless.
 */
public final class ChestOpener {

    /** Permission to open the ender chest by command; also gates the dialog's "set as main" action. */
    private static final String OPEN_GUI_PERMISSION = "enhancedechest.command.open";

    /** Permission an admin needs to see and use the "Clear chest" button in the admin detail dialog. */
    private static final String ADMIN_CLEAR_PERMISSION = "enhancedechest.admin.clear";

    /** Permission an admin needs to <i>edit</i> (rename / icon / sort) another player's chest while viewing. */
    private static final String ADMIN_EDIT_PERMISSION = "enhancedechest.admin.edit";

    private final ChestSessionManager sessions;
    private final StorageGateway storageGateway;
    private final PlayerSettingsCache settings;
    private final EnderChestStorage storage;
    private final DbExecutor db;
    private final LanguageManager lang;
    private final FoliaLib foliaLib;
    private final Logger logger;
    private final ChestDialogs dialogs;
    private final PermissionChestService permService;
    private final ChestSpillService spillService;
    private final PluginConfig config;
    private final DatabaseImportService importService;

    /** Last time (epoch millis) each player sorted a chest, for the anti-spam Sort cooldown. */
    private final ConcurrentHashMap<UUID, Long> lastSortAt = new ConcurrentHashMap<>();

    // Runtime-tunable via /ee reload (see setDefaultSize). volatile so the value written on the main
    // thread during a reload is visible to the async open threads that read it when bootstrapping.
    /** Size of the chest auto-created the first time a player ever opens their ender chest. */
    private volatile int defaultSize;

    public ChestOpener(ChestSessionManager sessions, StorageGateway storageGateway,
                       PlayerSettingsCache settings, EnderChestStorage storage, DbExecutor db,
                       LanguageManager lang, FoliaLib foliaLib, Logger logger, int defaultSize,
                       PermissionChestService permService, ChestSpillService spillService,
                       PluginConfig config, DatabaseImportService importService) {
        this.sessions       = sessions;
        this.storageGateway = storageGateway;
        this.settings       = settings;
        this.storage        = storage;
        this.db             = db;
        this.lang           = lang;
        this.foliaLib       = foliaLib;
        this.logger         = logger;
        this.defaultSize    = defaultSize;
        this.permService    = permService;
        this.spillService   = spillService;
        this.config         = config;
        this.importService  = importService;
        this.dialogs        = new ChestDialogs(this, storageGateway, settings, lang, config);
    }

    /**
     * Re-applies the runtime-tunable default chest size after a {@code /ee reload}. Read only when
     * bootstrapping a brand-new chest, so it is dupe-safe to set on the main thread while async storage
     * work is pending.
     */
    public void setDefaultSize(int defaultSize) {
        this.defaultSize = defaultSize;
    }

    // ---- opening ----

    /**
     * Default open entry point for {@code /enderchest} and right-click:
     * <ul>
     *   <li>0 or 1 normal chest and no temp chest — opens that chest directly (creating chest #1 if
     *       the player owns none);</li>
     *   <li>2+ normal chests, no temp chest, an explicit main is set <i>and</i> the player may use it
     *       — opens the main directly;</li>
     *   <li>otherwise — opens the management list dialog so the player picks (or sets a main).</li>
     * </ul>
     *
     * <p>A main is never auto-assigned at creation, so a multi-chest player who has not chosen one
     * always lands on the management dialog. Setting a main returns them to the open-directly path.
     * Players without the open-by-command permission can never have an effective main, so with 2+
     * chests they always get the dialog. Any TEMP (overflow) chest also forces the dialog, since
     * spilled items can only be retrieved from the list. {@code /eclist} still reaches the dialog
     * regardless.
     *
     * @param sourceBlock ender chest block location if opened via right-click; null for command/dialog
     */
    public void open(Player player, @Nullable Location sourceBlock) {
        UUID uuid = player.getUniqueId();
        boolean canSetMain = canSetMain(player);
        foliaLib.getScheduler().runAtEntity(player, outerTask -> {
            closeExistingGui(player);
            // Reconcile permission-granted chests and the base-chest size override against the player's
            // permissions before routing, reusing the list /ec already fetches (no extra query in the
            // common case where nothing changed).
            reconcileForOpen(player, uuid)
                    .thenAccept(chests -> {
                        // Spilled items live in TEMP chests that can ONLY be retrieved from the list
                        // dialog, so any temp chest forces the dialog regardless of how many normal
                        // chests exist (otherwise a single-chest player could never reach the overflow).
                        boolean hasTemp = chests.stream().anyMatch(c -> c.kind() == ChestKind.TEMP);
                        // PERM chests behave exactly like NORMAL ones for routing — count both as "real".
                        long normalCount = chests.stream().filter(c -> c.kind() != ChestKind.TEMP).count();
                        // 0 or 1 normal chest and nothing spilled: open it directly. The index comes
                        // straight from the list already in hand (with one non-TEMP chest there is
                        // nothing to disambiguate — same answer getPrimaryIndex would give, minus the
                        // extra query on every open). Only a player with no chests at all still goes
                        // to the DB, to bootstrap chest #1.
                        if (!hasTemp && normalCount <= 1) {
                            chests.stream().filter(c -> c.kind() != ChestKind.TEMP)
                                    .map(ChestSummary::index).findFirst()
                                    .ifPresentOrElse(
                                            index -> sessions.open(player, uuid, index, sourceBlock),
                                            () -> openPrimaryChest(player, uuid, sourceBlock));
                            return;
                        }
                        // 2+ chests: only an explicitly-flagged main, set by a player who may use it,
                        // bypasses the dialog — and never while a temp chest is awaiting recovery.
                        // Otherwise show the management list.
                        Integer mainIndex = (!hasTemp && canSetMain)
                                ? chests.stream().filter(ChestSummary::primary)
                                        .map(ChestSummary::index).findFirst().orElse(null)
                                : null;
                        if (mainIndex != null) {
                            openChest(player, mainIndex, sourceBlock);
                        } else {
                            // Seed the edit-mode checkbox from the player's saved preference.
                            settings.loadSettingsAsync(uuid).thenAccept(s ->
                                    foliaLib.getScheduler().runAtEntity(player, task -> {
                                        if (player.isOnline()) player.showDialog(
                                                dialogs.listDialog(chests, canSetMain, sourceBlock, s.editMode()));
                                    }));
                        }
                    })
                    .exceptionally(e -> reportOpenFailure(player, e));
        });
    }

    /**
     * Loads and opens the player's primary chest inventory directly (creating chest #1 if none exist).
     * Only reached when the already-fetched chest list was empty — a player with an existing chest is
     * routed straight to it by {@link #open} without this extra getPrimaryIndex round-trip.
     */
    private void openPrimaryChest(Player player, UUID uuid, @Nullable Location sourceBlock) {
        db.supply(() -> resolvePrimaryIndex(uuid))
                .thenAccept(index -> sessions.open(player, uuid, index, sourceBlock))
                .exceptionally(e -> reportOpenFailure(player, e));
    }

    /**
     * Opens a chest selected by a free-text query from {@code /ec <chest>}:
     * <ul>
     *   <li>{@code #N} (or a bare positive integer) opens the chest with that index;</li>
     *   <li>anything else is matched case-insensitively against players' custom chest names.</li>
     * </ul>
     * A miss reports {@code chest.not-found} rather than silently opening the primary chest.
     */
    public void openByQuery(Player player, String query) {
        String trimmed = query.trim();
        Integer index = parseIndexQuery(trimmed);
        if (index != null) {
            openChest(player, index, null);
            return;
        }
        UUID uuid = player.getUniqueId();
        storageGateway.listChestsAsync(uuid).thenAccept(chests ->
                foliaLib.getScheduler().runAtEntity(player, task -> {
                    if (!player.isOnline()) return;
                    chests.stream()
                            .filter(c -> c.customName() != null
                                    && c.customName().equalsIgnoreCase(trimmed))
                            .findFirst()
                            .ifPresentOrElse(
                                    c -> openChest(player, c.index(), null),
                                    () -> player.sendMessage(lang.get("chest.not-found")));
                })
        ).exceptionally(e -> reportOpenFailure(player, e));
    }

    /** Parses a {@code #N} or bare-integer index query; returns null for non-index input. */
    private static Integer parseIndexQuery(String query) {
        String digits = query.startsWith("#") ? query.substring(1) : query;
        if (digits.isEmpty() || !digits.chars().allMatch(Character::isDigit)) {
            return null;
        }
        try {
            int value = Integer.parseInt(digits);
            return value > 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Opens a specific chest by index (from the management dialog), sharing the live session. */
    public void openChest(Player player, int index, @Nullable Location sourceBlock) {
        sessions.open(player, player.getUniqueId(), index, sourceBlock);
    }

    /**
     * Opens another player's chest for an admin, sharing the live session. The admin becomes a viewer of
     * the same inventory the owner sees (concurrent edit on Paper; exclusive on Folia). Read-only vs
     * editable is enforced per-click in the GUI listener via the admin's permissions, so this method
     * itself simply joins the session.
     */
    public void adminOpen(Player admin, UUID owner, int index) {
        sessions.open(admin, owner, index, null);
    }

    private int resolvePrimaryIndex(UUID uuid) {
        int index = storage.getPrimaryIndex(uuid);
        if (index == -1) {
            // First-ever access: bootstrap chest #1. It is NOT flagged primary — with a single chest,
            // getPrimaryIndex falls back to the lowest index, so /ec still opens it directly.
            index = storage.createChest(uuid, defaultSize);
        }
        return index;
    }

    private void closeExistingGui(Player player) {
        Inventory currentTop = player.getOpenInventory().getTopInventory();
        if (currentTop.getHolder() instanceof EnderChestHolder) {
            player.closeInventory();
        }
    }

    private Void reportOpenFailure(Player player, Throwable e) {
        logger.error("Failed to load enderchest for {} — aborting open", player.getName(),
                e.getCause() != null ? e.getCause() : e);
        foliaLib.getScheduler().runAtEntity(player, t -> {
            if (player.isOnline()) player.sendMessage(lang.get("chest.load-failed"));
        });
        return null;
    }

    // ---- management dialog ----

    /** Loads the player's chests and shows the /eclist management dialog, seeding edit mode from their saved preference. */
    public void openListDialog(Player player) {
        settings.loadSettingsAsync(player.getUniqueId())
                .thenAccept(s -> openListDialog(player, s.editMode(), null))
                .exceptionally(e -> reportOpenFailure(player, e));
    }

    /**
     * Loads the player's chests and shows the management dialog with the edit-mode checkbox in the
     * given starting state. Fresh opens seed it from the player's saved preference (see the no-arg
     * overload and {@link #open}); returning from a detail dialog's Back seeds it on so the player
     * stays in edit mode. The checkbox itself toggles client-side without re-showing.
     *
     * @param editInitial starting state of the dialog's edit-mode checkbox
     * @param sourceBlock ender chest block this menu was opened from (threaded through so direct opens
     *                    still animate), or null when opened by command
     */
    public void openListDialog(Player player, boolean editInitial, @Nullable Location sourceBlock) {
        UUID uuid = player.getUniqueId();
        boolean canSetMain = canSetMain(player);
        // Reconcile permission-granted chests before building the list, so /eclist reflects the current
        // grants. resolveTargets must read permissions on the entity thread.
        foliaLib.getScheduler().runAtEntity(player, outerTask -> {
            reconcileForOpen(player, uuid)
                    .thenAccept(chests ->
                            foliaLib.getScheduler().runAtEntity(player, task -> {
                                if (!player.isOnline()) return;
                                if (chests.isEmpty()) {
                                    // Should not happen: reconcile bootstraps the base chest before this runs.
                                    logger.warn("{} has no ender chests to list — skipping the management dialog",
                                            player.getName());
                                    return;
                                }
                                player.showDialog(dialogs.listDialog(chests, canSetMain, sourceBlock, editInitial));
                            }))
                    .exceptionally(e -> reportOpenFailure(player, e));
        });
    }

    /**
     * Resolves the player's permission-derived targets (PERM chests + base-chest size override),
     * reconciles them against storage reusing a single chest list, and persists the new default-size
     * baseline when it changed — the shared prelude for every self-open path ({@code /ec}, {@code /eclist},
     * right-click). Also owns the lazy name-index write (see {@link #recordNameIfChanged}): the first time
     * a player opens their ender chest after a rename (or ever), not on join.
     *
     * <p>Must be called on the player's entity thread: {@link PermissionChestService#resolveTargets}
     * reads the player's effective permissions (both targets in one pass over the snapshot). Settings
     * are served from the write-through cache (warm for any online player), so the common "nothing changed"
     * case adds no DB query beyond the one chest list.
     */
    private CompletableFuture<List<ChestSummary>> reconcileForOpen(Player player, UUID uuid) {
        PermissionChestService.PermTargets targets = permService.resolveTargets(player);
        java.util.Map<Integer, Integer> permTarget = targets.desired();
        int defaultTarget = targets.defaultTarget();
        return settings.loadSettingsAsync(uuid).thenCompose(s -> {
            int applied = s.appliedDefaultSize();
            recordNameIfChanged(uuid, player.getName(), s.username());
            return storageGateway.listChestsAsync(uuid)
                    .thenCompose(chests -> permService.reconcile(uuid, permTarget, defaultTarget, applied, chests))
                    .thenApply(chests -> {
                        // The reconcile moved items; record the new baseline (which equals defaultTarget,
                        // i.e. the largest default_size permission held, or 0 for none) when it changed.
                        if (defaultTarget != applied) {
                            settings.setAppliedDefaultSizeAsync(uuid, defaultTarget);
                        }
                        return chests;
                    });
        });
    }

    /**
     * Lazily records the player's current name against their UUID (offline {@code /ee view} resolution):
     * reuses the settings row {@link #reconcileForOpen} already loaded, and writes only when it differs
     * from {@code loadedUsername} — a never-recorded name (first-ever open) or a rename. A returning
     * player whose name hasn't changed costs no write, unlike writing unconditionally on every open.
     */
    private void recordNameIfChanged(UUID uuid, String currentName, @Nullable String loadedUsername) {
        if (!currentName.equals(loadedUsername)) {
            settings.setUsernameAsync(uuid, currentName);
        }
    }

    /**
     * Shows the admin "view another player's chests" list dialog. Each button opens the target's chest
     * for the admin via the shared session ({@link #adminOpen}) — no edit-mode/rename/set-main. The
     * chests are passed in already loaded (the command lists them to route 0/1/2+), so this only builds
     * and pushes the dialog on the admin's entity thread.
     */
    public void showAdminViewList(Player admin, String targetName, UUID target, List<ChestSummary> chests) {
        foliaLib.getScheduler().runAtEntity(admin, t -> {
            if (admin.isOnline()) admin.showDialog(dialogs.adminViewListDialog(targetName, target, chests));
        });
    }

    /** Reloads the target's chests and shows the admin view-list dialog (used for Back from a detail dialog). */
    public void openAdminViewList(Player admin, String targetName, UUID target) {
        storageGateway.listChestsAsync(target).thenAccept(chests ->
                foliaLib.getScheduler().runAtEntity(admin, task -> {
                    if (!admin.isOnline()) return;
                    if (chests.isEmpty()) {
                        admin.sendMessage(lang.get("admin.view-no-chests", "player", targetName));
                        return;
                    }
                    admin.showDialog(dialogs.adminViewListDialog(targetName, target, chests));
                })).exceptionally(e -> reportOpenFailure(admin, e));
    }

    /**
     * Shows the per-chest detail dialog for the target's chest, in an admin context: same menu the owner
     * sees, but mutations target the owner's chest. The appearance edits (rename / icon / sort) are built
     * only when the admin holds {@code enhancedechest.admin.edit}; the Clear button only when they hold
     * {@code enhancedechest.admin.clear} — so a view-only admin gets just Open / Back. Reachable from
     * {@code /ee view} and the admin view-list dialog.
     */
    public void openAdminDetail(Player admin, String targetName, UUID target, int index) {
        DetailContext ctx = new DetailContext(target, targetName, false,
                admin.hasPermission(ADMIN_EDIT_PERMISSION), false,
                admin.hasPermission(ADMIN_CLEAR_PERMISSION), null);
        showDetail(admin, ctx, index);
    }

    /** Shows the "are you sure?" confirmation before an admin clears a chest (guards the destructive wipe). */
    public void openAdminClearConfirm(Player admin, String targetName, UUID target, int index) {
        if (!admin.hasPermission(ADMIN_CLEAR_PERMISSION)) {
            foliaLib.getScheduler().runAtEntity(admin, t -> {
                if (admin.isOnline()) admin.sendMessage(lang.get("admin.no-permission"));
            });
            return;
        }
        storageGateway.listChestsAsync(target).thenAccept(chests ->
                foliaLib.getScheduler().runAtEntity(admin, task -> {
                    if (!admin.isOnline()) return;
                    chests.stream().filter(c -> c.index() == index).findFirst().ifPresentOrElse(
                            c -> admin.showDialog(dialogs.adminClearConfirmDialog(targetName, target, c)),
                            () -> admin.sendMessage(lang.get("chest.not-found")));
                })).exceptionally(e -> reportOpenFailure(admin, e));
    }

    /**
     * Empties the target's chest (admin "Clear chest" action), re-checking the permission, then returns
     * the admin to the detail dialog with a confirmation message. Dupe-safe: the clear force-closes every
     * viewer and runs exclusively (see {@link ChestSpillService#clearChest}).
     */
    public void adminClear(Player admin, String targetName, UUID target, int index) {
        if (!admin.hasPermission(ADMIN_CLEAR_PERMISSION)) {
            foliaLib.getScheduler().runAtEntity(admin, t -> {
                if (admin.isOnline()) admin.sendMessage(lang.get("admin.no-permission"));
            });
            return;
        }
        spillService.clearChest(target, index).thenRun(() ->
                foliaLib.getScheduler().runAtEntity(admin, t -> {
                    if (!admin.isOnline()) return;
                    admin.sendMessage(lang.get("admin.chest-cleared",
                            "player", targetName, "index", Integer.toString(index)));
                    openAdminDetail(admin, targetName, target, index);
                })).exceptionally(e -> reportOpenFailure(admin, e));
    }

    /** Shows the per-chest detail dialog for the player's own chest (Open / Rename / Icon / Sort / Set-main / Back). */
    public void openDetailDialog(Player player, int index) {
        showDetail(player, selfContext(player, null), index);
    }

    /**
     * Re-shows the detail dialog in an explicit context (own or admin), reloading the chest first. Used by
     * the dialog callbacks (rename / icon / sort / set-main) to refresh after a mutation, so an admin's
     * refresh stays an admin dialog and the owner's stays an owner dialog.
     */
    public void openDetailDialog(Player viewer, DetailContext ctx, int index) {
        showDetail(viewer, ctx, index);
    }

    /** Builds the owner's detail context: full edit rights, set-main gated on the open permission. */
    private DetailContext selfContext(Player player, @Nullable Location sourceBlock) {
        return new DetailContext(player.getUniqueId(), null, true, true, canSetMain(player), false, sourceBlock);
    }

    /**
     * Sorts a chest's contents (the Sort button), then re-shows the detail dialog. Per-player rate-limited
     * by {@code enderchest.features.sort-cooldown}: a sort while still on cooldown is rejected with a chat
     * notice and does no work, so the button can't be spammed (each sort re-reads and re-writes the chest).
     * The cooldown is keyed on the clicking viewer, so an admin sorting players' chests is limited too.
     */
    public void sortChest(Player viewer, DetailContext ctx, int index) {
        if (!config.isSortEnabled()) {
            return;
        }
        long cooldown = config.getSortCooldownMillis();
        long now = System.currentTimeMillis();
        UUID viewerId = viewer.getUniqueId();
        if (cooldown > 0) {
            Long last = lastSortAt.get(viewerId);
            if (last != null && now - last < cooldown) {
                String remaining = DurationFormat.formatRemaining(cooldown - (now - last));
                foliaLib.getScheduler().runAtEntity(viewer, t -> {
                    if (viewer.isOnline()) viewer.sendMessage(lang.get("chest.sort-cooldown", "time", remaining));
                });
                return;
            }
        }
        lastSortAt.put(viewerId, now);
        spillService.sortChest(ctx.owner(), index).thenRun(() ->
                foliaLib.getScheduler().runAtEntity(viewer, t -> {
                    if (!viewer.isOnline()) return;
                    viewer.sendMessage(lang.get("chest.sorted"));
                    showDetail(viewer, ctx, index);
                })).exceptionally(e -> reportOpenFailure(viewer, e));
    }

    /**
     * Drops a player's sort-cooldown entry, called on quit so the {@link #lastSortAt} map stays bounded by
     * the online-player count (it holds one timestamp per player who has sorted, and entries never expire
     * on their own). Idempotent — a no-op for a player who never sorted.
     */
    public void clearSortCooldown(UUID playerId) {
        lastSortAt.remove(playerId);
    }

    /**
     * Whether the player may set a chest as their main: gated on the open-by-command permission,
     * since the "main" chest only matters when {@code /enderchest} can be used to open it.
     */
    private boolean canSetMain(Player player) {
        return player.hasPermission(OPEN_GUI_PERMISSION);
    }

    /** Shows the dedicated rename dialog for the player's own chest. */
    public void openRenameDialog(Player player, int index) {
        DetailContext ctx = selfContext(player, null);
        showChestDialog(player, ctx.owner(), index, chest -> dialogs.renameDialog(chest, ctx));
    }

    /**
     * Reloads a chest (from {@code ctx.owner()}) and re-shows its detail dialog in the given context, on
     * the viewer's thread. The single entry point for opening/refreshing the detail menu, owner or admin.
     */
    private void showDetail(Player viewer, DetailContext ctx, int index) {
        showChestDialog(viewer, ctx.owner(), index, chest -> dialogs.detailDialog(chest, ctx));
    }

    /** Loads the chest by index (under {@code owner}) and shows a dialog built from it on the viewer's thread. */
    private void showChestDialog(Player viewer, UUID owner, int index,
                                 java.util.function.Function<ChestSummary, io.papermc.paper.dialog.Dialog> builder) {
        storageGateway.listChestsAsync(owner).thenAccept(chests ->
                foliaLib.getScheduler().runAtEntity(viewer, task -> {
                    if (!viewer.isOnline()) return;
                    chests.stream().filter(c -> c.index() == index).findFirst().ifPresentOrElse(
                            c -> viewer.showDialog(builder.apply(c)),
                            () -> viewer.sendMessage(lang.get("chest.not-found")));
                })
        ).exceptionally(e -> reportOpenFailure(viewer, e));
    }

    /** Runs the given action on the player's entity thread (helper for command/dialog callbacks). */
    public void runForPlayer(Player player, Runnable action) {
        foliaLib.getScheduler().runAtEntity(player, task -> action.run());
    }

    // ---- database import (/ee import) ----

    /** Shows the DB→DB import dialog (source connection form) to the admin. */
    public void showImportDialog(Player admin) {
        foliaLib.getScheduler().runAtEntity(admin, t -> {
            if (admin.isOnline()) admin.showDialog(dialogs.importDialog());
        });
    }

    /** Carries the DB-executor outcome back to the entity thread: dest not empty, or the row counts. */
    private record ImportOutcome(long existingCount, @Nullable DatabaseImportService.Result result) {}

    /**
     * Validates the submitted source connection form, runs the pre-flight guards, and — if they pass —
     * copies the source database into the active backend off the region thread, reporting the result.
     *
     * <p>Guards: (1) the form must be complete for its chosen type; (2) no player other than the running
     * admin may be online (the copy assumes a quiet server); (3) the source must not be the active
     * destination; (4) the destination must be empty (import only into a fresh DB — checked on the DB
     * executor). All user-facing text goes through {@link LanguageManager}.
     */
    public void performImport(Player admin, SourceSpec spec) {
        String validationKey = validateSpec(spec);
        if (validationKey != null) {
            final String key = validationKey;
            foliaLib.getScheduler().runAtEntity(admin, t -> {
                if (admin.isOnline()) admin.sendMessage(lang.get(key));
            });
            return;
        }

        // Run the in-memory guards on the global/next tick so Bukkit.getOnlinePlayers() is read on a safe
        // thread (the dialog callback may arrive on a region thread under Folia).
        foliaLib.getScheduler().runNextTick(t -> {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.getUniqueId().equals(admin.getUniqueId())) {
                    admin.sendMessage(lang.get("import.players-online"));
                    return;
                }
            }
            if (importService.pointsAtActiveDatabase(spec)) {
                admin.sendMessage(lang.get("import.same-database"));
                return;
            }

            admin.sendMessage(lang.get("import.started", "source", spec.type()));
            db.supply(() -> {
                long existing = importService.destinationChestCount();
                if (existing > 0) {
                    return new ImportOutcome(existing, null);
                }
                try {
                    return new ImportOutcome(0, importService.importFrom(spec));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).whenComplete((outcome, error) -> foliaLib.getScheduler().runAtEntity(admin, tt -> {
                if (!admin.isOnline()) return;
                if (error != null) {
                    logger.error("[Import] Database import failed", error.getCause() != null ? error.getCause() : error);
                    admin.sendMessage(lang.get("import.failed", "error", rootMessage(error)));
                    return;
                }
                if (outcome.result() == null) {
                    admin.sendMessage(lang.get("import.dest-not-empty",
                            "count", Long.toString(outcome.existingCount())));
                    return;
                }
                admin.sendMessage(lang.get("import.done",
                        "players", Integer.toString(outcome.result().players()),
                        "chests", Integer.toString(outcome.result().chests())));
            }));
        });
    }

    /** Returns the language key of the first validation failure for {@code spec}, or null if it is valid. */
    private @Nullable String validateSpec(SourceSpec spec) {
        String family = SourceSpec.family(spec.type());
        switch (family) {
            case "sqlite" -> {
                if (isBlank(spec.sqliteFile())) return "import.missing-sqlite-file";
            }
            case "mysql", "postgres" -> {
                if (isBlank(spec.host()) || isBlank(spec.database()) || isBlank(spec.username())) {
                    return "import.missing-remote-fields";
                }
                if (spec.port() <= 0 || spec.port() > 65535) return "import.invalid-port";
            }
            default -> {
                return "import.invalid-type";
            }
        }
        return null;
    }

    private static boolean isBlank(@Nullable String s) {
        return s == null || s.isBlank();
    }

    private static String rootMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null) cause = cause.getCause();
        String msg = cause.getMessage();
        return msg != null ? msg : cause.getClass().getSimpleName();
    }
}
