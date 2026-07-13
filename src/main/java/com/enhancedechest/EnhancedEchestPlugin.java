package com.enhancedechest;

import com.enhancedechest.backup.BackupService;
import com.enhancedechest.config.ConfigMigrations;
import com.enhancedechest.config.PluginConfig;
import com.enhancedechest.config.YamlMigrator;
import com.enhancedechest.crossserver.CrossServerCoordinator;
import com.enhancedechest.crossserver.RedisCoordinator;
import com.enhancedechest.expiry.ExpirySweeper;
import com.enhancedechest.gui.dialog.IconCatalog;
import com.enhancedechest.lang.LanguageManager;
import com.enhancedechest.listener.EnderChestGuiListener;
import com.enhancedechest.listener.JoinMigrationListener;
import com.enhancedechest.listener.PlayerQuitListener;
import com.enhancedechest.listener.PlayerSettingsListener;
import com.enhancedechest.listener.VanillaEnderChestListener;
import com.enhancedechest.migration.AxVaultsMigrationService;
import com.enhancedechest.migration.CustomEnderChestMigrationService;
import com.enhancedechest.migration.DatabaseImportService;
import com.enhancedechest.migration.MigrationService;
import com.enhancedechest.migration.PlayerVaultsXMigrationService;
import com.enhancedechest.serialization.ContainerCodec;
import com.enhancedechest.service.ChestOpener;
import com.enhancedechest.service.ChestSessionManager;
import com.enhancedechest.service.ChestSpillService;
import com.enhancedechest.service.ChestTransferService;
import com.enhancedechest.service.DbExecutor;
import com.enhancedechest.service.PermissionChestService;
import com.enhancedechest.service.PlayerNameIndex;
import com.enhancedechest.service.PlayerSettingsCache;
import com.enhancedechest.scheduler.Scheduler;
import com.enhancedechest.service.StorageGateway;
import com.enhancedechest.storage.AutosaveService;
import com.enhancedechest.storage.CachedStorage;
import com.enhancedechest.storage.StorageBackend;
import com.enhancedechest.storage.StorageFactory;
import com.enhancedechest.telemetry.FastStatsTelemetry;
import com.enhancedechest.telemetry.Telemetry;
import com.enhancedechest.update.UpdateChecker;
import com.enhancedechest.update.UpdateNotifyListener;
import com.enhancedechest.util.DurationFormat;
import lombok.Getter;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.plugin.java.JavaPlugin;
import org.slf4j.Logger;

import java.io.File;

@Getter
public final class EnhancedEchestPlugin extends JavaPlugin {

    private PluginConfig pluginConfig;
    private LanguageManager languageManager;
    private ContainerCodec codec;
    private CachedStorage storage;
    private AutosaveService autosaveService;
    private DbExecutor dbExecutor;
    private StorageGateway storageGateway;
    private PlayerNameIndex playerNameIndex;
    private PlayerSettingsCache settingsCache;
    private ChestSessionManager sessionManager;
    private ChestSpillService spillService;
    private ChestTransferService chestTransferService;
    private PermissionChestService permissionChestService;
    private DatabaseImportService databaseImportService;
    private ChestOpener chestOpener;
    private ExpirySweeper expirySweeper;
    private BackupService backupService;
    private MigrationService migrationService;
    private AxVaultsMigrationService axVaultsMigrationService;
    private PlayerVaultsXMigrationService playerVaultsXMigrationService;
    private CustomEnderChestMigrationService customEnderChestMigrationService;
    private UpdateChecker updateChecker;
    private Scheduler scheduler;
    private Metrics metrics;
    private Telemetry telemetry;
    /** Non-null only in cross-server mode; the storage layer sees it as a {@link CrossServerCoordinator}. */
    private RedisCoordinator redisCoordinator;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        migrateConfigFile();
        reloadConfig();

        scheduler       = new Scheduler(this);
        pluginConfig    = new PluginConfig(getConfig());
        codec           = new ContainerCodec();

