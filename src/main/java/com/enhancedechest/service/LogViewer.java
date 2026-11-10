package com.enhancedechest.service;

import com.enhancedechest.gui.LogMenu;
import com.enhancedechest.gui.LogSnapshotHolder;
import com.enhancedechest.lang.LanguageManager;
import com.enhancedechest.log.ChestLogStore;
import com.enhancedechest.log.LogEntry;
import com.enhancedechest.scheduler.Scheduler;
import com.enhancedechest.serialization.ContainerCodec;
import com.enhancedechest.telemetry.Telemetry;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletionException;

/**
 * Opens the {@code /ee log} viewer, its item-search dialog, and its snapshot previews. Shared by the
 * command (initial open) and the menu listener (paging, search, pane clicks), so every path uses the
 * same async → build → open flow.
 *
 * <p>All database work — counting rows, reading a page (optionally filtered by a searched item), and
 * decoding a snapshot blob — runs on the shared DB executor (decode is safe off the tick thread: the
 * stored bytes are immutable and the decoded stacks are handed to the entity thread through the future's
 * happens-before edge). Only the cheap inventory build and {@code openInventory} touch the entity thread.
 *
 * <p>The search dialog is the one place outside {@code gui.dialog.ChestDialogs} that touches the Paper
 * Dialog API — kept here so the whole log feature stays in one place; it is a single small form.
 */
@SuppressWarnings("UnstableApiUsage")
public final class LogViewer {

