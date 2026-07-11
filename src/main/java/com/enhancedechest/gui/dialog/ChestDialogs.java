package com.enhancedechest.gui.dialog;

import com.enhancedechest.config.PluginConfig;
import com.enhancedechest.lang.LanguageManager;
import com.enhancedechest.model.ChestKind;
import com.enhancedechest.model.ChestSummary;
import com.enhancedechest.service.ChestOpener;
import com.enhancedechest.service.PlayerSettingsCache;
import com.enhancedechest.service.StorageGateway;
import com.enhancedechest.util.DurationFormat;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import com.enhancedechest.migration.SourceSpec;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Builds the /ec list management dialogs using Paper's (experimental) Dialog API.
 *
 * <p>All Dialog API usage is isolated here so a Paper breaking change requires edits in one place
 * only (mirrors how {@code ContainerCodec} isolates the Data Component API).
 *
 * <p>Three levels: list (one button per chest), per-chest detail (Open / Rename / Set-as-main),
 * and a dedicated rename dialog (text input + Save / Cancel).
 *
 * <p>Navigation strategy — to avoid the cursor recentre that a server-pushed {@code showDialog}
 * causes, <i>forward</i> navigation (list→detail, detail→rename) uses a client-side
 * {@code show_dialog} action ({@link ClickEvent#showDialog}); the client swaps the dialog in place
 * without reopening the screen. Back/Cancel and post-mutation refreshes re-query the DB and are
 * pushed from the server (these legitimately recentre, since the underlying data may have changed).
 */
@SuppressWarnings("UnstableApiUsage")
public final class ChestDialogs {

    private static final int MAX_NAME_LENGTH = 32;
    private static final int BUTTON_WIDTH = 180;
    private static final int BODY_WIDTH = 200;

    // Icon picker grid: a single scrollable multi-action grid (no paging, so browsing never re-pushes
    // the dialog and never recentres the cursor). Search narrows the list when the catalog is large.
    private static final int ICON_COLUMNS = 4;
    private static final int ICON_BUTTON_WIDTH = 150;
    private static final int ICON_SEARCH_MAX_LENGTH = 48;

    /** Key of the icon picker's search text input, read at click time to filter the catalog. */
    private static final String ICON_SEARCH_INPUT = "icon_search";

    /** Key of the list dialog's edit-mode checkbox, read at click time to route chest buttons. */
    private static final String EDIT_MODE_INPUT = "edit_mode";

    // Import dialog input keys (the DB→DB conversion source-connection form). Host and port share one
    // "host[:port]" field to keep the remote form short. The type is chosen with the cycle button below
    // the inputs, which re-pushes the form so only the chosen type's fields are shown.
    private static final String IMPORT_SQLITE_FILE_INPUT = "import_sqlite_file";
    private static final String IMPORT_HOSTPORT_INPUT = "import_hostport";
    private static final String IMPORT_DATABASE_INPUT = "import_database";
    private static final String IMPORT_USERNAME_INPUT = "import_username";
    private static final String IMPORT_PASSWORD_INPUT = "import_password";

    /** Source backend ids, in the order the source-type selector cycles through them. */
    private static final String[] IMPORT_TYPE_IDS = {"sqlite", "mysql", "mariadb", "postgres"};

    private static final int IMPORT_INPUT_WIDTH = 220;
    /** Full width: buttons stack in a single column (columns = 1), aligned with the inputs above. */
    private static final int IMPORT_BUTTON_WIDTH = IMPORT_INPUT_WIDTH;
    private static final int IMPORT_FIELD_MAX_LENGTH = 256;

    private final ChestOpener opener;
    private final StorageGateway storageGateway;
    private final PlayerSettingsCache settings;
    private final LanguageManager lang;
    private final PluginConfig config;

    public ChestDialogs(ChestOpener opener, StorageGateway storageGateway,
                        PlayerSettingsCache settings, LanguageManager lang, PluginConfig config) {
        this.opener = opener;
        this.storageGateway = storageGateway;
        this.settings = settings;
        this.lang = lang;
        this.config = config;
    }

    /**
     * The owner/permission context a per-chest detail dialog operates in. The same dialog serves the chest
     * owner and an admin viewing someone else's chest ({@code /ee view}); this record carries everything
     * that differs between those cases, so the dialog itself stays a single code path.
     *
     * <p>All storage mutations (rename, icon, sort, set-main) target {@link #owner}, <i>not</i> the
     * clicking viewer — that is how an admin edits another player's chest. {@link #self} decides Open and
     * Back routing (own list vs. the admin view list); {@link #canEdit} gates the appearance edits
     * (rename / icon / sort); {@link #canSetMain} gates "set as main" (owner-only); {@link #canClear} gates
     * the admin Clear button.
     *
     * @param owner       whose chest these operations target (the clicker for self, the target for admin)
     * @param ownerName   display name of {@code owner}, used for admin Back navigation (null for self)
     * @param self        true when the viewer owns these chests
     * @param canEdit     whether the appearance edits (rename / icon / sort) may be shown
     * @param canSetMain  whether "set as main" may be shown (owner-only, gated on the open permission)
     * @param canClear    whether the admin Clear button may be shown (gated on the clear permission)
     * @param sourceBlock owning ender-chest block for the lid animation on Open (self, right-click), or null
     * @param locale      the <i>viewer's</i> client locale (owner or admin, whoever is looking at the
     *                    dialog) — threaded into the icon picker so its search can match localized item
     *                    names; see {@link IconCatalog#search(String, Locale)}
     */
    public record DetailContext(UUID owner, @Nullable String ownerName, boolean self, boolean canEdit,
                                boolean canSetMain, boolean canClear, @Nullable Location sourceBlock,
                                Locale locale) {}

    /**
     * Top-level list: one button per chest, plus an in-dialog "edit mode" checkbox.
     *
     * <p>The edit-mode checkbox decides what clicking a chest does, read fresh at click time:
     * <ul>
     *   <li><b>off</b> (default) — clicking a chest opens its inventory directly;</li>
     *   <li><b>on</b> — clicking a chest opens its management detail dialog (rename, set main, …).</li>
     * </ul>
     * Because the mode lives in a {@linkplain DialogInput#bool boolean input} the client toggles
     * locally, flipping it never reopens the dialog — so the cursor never recentres (a server-pushed
     * rebuild would have). {@code editInitial} only seeds the checkbox's starting state, used to keep
     * edit mode on when returning from a detail dialog's Back.
     *
     * @param canSetMain  whether the viewer may set a chest as their main (gated on the
     *                    open-by-command permission); threaded into each detail dialog
     * @param sourceBlock ender chest block this menu was opened from (for the lid close animation),
     *                    or null when opened by command; threaded into each chest's open/detail
     * @param editInitial starting state of the edit-mode checkbox (false for a fresh open)
     */
    public Dialog listDialog(List<ChestSummary> chests, boolean canSetMain,
                             @Nullable Location sourceBlock, boolean editInitial) {
        // Temporary chests always sort to the top so players notice them (they expire); within each
        // group the natural index order is preserved.
        List<ChestSummary> ordered = new ArrayList<>(chests);
        ordered.sort(Comparator
                .comparingInt((ChestSummary c) -> c.kind() == ChestKind.TEMP ? 0 : 1)
                .thenComparingInt(ChestSummary::index));

        List<ActionButton> buttons = new ArrayList<>(ordered.size());
        for (ChestSummary chest : ordered) {
            int index = chest.index();
            Component label = withIcon(chest, lang.getChestLabel(index, chest.customName(), chest.kind()));
            if (chest.primary()) {
                label = label.append(Component.text(" ")).append(lang.getGui("dialog.main-tag"));
            }
            // Branch on the edit-mode checkbox at click time. Edit off: open the chest directly
            // (opening an inventory dismisses the dialog, so its recentre is moot). Edit on: push the
            // detail dialog from the player's thread, source block preserved for its Open animation.
            buttons.add(ActionButton.create(label, listTooltip(chest), BUTTON_WIDTH,
                    click((view, audience) -> {
                        if (!(audience instanceof Player p)) return;
                        // The checkbox toggles client-side and is only readable here, on a click. This
                        // is the one moment we can persist the player's choice — and only when it
                        // actually differs from the state we seeded the dialog with, to avoid needless
                        // writes when they never touched it.
                        boolean editing = Boolean.TRUE.equals(view.getBoolean(EDIT_MODE_INPUT));
                        if (editing != editInitial) {
                            settings.setEditModeAsync(p.getUniqueId(), editing);
                        }
                        if (editing) {
                            DetailContext ctx = new DetailContext(p.getUniqueId(), null, true, true,
                                    canSetMain, false, sourceBlock, p.locale());
                            opener.runForPlayer(p, () -> {
                                if (p.isOnline()) p.showDialog(detailDialog(chest, ctx));
                            });
                        } else {
                            opener.openChest(p, index, sourceBlock);
                        }
                    })));
        }

        // Dedicated close button (the dialog's exit action). It carries a click action purely to
        // persist the edit-mode checkbox: closing is the common way to leave the list without ever
        // clicking a chest, and the client-side checkbox is only readable on an action click — so
        // without this, toggling edit mode then closing would never save. The exit action still
        // dismisses the dialog afterwards. (Escape can't be captured, so it doesn't persist.)
        ActionButton close = ActionButton.create(lang.getGui("dialog.close"), null, BUTTON_WIDTH,
                click((view, audience) -> {
                    if (!(audience instanceof Player p)) return;
                    boolean editing = Boolean.TRUE.equals(view.getBoolean(EDIT_MODE_INPUT));
                    if (editing != editInitial) {
                        settings.setEditModeAsync(p.getUniqueId(), editing);
                    }
                }));

        // In-dialog toggle: a checkbox the client flips locally, so switching modes never reopens the
        // dialog (and so never recentres the cursor). Rendered in the body, above the chest buttons.
        DialogInput editMode = DialogInput.bool(EDIT_MODE_INPUT, lang.getGui("dialog.edit-mode"))
                .initial(editInitial)
                .build();

        return Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(lang.getGui("dialog.list-title"))
                        .body(List.of(DialogBody.plainMessage(lang.getGui("dialog.list-body"), BODY_WIDTH)))
                        .inputs(List.of(editMode))
                        .build())
                .type(DialogType.multiAction(buttons, close, columnsFor(ordered.size()))));
    }

    /**
     * Admin "view another player's chests" list: one button per chest that opens it <i>for the admin</i>
     * via the shared session ({@link ChestOpener#adminOpen}). Unlike {@link #listDialog} there is
     * no edit-mode checkbox, rename, or set-main — an admin only views/edits chest <i>contents</i>, never
     * the owner's chest metadata. Reachable from {@code /ee view <player>} (2+ chests) and
     * {@code /ee view <player> list}.
     *
     * @param targetName display name of the owner whose chests these are (shown in the title)
     * @param target     UUID of the owner; passed to {@code adminOpen} when a button is clicked
     */
    public Dialog adminViewListDialog(String targetName, UUID target, List<ChestSummary> chests) {
        // Same ordering as the player list: temp (expiring) chests first, then natural index order.
        List<ChestSummary> ordered = new ArrayList<>(chests);
        ordered.sort(Comparator
                .comparingInt((ChestSummary c) -> c.kind() == ChestKind.TEMP ? 0 : 1)
                .thenComparingInt(ChestSummary::index));

        List<ActionButton> buttons = new ArrayList<>(ordered.size());
        for (ChestSummary chest : ordered) {
            int index = chest.index();
            Component label = withIcon(chest, lang.getChestLabel(index, chest.customName(), chest.kind()));
            if (chest.primary()) {
                label = label.append(Component.text(" ")).append(lang.getGui("dialog.main-tag"));
            }
            // Clicking a chest opens the admin per-chest detail dialog (Open / Clear chest / Back),
            // rather than opening the inventory straight away — that detail dialog is where the
            // admin-only Clear button lives.
            buttons.add(ActionButton.create(label, listTooltip(chest), BUTTON_WIDTH,
                    click((view, audience) -> {
                        if (audience instanceof Player p) opener.openAdminDetail(p, targetName, target, index);
                    })));
        }

        // Exit button is a plain close (no state to persist, unlike the player list's edit-mode checkbox).
        ActionButton close = ActionButton.create(lang.getGui("dialog.close"), null, BUTTON_WIDTH,
                click((view, audience) -> { /* dismiss only */ }));

        return Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(lang.getGui("dialog.admin-list-title", "player", targetName))
                        .body(List.of(DialogBody.plainMessage(lang.getGui("dialog.admin-list-body"), BODY_WIDTH)))
                        .build())
                .type(DialogType.multiAction(buttons, close, columnsFor(ordered.size()))));
    }

    /**
     * Confirmation gate for the admin Clear chest action: a red warning body, a Confirm button (also
     * tagged {@code (Admin)}) that performs the wipe, and Cancel that returns to the detail dialog. The
     * wipe is permanent, so it is never done on a single click.
     */
    public Dialog adminClearConfirmDialog(String targetName, UUID target, ChestSummary chest) {
        int index = chest.index();

        ActionButton confirm = ActionButton.create(lang.getGui("dialog.admin-clear-confirm"), null, BUTTON_WIDTH,
                click((view, audience) -> {
                    if (audience instanceof Player p) opener.adminClear(p, targetName, target, index);
                }));
        ActionButton cancel = ActionButton.create(lang.getGui("dialog.cancel"), null, BUTTON_WIDTH,
                click((view, audience) -> {
                    if (audience instanceof Player p) opener.openAdminDetail(p, targetName, target, index);
                }));

        // A dedicated question title (not the chest label) so the dialog reads as a confirmation; the
        // chest body names which chest (plain label, no icon sprite), and Cancel is the safe default.
        Component title = lang.getGui("dialog.admin-clear-confirm-title");
        return Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(title)
                        .body(List.of(
                                DialogBody.plainMessage(
                                        lang.getChestLabel(index, chest.customName(), chest.kind()), BODY_WIDTH),
                                DialogBody.plainMessage(lang.getGui("dialog.admin-clear-confirm-body"), BODY_WIDTH)))
                        .build())
                .type(DialogType.multiAction(List.of(confirm, cancel), null, 1)));
    }

    /**
     * Chest-list grid width: keep a single familiar column for short lists, but fan wider lists out
     * into multiple columns so they use the horizontal space instead of forcing a tall scrollbar.
     */
    private static int columnsFor(int count) {
        if (count <= 7) return 1;
        if (count <= 14) return 2;
        return 3;
    }

    /**
     * Per-chest detail menu, shared by the owner ({@code /eclist} edit mode) and an admin ({@code /ee
     * view}). The button set is decided by {@code ctx}: Open is always present; appearance edits (Rename /
     * Choose icon / Sort) appear when {@link DetailContext#canEdit} is set <i>and</i> the matching feature
     * toggle is on; "Set as main" appears for the owner only; the admin Clear button appears when
     * {@link DetailContext#canClear} is set. All mutations target {@code ctx.owner()}, so an admin's clicks
     * edit the <i>target's</i> chest, never their own.
     *
     * <p>Temp chests are transient overflow holders — no appearance edits or main flag, only Open (and
     * Clear / Back as permitted).
     */
    public Dialog detailDialog(ChestSummary chest, DetailContext ctx) {
        int index = chest.index();
        boolean temp = chest.kind() == ChestKind.TEMP;
        UUID owner = ctx.owner();
        List<ActionButton> buttons = new ArrayList<>(6);

        // Open first — the primary action, and the most common reason to be here. The owner opens their own
        // chest (with the lid animation); an admin joins the target's shared session via adminOpen.
        buttons.add(ActionButton.create(lang.getGui("dialog.open"), lang.getGui("dialog.open-desc"), BUTTON_WIDTH,
                click((view, audience) -> {
                    if (!(audience instanceof Player p)) return;
                    if (ctx.self()) opener.openChest(p, index, ctx.sourceBlock());
                    else opener.adminOpen(p, owner, index);
                })));

        // Temp chests are transient overflow holders: no main flag, no customisation. Only Open + (Clear) + Back.
        if (!temp) {
            // Set as main / Unset main — owner-only (a main only matters for the owner's /ec). Highest-impact
            // toggle, so it sits right after Open. Mutates data, so it re-queries and is re-pushed.
            if (ctx.canSetMain() && !chest.primary()) {
                buttons.add(ActionButton.create(lang.getGui("dialog.set-main"), lang.getGui("dialog.set-main-desc"),
                        BUTTON_WIDTH, click((view, audience) -> {
                            if (!(audience instanceof Player p)) return;
                            storageGateway.setPrimaryAsync(owner, index)
                                    .thenRun(() -> opener.openDetailDialog(p, ctx, index));
                        })));
            } else if (ctx.canSetMain() && chest.primary()) {
                buttons.add(ActionButton.create(lang.getGui("dialog.unset-main"), lang.getGui("dialog.unset-main-desc"),
                        BUTTON_WIDTH, click((view, audience) -> {
                            if (!(audience instanceof Player p)) return;
                            storageGateway.clearPrimaryAsync(owner)
                                    .thenRun(() -> opener.openDetailDialog(p, ctx, index));
                        })));
            }

            // Appearance edits, each behind its global feature toggle (config) and the viewer's edit right.
            // Rename / Choose icon forward in-place (client-side show_dialog) so they never recentre the
            // cursor; Sort is a server action (it re-reads/re-writes the chest, then re-pushes the menu).
            if (ctx.canEdit() && config.isRenameEnabled()) {
                buttons.add(ActionButton.create(lang.getGui("dialog.rename"), lang.getGui("dialog.rename-desc"),
                        BUTTON_WIDTH, DialogAction.staticAction(ClickEvent.showDialog(renameDialog(chest, ctx)))));
            }
            if (ctx.canEdit() && config.isIconEnabled()) {
                buttons.add(ActionButton.create(lang.getGui("dialog.choose-icon"), lang.getGui("dialog.choose-icon-desc"),
                        BUTTON_WIDTH, DialogAction.staticAction(ClickEvent.showDialog(iconPickerDialog(chest, ctx, "")))));
            }
            if (ctx.canEdit() && config.isSortEnabled()) {
                buttons.add(ActionButton.create(lang.getGui("dialog.sort"), lang.getGui("dialog.sort-desc"),
                        BUTTON_WIDTH, click((view, audience) -> {
                            if (audience instanceof Player p) opener.sortChest(p, ctx, index);
                        })));
            }
        }

        // Admin-only Clear: a red (Admin)-tagged button routing through a confirmation before wiping. Shown
        // for any chest kind (overflow temp chests can be cleared too), gated on the clear permission.
        if (ctx.canClear()) {
            buttons.add(ActionButton.create(lang.getGui("dialog.admin-clear"), lang.getGui("dialog.admin-clear-desc"),
                    BUTTON_WIDTH, click((view, audience) -> {
                        if (audience instanceof Player p) {
                            opener.openAdminClearConfirm(p, ctx.ownerName(), owner, index);
                        }
                    })));
        }

        // Back: the owner returns to their list in edit mode; an admin returns to the admin view list.
        buttons.add(ActionButton.create(lang.getGui("dialog.back"), null, BUTTON_WIDTH,
                click((view, audience) -> {
                    if (!(audience instanceof Player p)) return;
                    if (ctx.self()) opener.openListDialog(p, true, ctx.sourceBlock());
                    else opener.openAdminViewList(p, ctx.ownerName(), owner);
                })));

        Component title = withIcon(chest, lang.getChestLabel(index, chest.customName(), chest.kind()));
        return Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(title)
                        .body(List.of(detailBody(chest)))
                        .build())
                .type(DialogType.multiAction(buttons, null, 1)));
    }

    /**
     * Icon picker: a single, searchable, <b>scrollable</b> grid of every matching material rendered with
     * its in-game sprite (via {@link IconCatalog}). The grid is intentionally <i>not</i> paged — the
     * client scrolls the button list, so browsing icons never re-pushes the dialog from the server and
     * therefore never recentres the cursor (paging buttons would, since their target page can only be
     * server-pushed). Search narrows the list when the full catalog is unwieldy.
     *
     * <p>Layout: the control row (Search / Default) is the first buttons, the icons follow, and Back is
     * the dialog's exit action (kept reachable while scrolling). Search reads the text input and
     * re-shows the filtered list. Picking an icon, or Default, writes the change and returns to detail.
     *
     * @param filter case-insensitive name filter ("" = whole catalog)
     */
    public Dialog iconPickerDialog(ChestSummary chest, DetailContext ctx, String filter) {
        int index = chest.index();
        UUID owner = ctx.owner();
        List<IconCatalog.Entry> results = IconCatalog.search(filter, ctx.locale());

        List<ActionButton> buttons = new ArrayList<>(results.size() + 2);

        // Search: re-show the list filtered by the freshly typed text (an explicit, server-pushed action).
        buttons.add(ActionButton.create(lang.getGui("dialog.icon-search"), null, ICON_BUTTON_WIDTH,
                click((view, audience) -> {
                    if (!(audience instanceof Player p)) return;
                    String typed = view.getText(ICON_SEARCH_INPUT);
                    String query = typed == null ? "" : typed.trim();
                    opener.runForPlayer(p, () -> {
                        if (p.isOnline()) p.showDialog(iconPickerDialog(chest, ctx, query));
                    });
                })));
        // Default: clear the icon back to the ender-chest default.
        buttons.add(ActionButton.create(lang.getGui("dialog.icon-default"), lang.getGui("dialog.icon-default-desc"),
                ICON_BUTTON_WIDTH, click((view, audience) -> {
                    if (!(audience instanceof Player p)) return;
                    storageGateway.setIconAsync(owner, index, null)
                            .thenRun(() -> opener.openDetailDialog(p, ctx, index));
                })));

        // One button per matching icon; the client scrolls the grid.
        for (IconCatalog.Entry entry : results) {
            Component label = entry.sprite().append(Component.text(" ")).append(entry.name());
            buttons.add(ActionButton.create(label, null, ICON_BUTTON_WIDTH,
                    click((view, audience) -> {
                        if (!(audience instanceof Player p)) return;
                        storageGateway.setIconAsync(owner, index, entry.key())
                                .thenRun(() -> opener.openDetailDialog(p, ctx, index));
                    })));
        }

        DialogInput search = DialogInput.text(ICON_SEARCH_INPUT, lang.getGui("dialog.icon-search-label"))
                .initial(filter)
                .maxLength(ICON_SEARCH_MAX_LENGTH)
                .build();

        Component body = lang.getGui("dialog.icon-body", "count", Integer.toString(results.size()));

        ActionButton back = ActionButton.create(lang.getGui("dialog.back"), null, ICON_BUTTON_WIDTH,
                click((view, audience) -> {
                    if (audience instanceof Player p) opener.openDetailDialog(p, ctx, index);
                }));

        Component title = lang.getGui("dialog.icon-title");
        return Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(title)
                        .body(List.of(DialogBody.plainMessage(body, BODY_WIDTH)))
                        .inputs(List.of(search))
                        .build())
                .type(DialogType.multiAction(buttons, back, ICON_COLUMNS)));
    }

    /** Prepends the chest's chosen icon sprite (if any) to a label/title, separated by a space. */
    private Component withIcon(ChestSummary chest, Component label) {
        Component icon = IconCatalog.sprite(chest.icon());
        if (icon == null) {
            return label;
        }
        return icon.append(Component.text(" ")).append(label);
    }

    /** Dedicated rename dialog: a single text input plus Save / Cancel. Targets {@code ctx.owner()}. */
    public Dialog renameDialog(ChestSummary chest, DetailContext ctx) {
        int index = chest.index();
        UUID owner = ctx.owner();
        String current = chest.customName() != null ? chest.customName() : "";

        DialogInput nameInput = DialogInput.text("name", lang.getGui("dialog.name-label"))
                .initial(current)
                .maxLength(MAX_NAME_LENGTH)
                .build();

        ActionButton save = ActionButton.create(lang.getGui("dialog.save-name"), null, BUTTON_WIDTH,
                click((view, audience) -> {
                    if (!(audience instanceof Player p)) return;
                    String typed = view.getText("name");
                    String name = (typed == null || typed.isBlank()) ? null : typed.trim();
                    // Reject names containing a configured banned word (case-insensitive substring),
                    // matched against the formatting-stripped text so colour codes can't smuggle a banned
                    // word through. The rejected name is never stored; re-show the dialog so they can retry.
                    if (name != null && config.isNameBlocked(lang.plainChestName(name))) {
                        p.sendMessage(lang.get("chest.rename-blocked"));
                        opener.runForPlayer(p, () -> {
                            if (p.isOnline()) p.showDialog(renameDialog(chest, ctx));
                        });
                        return;
                    }
                    storageGateway.renameAsync(owner, index, name)
                            .thenRun(() -> opener.openDetailDialog(p, ctx, index));
                }));

        ActionButton cancel = ActionButton.create(lang.getGui("dialog.cancel"), null, BUTTON_WIDTH,
                click((view, audience) -> {
                    if (audience instanceof Player p) opener.openDetailDialog(p, ctx, index);
                }));

        Component title = lang.getChestLabel(index, chest.customName(), chest.kind());
        return Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(title)
                        .body(List.of(DialogBody.plainMessage(lang.getGui("dialog.rename-body"), BODY_WIDTH)))
                        .inputs(List.of(nameInput))
                        .build())
                .type(DialogType.multiAction(List.of(save, cancel), null, 1)));
    }

    /**
     * DB→DB import dialog ({@code /ee import}), no-arg entry point — opens the form with SQLite selected.
     */
    public Dialog importDialog() {
        return importDialog(defaultImportSpec());
    }

    /**
     * DB→DB import dialog for a given (in-progress) {@link SourceSpec}. The Dialog API is static, so the
     * form <i>adapts to the type</i> by being rebuilt: only the chosen type's fields are shown. The type
     * is a simple two-way toggle — <b>SQLite</b> (a file) or <b>Server</b> (a MySQL/MariaDB/PostgreSQL
     * host) — because the three server engines share the exact same connection form; the concrete engine
     * is resolved from the port on submit (5432 → PostgreSQL, otherwise MySQL/MariaDB). The type button is
     * shown <i>above</i> Start import and Cancel; clicking it reads whatever has been typed, flips the
     * type, and re-pushes this dialog, so typed values survive the switch.
     *
     * <p>The password is a plain text input, so it is visible on screen while typing — documented in the
     * database docs, so the dialog itself does not repeat the warning.
     */
    public Dialog importDialog(SourceSpec spec) {
        String typeId = normalizeTypeId(spec.type());
        boolean server = !SourceSpec.family(typeId).equals("sqlite");

        // Only the selected type's fields are shown, which keeps the form short.
        List<DialogInput> inputs = new ArrayList<>();
        if (!server) {
            inputs.add(importText(IMPORT_SQLITE_FILE_INPUT, "dialog.import-sqlite-file",
                    blankOr(spec.sqliteFile(), "enderchests.db")));
        } else {
            inputs.add(importText(IMPORT_HOSTPORT_INPUT, "dialog.import-host", hostPortInitial(spec)));
            inputs.add(importText(IMPORT_DATABASE_INPUT, "dialog.import-database",
                    blankOr(spec.database(), "enhancedechest")));
            inputs.add(importText(IMPORT_USERNAME_INPUT, "dialog.import-username",
                    blankOr(spec.username(), "root")));
            inputs.add(importText(IMPORT_PASSWORD_INPUT, "dialog.import-password", orEmpty(spec.password())));
        }

        // Buttons stack in a single full-width column (columns = 1): the type toggle on top, then Start
        // import and Cancel. The type button's own label already flips on click (tooltip says so), so no
        // separate "Switch" button is needed — this keeps the dialog narrow and the buttons aligned.
        ActionButton typeButton = toggleTypeButton(
                lang.getGui(server ? "dialog.import-type-server" : "dialog.import-type-sqlite"), server, spec);
        ActionButton start = ActionButton.create(lang.getGui("dialog.import-run"), lang.getGui("dialog.import-run-desc"),
                IMPORT_BUTTON_WIDTH, click((view, audience) -> {
                    if (!(audience instanceof Player p)) return;
                    opener.performImport(p, readForm(view, server, spec));
                }));
        ActionButton cancel = ActionButton.create(lang.getGui("dialog.cancel"), null, IMPORT_BUTTON_WIDTH,
                click((view, audience) -> { /* dismiss only */ }));

        return Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(lang.getGui("dialog.import-title"))
                        .body(List.of(DialogBody.plainMessage(lang.getGui("dialog.import-body"), IMPORT_INPUT_WIDTH)))
                        .inputs(inputs)
                        .build())
                .type(DialogType.multiAction(List.of(typeButton, start, cancel), null, 1)));
    }

    /** Default import spec: SQLite selected, with the same starter values as {@code config.yml}. */
    private static SourceSpec defaultImportSpec() {
        return new SourceSpec("sqlite", "enderchests.db", "localhost", 0, "enhancedechest", "root", "");
    }

    /**
     * A source-type toggle button (the top button in the column): flips between SQLite and Server,
     * preserving whatever has been typed, and re-pushes the form so only that type's fields show. Its
     * label shows the current type and clicking it toggles (the tooltip says so).
     * Entering server mode defaults to MySQL; the concrete engine is resolved from the port on submit
     * (see {@link #readForm}). {@code label} is the button's text; the explanation is in its tooltip.
     */
    private ActionButton toggleTypeButton(Component label, boolean server, SourceSpec spec) {
        Component tooltip = lang.getGui("dialog.import-type-desc");
        return ActionButton.create(label, tooltip, IMPORT_BUTTON_WIDTH,
                click((view, audience) -> {
                    if (!(audience instanceof Player p)) return;
                    // Preserve what has been typed for the current type, then flip and re-show.
                    SourceSpec current = readForm(view, server, spec);
                    String nextType = server ? "sqlite" : "mysql";
                    SourceSpec switched = new SourceSpec(nextType, current.sqliteFile(), current.host(),
                            current.port(), current.database(), current.username(), current.password());
                    opener.runForPlayer(p, () -> {
                        if (p.isOnline()) p.showDialog(importDialog(switched));
                    });
                }));
    }

    /** Builds one import-form text input (label from gui.yml) with the given initial value. */
    private DialogInput importText(String key, String labelKey, String initial) {
        return DialogInput.text(key, lang.getGui(labelKey))
                .width(IMPORT_INPUT_WIDTH)
                .initial(initial)
                .maxLength(IMPORT_FIELD_MAX_LENGTH)
                .build();
    }

    /**
     * Reads the currently shown fields into a {@link SourceSpec}, falling back to {@code prev} for any
     * field not on screen (so flipping the type never loses the other type's values). In server mode the
     * single {@code host[:port]} field is split and the concrete engine is picked from the port: 5432 →
     * PostgreSQL, otherwise MySQL/MariaDB (an omitted port defaults to 3306; a non-numeric one becomes -1,
     * rejected on submit).
     */
    private SourceSpec readForm(io.papermc.paper.dialog.DialogResponseView view, boolean server, SourceSpec prev) {
        if (!server) {
            String file = orDefault(view.getText(IMPORT_SQLITE_FILE_INPUT), prev.sqliteFile()).trim();
            return new SourceSpec("sqlite", file, prev.host(), prev.port(), prev.database(),
                    prev.username(), prev.password());
        }
        String hostport = orDefault(view.getText(IMPORT_HOSTPORT_INPUT), hostPortInitial(prev)).trim();
        String host;
        int port;
        int sep = hostport.lastIndexOf(':');
        if (sep >= 0) {
            host = hostport.substring(0, sep).trim();
            String portStr = hostport.substring(sep + 1).trim();
            port = portStr.isEmpty() ? 0 : parsePort(portStr);
        } else {
            host = hostport;
            port = 0;
        }
        // The three server engines share this form; pick the concrete driver from the port. Only 5432 maps
        // to PostgreSQL; everything else (incl. an omitted port → 3306) uses the MySQL/MariaDB driver.
        String type = port == 5432 ? "postgres" : "mysql";
        if (port == 0) port = 3306;
        String database = orDefault(view.getText(IMPORT_DATABASE_INPUT), prev.database()).trim();
        String username = orDefault(view.getText(IMPORT_USERNAME_INPUT), prev.username()).trim();
        String password = orDefault(view.getText(IMPORT_PASSWORD_INPUT), prev.password());
        return new SourceSpec(type, prev.sqliteFile(), host, port, database, username, password);
    }

    /** Renders {@code host}/{@code port} back into the combined "host[:port]" field's initial value. */
    private static String hostPortInitial(SourceSpec spec) {
        String host = orEmpty(spec.host());
        return spec.port() > 0 ? host + ":" + spec.port() : host;
    }

    /** Normalises the selected type to one of {@link #IMPORT_TYPE_IDS}; unknown/blank falls back to sqlite. */
    private static String normalizeTypeId(@Nullable String type) {
        String t = type == null ? "" : type.trim().toLowerCase(java.util.Locale.ROOT);
        if (t.equals("postgresql")) t = "postgres";
        for (String id : IMPORT_TYPE_IDS) {
            if (id.equals(t)) return id;
        }
        return "sqlite";
    }

    private static int parsePort(@Nullable String raw) {
        if (raw == null || raw.isBlank()) return 0;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return -1; // signals an invalid port to performImport's validation
        }
    }

    /** Non-null with a fallback (used for inputs absent from the current type's form when switching). */
    private static String orDefault(@Nullable String value, @Nullable String fallback) {
        return value != null ? value : orEmpty(fallback);
    }

    /** Returns {@code value}, or {@code fallback} when it is null/blank (for a field's initial value). */
    private static String blankOr(@Nullable String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String orEmpty(@Nullable String s) {
        return s == null ? "" : s;
    }

    /**
     * Detail-dialog body: a centred slot count, plus the static "expires in" snapshot for expiring
     * chests. The chest's chosen icon is shown in the title (see {@link #withIcon}), so the body stays a
     * clean centred message rather than an item body whose icon floats off to one side.
     */
    private DialogBody detailBody(ChestSummary chest) {
        Component info = lang.getGui("dialog.detail-body", "size", Integer.toString(chest.size()));
        Component expiry = expiryTooltip(chest);
        if (expiry != null) {
            info = info.appendNewline().append(expiry);
        }
        return DialogBody.plainMessage(info, BODY_WIDTH);
    }

    /** List-button tooltip: slot count, plus a static "expires in" snapshot for expiring chests. */
    private Component listTooltip(ChestSummary chest) {
        Component tip = lang.getGui("dialog.slots", "size", Integer.toString(chest.size()));
        Component expiry = expiryTooltip(chest);
        return expiry == null ? tip : tip.appendNewline().append(expiry);
    }

    /**
     * Static "expires in &lt;time&gt;" snapshot recomputed each time the dialog is built (a live
     * ticking countdown is impossible with the static Dialog API). Null for chests that never expire.
     */
    private @Nullable Component expiryTooltip(ChestSummary chest) {
        if (chest.expiresAt() == null) {
            return null;
        }
        String remaining = DurationFormat.formatRemaining(chest.expiresAt() - System.currentTimeMillis());
        return lang.getGui("dialog.expires-in", "time", remaining);
    }

    private static DialogAction click(BiConsumer<io.papermc.paper.dialog.DialogResponseView,
            net.kyori.adventure.audience.Audience> body) {
        return DialogAction.customClick(
                body::accept,
                ClickCallback.Options.builder().build());
    }
}
