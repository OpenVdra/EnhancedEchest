package com.enhancedechest.service;

import com.enhancedechest.gui.LogMenu;
import com.enhancedechest.gui.LogSnapshotHolder;
import com.enhancedechest.lang.LanguageManager;
import com.enhancedechest.log.ChestLogStore;
import com.enhancedechest.log.LogAction;
import com.enhancedechest.log.LogEntry;
import com.enhancedechest.scheduler.Scheduler;
import com.enhancedechest.serialization.ContainerCodec;
import com.enhancedechest.telemetry.Telemetry;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.slf4j.Logger;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;

/**
 * Opens the {@code /ee log} viewer and its snapshot previews. Shared by the command (initial open) and
 * the menu listener (page navigation, pane clicks), so both use the exact same async → build → open path.
 *
 * <p>All database work — counting rows, reading a page, and decoding a snapshot blob — runs on the shared
 * DB executor (decode is safe off the tick thread: the stored bytes are immutable and the decoded stacks
 * are handed to the entity thread through the future's happens-before edge). Only the cheap inventory
 * build and {@code openInventory} touch the entity thread.
 */
public final class LogViewer {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private record PageData(List<LogEntry> entries, int page, int pageCount, int total) {}

    private final ChestLogStore store;
    private final ContainerCodec codec;
    private final DbExecutor db;
    private final Scheduler scheduler;
    private final LanguageManager lang;
    private final LogMenu menu;
    private final Logger logger;
    private final Telemetry telemetry;

    public LogViewer(ChestLogStore store, ContainerCodec codec, DbExecutor db, Scheduler scheduler,
                     LanguageManager lang, Logger logger, Telemetry telemetry) {
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
     * Opens (or re-opens at a different page) the log viewer for {@code owner} to {@code admin}. Clamps
     * {@code page} into range, and reports an empty log rather than opening a blank menu.
     */
    public void openLog(Player admin, UUID owner, String ownerName, int page) {
        db.supply(() -> {
            try {
                int total = store.countForOwner(owner);
                int pageCount = Math.max(1, (total + LogMenu.PAGE_SIZE - 1) / LogMenu.PAGE_SIZE);
                int clamped = Math.max(0, Math.min(page, pageCount - 1));
                List<LogEntry> entries = total == 0
                        ? List.of()
                        : store.page(owner, clamped * LogMenu.PAGE_SIZE, LogMenu.PAGE_SIZE);
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
                if (data.total() == 0) {
                    admin.sendMessage(lang.get("admin.log-empty", "player", ownerName));
                    return;
                }
                Inventory inv = menu.build(admin.locale(), owner, ownerName,
                        data.entries(), data.page(), data.pageCount());
                admin.openInventory(inv);
            });
        });
    }

    /**
     * Opens a read-only-safe sandbox showing exactly what the chest held at {@code entry}. The inventory
     * uses {@link LogSnapshotHolder}, so it never funnels through the session manager and is discarded on
     * close — the real chest is never affected.
     */
    public void openSnapshot(Player admin, UUID owner, String ownerName, LogEntry entry) {
        db.supply(() -> {
            try {
                ChestLogStore.SnapshotBlob blob = store.loadSnapshot(entry.id());
                if (blob == null) return null;   // pruned between listing and click
                return codec.decode(blob.data(), blob.size());
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }).whenComplete((contents, err) -> {
            scheduler.runAtEntity(admin, t -> {
                if (!admin.isOnline()) return;
                if (err != null || contents == null) {
                    if (err != null) {
                        logger.warn("Could not open chest-log snapshot {} for {}", entry.id(), owner, err);
                        telemetry.error(err, "log.snapshot-decode");
                    }
                    admin.sendMessage(lang.get("admin.log-snapshot-failed"));
                    return;
                }
                String time = TIME.format(Instant.ofEpochMilli(entry.ts()));
                String titleKey = entry.action() == LogAction.OPEN
                        ? "log.snapshot-title-open" : "log.snapshot-title-close";
                Component title = lang.getGui(admin.locale(), titleKey,
                        "player", ownerName,
                        "index", Integer.toString(entry.index()),
                        "time", time);
                Inventory inv = Bukkit.createInventory(
                        new LogSnapshotHolder(owner, entry.index()), contents.length, title);
                inv.setContents(contents);
                admin.openInventory(inv);
            });
        });
    }

    private void reportFailure(Player admin, String messageKey, Throwable err, String label) {
        logger.warn("Chest-log read failed", err);
        telemetry.error(err, label);
        scheduler.runAtEntity(admin, t -> {
            if (admin.isOnline()) admin.sendMessage(lang.get(messageKey));
        });
    }
}