        // Lets a server owner drop icons/lang/<locale>.json into the plugin's data folder to add icon
        // picker search support for a client language this plugin doesn't bundle, or to override a
        // bundled one, without a plugin update or restart (/ee reload picks up changes).
        IconCatalog.setExternalLangDir(getDataFolder().toPath().resolve("icons").resolve("lang"),
                getSLF4JLogger());

        // FastStats custom metrics + error tracking behind the Telemetry facade. Wired before storage
        // (and the rest of the service layer) so every layer, including the cache's own shutdown flush,
        // can report errors; NOOP when no token was baked in at build time.
        telemetry = FastStatsTelemetry.create(this, pluginConfig);

        // Cross-server mode: a Redis-backed owner-lock coordinator makes the lazy cache safe on a
        // database shared by several servers (see CrossServerCoordinator). Both preconditions are
        // hard requirements — running unsynchronized on a shared database risks item loss/dupes, so
        // a misconfiguration disables the plugin instead of silently degrading.
        CrossServerCoordinator coordinator = CrossServerCoordinator.NOOP;
        if (pluginConfig.isCrossServerEnabled()) {
            if (pluginConfig.getDatabaseType().equalsIgnoreCase("sqlite")) {
                getSLF4JLogger().error("cross-server.enabled requires a shared mysql/mariadb/postgres "
                        + "database — SQLite cannot be shared between servers. Set database.type on "
                        + "every server, or turn cross-server off. Disabling the plugin.");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
            String serverId = pluginConfig.getCrossServerServerId();
            if (serverId == null || serverId.isBlank()) {
                serverId = "srv-" + java.util.UUID.randomUUID().toString().substring(0, 8);
            }
            redisCoordinator = new RedisCoordinator(pluginConfig.getRedisHost(),
                    pluginConfig.getRedisPort(), pluginConfig.getRedisPassword(),
                    pluginConfig.isRedisSsl(), pluginConfig.getRedisDatabase(),
                    pluginConfig.getRedisKeyPrefix(), serverId, scheduler, getSLF4JLogger(), telemetry);
            try {
                redisCoordinator.init();
            } catch (Exception e) {
                getSLF4JLogger().error("cross-server.enabled but Redis at {}:{} is unreachable — "
                        + "disabling the plugin rather than running unsynchronized on a shared database",
                        pluginConfig.getRedisHost(), pluginConfig.getRedisPort(), e);
                redisCoordinator.close();
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
            coordinator = redisCoordinator;
        }

        // The SQL backend is wrapped in the lazy write-back cache: a player's rows are read from SQL
        // once on first touch (join prefetch / on-demand miss) and served from memory after that; the
        // SQL side is otherwise touched only by the periodic autosave, the per-player write-back after
        // a quit, backups/imports, and the final flush at shutdown (CachedStorage.close()).
        StorageBackend backend = StorageFactory.create(pluginConfig, getDataFolder().toPath());
        storage         = new CachedStorage(backend, getSLF4JLogger(), telemetry, coordinator);

        try {
            storage.init();
        } catch (Exception e) {
            getSLF4JLogger().error("Failed to initialize storage, disabling plugin", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        languageManager = new LanguageManager(this, pluginConfig, pluginConfig.getLocale());

        // Service layer, wired bottom-up: the shared async pool, then the storage/settings wrappers
        // over it, then the dupe-safe session registry, then the item-moving and open-routing layers.
        // Storage calls now complete at memory speed (the SQL side is only touched by autosave/backup),
        // so the pool's real work is item encode/decode; a small handful of threads is plenty, but the
        // remote-backend sizing is kept as harmless headroom.
        boolean sqliteBackend = pluginConfig.getDatabaseType().equalsIgnoreCase("sqlite");
        dbExecutor     = new DbExecutor(sqliteBackend ? 4 : Math.max(8, pluginConfig.getDbPoolSize() * 2));
        storageGateway = new StorageGateway(storage, dbExecutor);
        playerNameIndex = new PlayerNameIndex(storageGateway, getSLF4JLogger(), telemetry);
        playerNameIndex.loadAll();
        settingsCache  = new PlayerSettingsCache(storage, dbExecutor, getSLF4JLogger(), playerNameIndex, telemetry);
        sessionManager = new ChestSessionManager(languageManager, codec, storage,
                getSLF4JLogger(), scheduler, dbExecutor, telemetry);

        // Cross-server handover: when another server asks for an owner this one still holds, flush +
        // evict + release them — but only once the player is gone from here and no chest session of
        // theirs is open or mid-save (the requester re-asks every poll round, so deferring is safe).
        // flushOwner performs the release itself via the eviction path's coordinator hooks.
        if (redisCoordinator != null) {
            redisCoordinator.setReleaseRequestHandler(uuid -> {
                if (storage.isPinned(uuid) || sessionManager.hasActivity(uuid)) {
                    return;
                }
                dbExecutor.run(() -> {
                    try {
                        storage.flushOwner(uuid);
                    } catch (Exception e) {
                        getSLF4JLogger().error("Cross-server handover flush failed for {} — the "
                                + "requesting server will retry", uuid, e);
                        telemetry.error(e, "crossserver.handover-flush");
                    }
                });
            });
        }
        spillService   = new ChestSpillService(sessionManager, storage, codec, storageGateway,
                pluginConfig.getTempExpiryMillis());
        chestTransferService = new ChestTransferService(sessionManager, storage, codec, storageGateway,
                languageManager, scheduler, dbExecutor, getSLF4JLogger(), telemetry,
                pluginConfig.getTempExpiryMillis());
        permissionChestService = new PermissionChestService(storageGateway, spillService,
                pluginConfig.isPermissionChestsEnabled(), pluginConfig.getDefaultSize());
        databaseImportService = new DatabaseImportService(storage, pluginConfig, getSLF4JLogger(),
                getDataFolder().toPath());
        chestOpener    = new ChestOpener(sessionManager, storageGateway, settingsCache, storage,
                dbExecutor, languageManager, scheduler, getSLF4JLogger(), pluginConfig.getDefaultSize(),
                permissionChestService, spillService, pluginConfig, databaseImportService, telemetry);

        migrationService  = new MigrationService(storage, codec, getSLF4JLogger(),
                sessionManager, scheduler, telemetry, pluginConfig.getTempExpiryMillis());
        axVaultsMigrationService = new AxVaultsMigrationService(storage, codec, getSLF4JLogger(), telemetry,
                getDataFolder().getParentFile().toPath());
        playerVaultsXMigrationService = new PlayerVaultsXMigrationService(storage, codec, getSLF4JLogger(),
                telemetry, getDataFolder().getParentFile().toPath());
        customEnderChestMigrationService = new CustomEnderChestMigrationService(storage, codec, getSLF4JLogger(),
                telemetry, getDataFolder().getParentFile().toPath());

        expirySweeper = new ExpirySweeper(spillService, storage, scheduler,
                getSLF4JLogger(), telemetry, pluginConfig.getExpiryCheckIntervalMillis());
        expirySweeper.start();

        backupService = new BackupService(storage, scheduler, getSLF4JLogger(), telemetry, getDataFolder().toPath(),
                pluginConfig.isBackupEnabled(), pluginConfig.getBackupIntervalMillis(),
                pluginConfig.getBackupKeep(), pluginConfig.getBackupFolder(),
                pluginConfig.getDatabaseType());
        backupService.start();
        if (pluginConfig.isBackupOnStartup()) {
            backupService.backupNowAsync();
        }

        // Periodic write-back of dirty in-memory rows to the database + eviction of flushed offline
        // players (the final full save happens in CachedStorage.close() at shutdown).
        autosaveService = new AutosaveService(storage, scheduler, getSLF4JLogger(), telemetry,
                pluginConfig.getAutosaveIntervalMillis());
        autosaveService.start();

        var pm = getServer().getPluginManager();
        pm.registerEvents(new VanillaEnderChestListener(chestOpener), this);
        pm.registerEvents(new EnderChestGuiListener(sessionManager, scheduler, languageManager, pluginConfig), this);
        pm.registerEvents(new PlayerQuitListener(sessionManager, scheduler), this);
        pm.registerEvents(new JoinMigrationListener(pluginConfig, migrationService, storage,
                dbExecutor, getSLF4JLogger(), telemetry), this);
        pm.registerEvents(new PlayerSettingsListener(settingsCache, chestOpener, storage,
                autosaveService), this);

        // Pin + preload players already online (a /reload or hot-load fires no join event for them):
        // the pin keeps them cache-resident while online, and the settings preload also materializes
        // their chest rows (the same prefetch the join listener performs). The name index is unaffected
        // here — it is written lazily by ChestOpener the first time a player opens their ender chest.
        getServer().getOnlinePlayers().forEach(p -> {
            storage.pin(p.getUniqueId());
            settingsCache.preloadSettings(p.getUniqueId());
        });

        updateChecker = new UpdateChecker(getPluginMeta().getVersion(), getSLF4JLogger());
        pm.registerEvents(new UpdateNotifyListener(scheduler, updateChecker, languageManager), this);
        updateChecker.checkAsync(scheduler);

        initMetrics();

        printStartupBanner(getSLF4JLogger());
    }

    @Override
    public void onDisable() {
        if (metrics != null) {
            metrics.shutdown();
        }
        if (expirySweeper != null) {
            expirySweeper.stop();
        }
        if (backupService != null) {
            backupService.stop();
        }
        if (autosaveService != null) {
            autosaveService.stop();
        }
        // Flush live sessions and pending saves first, drop the settings cache, then close the async
        // pool last — so the flush above can still dispatch its writes onto it.
        if (sessionManager != null) {
            sessionManager.shutdown();
        }
        // After the session flush, so a save failure during that flush is still reported before the
        // final telemetry submission goes out.
        if (telemetry != null) {
            telemetry.shutdown();
        }
        if (settingsCache != null) {
            settingsCache.clear();
        }
        if (dbExecutor != null) {
            dbExecutor.shutdown();
        }
        // Last: writes ALL still-dirty in-memory data to the database (the "save everything at
        // shutdown" half of the keep-in-memory model), then closes the connection pool.
        if (storage != null) {
            storage.close();
        }
        // After the final flush: every owner's rows are now in SQL, so the distributed locks may be
        // handed to whichever server the players land on next.
        if (redisCoordinator != null) {
            redisCoordinator.close();
        }
        if (scheduler != null) {
            scheduler.cancelAllTasks();
        }
        getSLF4JLogger().info("EnhancedEchest disabled.");
    }

    public void reload() {
        migrateConfigFile();
        reloadConfig();

        String previousDbSignature = databaseSignature();
        pluginConfig.reload(getConfig());
        languageManager.reload(pluginConfig.getLocale());

        // Re-apply the runtime-tunable values to the already-running services so they take effect
        // without a restart. These touch only work started after this point, so they are dupe-safe
        // even while async saves are in flight.
        chestOpener.setDefaultSize(pluginConfig.getDefaultSize());
        spillService.setTempExpiry(pluginConfig.getTempExpiryMillis());
        chestTransferService.setTempExpiry(pluginConfig.getTempExpiryMillis());
        migrationService.setTempExpiry(pluginConfig.getTempExpiryMillis());
        permissionChestService.setConfig(pluginConfig.isPermissionChestsEnabled(),
                pluginConfig.getDefaultSize());
        expirySweeper.reschedule(pluginConfig.getExpiryCheckIntervalMillis());
        backupService.reschedule(pluginConfig.isBackupEnabled(), pluginConfig.getBackupIntervalMillis(),
                pluginConfig.getBackupKeep());
        autosaveService.reschedule(pluginConfig.getAutosaveIntervalMillis());
        // Re-reads plugins/EnhancedEchest/icons/lang/*.json, so a file added or edited since startup
        // (or since the last reload) takes effect immediately.
        IconCatalog.reloadLocaleNames();

        // Database + cross-server settings are bound when the connection pool / Redis coordinator are
        // built at startup; rebuilding them on a live reload could drop connections mid-save and risk
        // dupes, so we don't. Warn if they changed.
        if (!databaseSignature().equals(previousDbSignature)) {
            getSLF4JLogger().warn("Database/cross-server settings changed in config but are still "
                    + "running on the previous connection — a full server restart is required for "
                    + "them to take effect.");
        }

        getSLF4JLogger().info("Configuration reloaded.");
    }

    /** Snapshot of every database-pool setting, used to detect changes that require a restart. */
    private String databaseSignature() {
        PluginConfig c = pluginConfig;
        return String.join(" ",
                c.getDatabaseType(), c.getSqliteFile(), c.getDbHost(), String.valueOf(c.getDbPort()),
                c.getDbName(), c.getDbUsername(), c.getDbPassword(), String.valueOf(c.isDbSsl()),
                String.valueOf(c.getDbPoolSize()),
                // Cross-server coordination is bound at startup exactly like the connection pool.
                String.valueOf(c.isCrossServerEnabled()), c.getCrossServerServerId(),
                c.getRedisHost(), String.valueOf(c.getRedisPort()), c.getRedisPassword(),
                String.valueOf(c.isRedisSsl()), String.valueOf(c.getRedisDatabase()),
                c.getRedisKeyPrefix());
    }

    /** Registers the plugin with bStats (<a href="https://bstats.org/plugin/bukkit/EnhancedEchest/32142">...</a>). */
    private void initMetrics() {
        int pluginId = 32142;
        metrics = new Metrics(this, pluginId);

        metrics.addCustomChart(new SimplePie("storage_type",
                () -> pluginConfig.getDatabaseType().toUpperCase()));
        metrics.addCustomChart(new SimplePie("language",
                () -> pluginConfig.getLocale()));
    }

    private void migrateConfigFile() {
        YamlMigrator.migrate(
                new File(getDataFolder(), "config.yml"),
                getResource("config.yml"),
                ConfigMigrations.CONFIG,
                getSLF4JLogger()
        );
    }

    /** Human-readable auto-backup state for the startup banner. */
    private String backupStatus() {
        if (!pluginConfig.isBackupEnabled()) {
            return "OFF";
        }
        if (!storage.supportsBackup()) {
            return "UNSUPPORTED (" + pluginConfig.getDatabaseType() + ")";
        }
        return "every " + DurationFormat.formatRemaining(pluginConfig.getBackupIntervalMillis());
    }

    private void printStartupBanner(Logger log) {
        String version   = getPluginMeta().getVersion();
        String storage   = pluginConfig.getDatabaseType().toUpperCase();
        String locale    = pluginConfig.getLocale();
        String migration = pluginConfig.isMigrationEnabled() ? "ON" : "OFF";
        String backup    = backupStatus();
        String folia     = scheduler.isFolia() ? "Folia" : "Paper";
        String crossSrv  = redisCoordinator != null
                ? "ON (" + redisCoordinator.serverId() + ")" : "OFF";
        String sep       = "——————————————[ EnhancedEchest ]——————————————";

        log.info("> {}", sep);
        log.info(">");
        log.info(">   Version   : {}", version);
        log.info(">   Platform  : {}", folia);
        log.info(">   Storage   : {}", storage);
        log.info(">   Language  : {}", locale);
        log.info(">   Migration : {}", migration);
        log.info(">   Backup    : {}", backup);
        log.info(">   X-Server  : {}", crossSrv);
        log.info(">");
        log.info("> {}", sep);
    }
}