    /** Short timestamp for the snapshot window title, so a long title never overflows the inventory bar. */
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault());

    /** Search dialog's text input key, read back when the Search button is clicked. */
    private static final String SEARCH_INPUT = "log_search";
    private static final int SEARCH_MAX_LENGTH = 48;

    /** What a snapshot window shows: contents, the title key, and the time to put in the title. */
    private record Shown(org.bukkit.inventory.ItemStack[] contents, String titleKey, long time) {}

    private record PageData(List<LogEntry> entries, int page, int pageCount, int total) {}

    private final ChestLogStore store;
    private final ContainerCodec codec;
    private final DbExecutor db;
    private final Scheduler scheduler;
    private final LanguageManager lang;
    private final LogMenu menu;
    private final Logger logger;
    private final Telemetry telemetry;

    /** Folder of the scheduled database backups, searched by the "before" view; {@code null} = don't. */
    @Nullable
    private final java.nio.file.Path backupDir;

    public LogViewer(ChestLogStore store, ContainerCodec codec, DbExecutor db, Scheduler scheduler,
                     LanguageManager lang, Logger logger, Telemetry telemetry) {
        this(store, codec, db, scheduler, lang, logger, telemetry, null);
    }

    public LogViewer(ChestLogStore store, ContainerCodec codec, DbExecutor db, Scheduler scheduler,
                     LanguageManager lang, Logger logger, Telemetry telemetry, @Nullable java.nio.file.Path backupDir) {
        this.backupDir = backupDir;
        this.store = store;
        this.codec = codec;
        this.db = db;
        this.scheduler = scheduler;
        this.lang = lang;
        this.menu = new LogMenu(lang);
        this.logger = logger;
        this.telemetry = telemetry;
    }

    /**
     * Opens (or re-opens) the log viewer for {@code owner} to {@code admin}. {@code query} filters to
     * visits whose change list mentions that item (null = the full log). Clamps {@code page} into range;
     * only an unfiltered, genuinely empty log reports "nothing logged" instead of opening a menu — an
     * empty <i>search</i> still opens so the admin can clear it or search again.
     */
    public void openLog(Player admin, UUID owner, String ownerName, int page, @Nullable String query) {
        db.supply(() -> {
            try {
                int total = store.countForOwner(owner, query);
                int pageCount = Math.max(1, (total + LogMenu.PAGE_SIZE - 1) / LogMenu.PAGE_SIZE);
                int clamped = Math.max(0, Math.min(page, pageCount - 1));
                List<LogEntry> entries = total == 0
                        ? List.of()
                        : store.page(owner, query, clamped * LogMenu.PAGE_SIZE, LogMenu.PAGE_SIZE);
                return new PageData(entries, clamped, pageCount, total);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }).whenComplete((data, err) -> {
            if (err != null) {
                reportFailure(admin, "admin.log-failed", err, "log.read");
                return;
            }
            scheduler.runAtEntity(admin, t -> {
                if (!admin.isOnline()) return;
                if (query == null && data.total() == 0) {
                    admin.sendMessage(lang.get("admin.log-empty", "player", ownerName));
                    return;
                }
                Inventory inv = menu.build(admin.locale(), owner, ownerName,
                        data.entries(), data.page(), data.pageCount(), query);
                admin.openInventory(inv);
            });
        });
    }

    /**
     * Opens the search dialog: a text input for an item plus Search and Back buttons. Search re-opens the
     * log filtered to that item; Back re-opens it with whatever filter was already active. Built here (not
     * in {@code ChestDialogs}) to keep the log feature self-contained.
     */
    public void openSearchDialog(Player admin, UUID owner, String ownerName, @Nullable String currentQuery) {
        Locale locale = admin.locale();
        DialogInput input = DialogInput.text(SEARCH_INPUT, lang.getGui(locale, "log.search-label"))
                .width(200).initial(currentQuery == null ? "" : currentQuery)
                .maxLength(SEARCH_MAX_LENGTH).build();
        ActionButton run = ActionButton.create(lang.getGui(locale, "log.search-run"), null, 100,
                DialogAction.customClick((view, audience) -> {
                    if (!(audience instanceof Player p)) return;
                    String typed = view.getText(SEARCH_INPUT);
                    typed = typed == null ? "" : typed.trim();
                    openLog(p, owner, ownerName, 0, typed.isEmpty() ? null : typed);
                }, ClickCallback.Options.builder().build()));
        ActionButton back = ActionButton.create(lang.getGui(locale, "log.search-back"), null, 100,
                DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player p) openLog(p, owner, ownerName, 0, currentQuery);
                }, ClickCallback.Options.builder().build()));
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(lang.getGui(locale, "log.search-title"))
                        .body(List.of(DialogBody.plainMessage(lang.getGui(locale, "log.search-body"), 220)))
                        .inputs(List.of(input))
                        .build())
                .type(DialogType.multiAction(List.of(run, back), null, 2)));
        scheduler.runAtEntity(admin, t -> {
            if (admin.isOnline()) admin.showDialog(dialog);
        });
    }

    /**
     * Opens a read-only-safe sandbox showing exactly what the chest held when {@code entry} was closed.
     * The inventory uses {@link LogSnapshotHolder}, so it never funnels through the session manager and is
     * discarded on close — the real chest is never affected. {@code page} and {@code query} are remembered
     * so closing the preview returns to the same (possibly filtered) log page.
     */
    public void openSnapshot(Player admin, UUID owner, String ownerName, LogEntry entry, int page,
                             @Nullable String query) {
        openSnapshot(admin, owner, ownerName, entry, page, query, false);
    }

    /**
     * {@code before} = show the chest as it was when this visit started (the previous logged visit's
     * snapshot) instead of as it was left.
     */
    public void openSnapshot(Player admin, UUID owner, String ownerName, LogEntry entry, int page,
                             @Nullable String query, boolean before) {
        db.supply(() -> {
            try {
                if (!before) {
                    ChestLogStore.SnapshotBlob blob = store.loadSnapshot(entry.id());
                    if (blob == null) return null;   // pruned between listing and click
                    return new Shown(codec.decode(blob.data(), blob.size()), "log.snapshot-title", entry.closedAt());
                }
                // Before this visit: the newest of (the previous logged visit of this chest, the newest
                // database backup taken before the visit opened).
                ChestLogStore.PreviousSnapshot prev = store.loadPreviousSnapshot(owner, entry.index(), entry.id());
                com.enhancedechest.log.BackupLookup.Found backup = null;
                if (backupDir != null) {
                    try {
                        backup = com.enhancedechest.log.BackupLookup.find(backupDir, owner, entry.index(), entry.openedAt());
                    } catch (Exception e) {
                        logger.warn("Could not read database backups for the chest-log before view", e);
                    }
                }
                if (backup != null && (prev == null || backup.takenAt() > prev.closedAt())) {
                    org.bukkit.inventory.ItemStack[] items = backup.data().length < 2
                            ? emptyChest(backup.size())
                            : codec.decode(backup.data(), backup.size());
                    return new Shown(items, "log.snapshot-title-backup", backup.takenAt());
                }
                if (prev == null) return null;
                return new Shown(codec.decode(prev.data(), prev.size()), "log.snapshot-title-before", prev.closedAt());
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }).whenComplete((shown, err) -> {
            scheduler.runAtEntity(admin, t -> {
                if (!admin.isOnline()) return;
                if (err != null || shown == null) {
                    if (err != null) {
                        logger.warn("Could not open chest-log snapshot {} for {}", entry.id(), owner, err);
                        telemetry.error(err, "log.snapshot-decode");
                    }
                    admin.sendMessage(lang.get(before && err == null
                            ? "admin.log-snapshot-no-before" : "admin.log-snapshot-failed"));
                    return;
                }
                org.bukkit.inventory.ItemStack[] contents = shown.contents();
                Component title = lang.getGui(admin.locale(), shown.titleKey(),
                        "index", Integer.toString(entry.index()),
                        "time", TIME.format(Instant.ofEpochMilli(shown.time())));
                Inventory inv = Bukkit.createInventory(
                        new LogSnapshotHolder(owner, ownerName, entry.index(), page, query),
                        contents.length, title);
                inv.setContents(contents);
                admin.openInventory(inv);
            });
        });
    }

    private static org.bukkit.inventory.ItemStack[] emptyChest(int size) {
        org.bukkit.inventory.ItemStack[] out = new org.bukkit.inventory.ItemStack[Math.max(9, size)];
        java.util.Arrays.fill(out, org.bukkit.inventory.ItemStack.empty());
        return out;
    }

    private void reportFailure(Player admin, String messageKey, Throwable err, String label) {
        logger.warn("Chest-log read failed", err);
        telemetry.error(err, label);
        scheduler.runAtEntity(admin, t -> {
            if (admin.isOnline()) admin.sendMessage(lang.get(messageKey));
        });
    }
}
