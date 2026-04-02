package com.enviouse.playtime;

import com.enviouse.playtime.command.CommandRegistration;
import com.enviouse.playtime.config.RankConfig;
import com.enviouse.playtime.data.JsonPlayerDataRepository;
import com.enviouse.playtime.data.PlayerDataRepository;
import com.enviouse.playtime.integration.IntegratedChatHandler;
import com.enviouse.playtime.integration.LuckPermsService;
import com.enviouse.playtime.integration.OpacBridge;
import com.enviouse.playtime.migration.KubeJsImporter;
import com.enviouse.playtime.network.PlaytimeNetwork;
import com.enviouse.playtime.service.BackupService;
import com.enviouse.playtime.service.CleanupService;
import com.enviouse.playtime.service.RankEngine;
import com.enviouse.playtime.service.SessionTracker;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.nio.file.Path;

/**
 * Main mod entry point for the Playtime mod.
 * Server-side only playtime tracking system with rank progression,
 * LuckPerms integration, OpenPAC claim cleanup, and rotating backups.
 */
@Mod(Playtime.MODID)
@SuppressWarnings({"deprecation", "removal"}) // ModLoadingContext.get() is the correct API for 1.20.1
public class Playtime {

    public static final String MODID = "playtime";
    private static final Logger LOGGER = LogUtils.getLogger();

    // ── Service references (initialised on server start) ───────────────────────
    private static JsonPlayerDataRepository repository;
    private static RankConfig rankConfig;
    private static LuckPermsService luckPermsService;
    private static OpacBridge opacBridge;
    private static RankEngine rankEngine;
    private static SessionTracker sessionTracker;
    private static BackupService backupService;
    private static CleanupService cleanupService;

    public Playtime() {
        // Register network channel (must be early, before any packets)
        PlaytimeNetwork.register();

        // Register config
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC, "playtime.toml");

        // Register ourselves for game events
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(CommandRegistration.class);
        MinecraftForge.EVENT_BUS.register(new IntegratedChatHandler());

        // Register client-side inventory button (only on physical client)
        if (FMLEnvironment.dist.isClient()) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    MinecraftForge.EVENT_BUS.register(new com.enviouse.playtime.client.InventoryButtonHandler()));
        }
    }

    // ── Server Lifecycle ───────────────────────────────────────────────────────

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        Path worldDir = server.getWorldPath(LevelResource.ROOT);

        LOGGER.info("[Playtime] Initialising playtime tracking system...");

        // 1. Load rank config
        rankConfig = new RankConfig(worldDir);
        rankConfig.load();

        // 2. Load player data repository
        repository = new JsonPlayerDataRepository(worldDir);
        if (!repository.load()) {
            LOGGER.error("[Playtime] CRITICAL: Failed to load player data. Most features will be disabled.");
        }

        // 3. Integration bridges — only load LuckPermsService if LP is on the classpath
        //    (the class imports LP types which causes NoClassDefFoundError in singleplayer)
        if (isLuckPermsPresent()) {
            try {
                luckPermsService = new LuckPermsService();
                luckPermsService.initialize();
            } catch (NoClassDefFoundError | Exception e) {
                luckPermsService = null;
                LOGGER.info("[Playtime] LuckPerms classes not available — running without LP integration.");
            }
        } else {
            luckPermsService = null;
            LOGGER.info("[Playtime] LuckPerms not detected on classpath — running without LP integration.");
        }

        opacBridge = new OpacBridge();

        // 4. Core services
        rankEngine = new RankEngine(rankConfig, repository, luckPermsService);
        sessionTracker = new SessionTracker(repository, rankEngine);

        // 4b. Auto-import from KubeJS (if imports.json exists in the playtime folder)
        int importResult = KubeJsImporter.autoImport(worldDir, repository, rankEngine, server);
        if (importResult > 0) {
            LOGGER.info("[Playtime] Auto-imported {} player records from KubeJS data.", importResult);
        } else if (importResult == 0) {
            LOGGER.warn("[Playtime] Auto-import found imports.json but imported 0 records (check logs for errors).");
        }

        // 5. Backup service
        backupService = new BackupService(worldDir, repository);
        backupService.initialize();

        // 6. Cleanup service
        cleanupService = new CleanupService(repository, rankConfig);

        // Schedule initial cleanup check after delay
        if (Config.cleanupEnabled && Config.cleanupDelayTicks > 0) {
            LOGGER.info("[Playtime] Scheduling cleanup check in {} ticks...", Config.cleanupDelayTicks);
            // Cleanup will run on tick based on counter, we track it via tick count
        }

        LOGGER.info("[Playtime] Initialisation complete. {} players loaded, {} ranks configured.",
                repository.getAllPlayers().size(), rankConfig.getRanks().size());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("[Playtime] Server stopping — flushing data...");

        if (sessionTracker != null) {
            sessionTracker.flushAll(event.getServer());
        }
        if (repository != null) {
            repository.save(true);
        }
        if (luckPermsService != null) {
            luckPermsService.shutdown();
        }

        // Clear references
        repository = null;
        rankConfig = null;
        luckPermsService = null;
        opacBridge = null;
        rankEngine = null;
        sessionTracker = null;
        backupService = null;
        cleanupService = null;

        LOGGER.info("[Playtime] Shutdown complete.");
    }

    // ── Tick Events ────────────────────────────────────────────────────────────

    private boolean cleanupRanOnce = false;
    private int ticksSinceStart = 0;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (sessionTracker == null) return;

        MinecraftServer server = event.getServer();
        int tickCount = server.getTickCount();

        // Session tracking (AFK, activity, saving)
        sessionTracker.onServerTick(server, tickCount);

        // Backup checks
        if (backupService != null && tickCount % Config.backupCheckIntervalTicks == 0) {
            backupService.tick();
        }

        // One-time scheduled cleanup
        ticksSinceStart++;
        if (!cleanupRanOnce && Config.cleanupEnabled && ticksSinceStart >= Config.cleanupDelayTicks) {
            cleanupRanOnce = true;
            LOGGER.info("[Playtime] Running scheduled cleanup check...");
            cleanupService.runCleanup(server, null, false);
        }
    }

    // ── Player Events ──────────────────────────────────────────────────────────

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (sessionTracker == null) return;
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) return;
        sessionTracker.onPlayerJoin(serverPlayer.getServer(), serverPlayer);
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (sessionTracker == null) return;
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) return;
        sessionTracker.onPlayerLeave(serverPlayer.getServer(), serverPlayer);
    }

    // ── Interaction Events (feed INTERACTION signal to AFK detector) ────────────

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (sessionTracker != null && event.getPlayer() instanceof ServerPlayer sp) {
            sessionTracker.onActivity(sp.getUUID());
        }
    }

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (sessionTracker != null && event.getEntity() instanceof ServerPlayer sp) {
            sessionTracker.onActivity(sp.getUUID());
        }
    }

    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent event) {
        if (sessionTracker != null && event.getEntity() instanceof ServerPlayer sp) {
            sessionTracker.onActivity(sp.getUUID());
        }
    }

    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (sessionTracker != null && event.getEntity() instanceof ServerPlayer sp) {
            sessionTracker.onActivity(sp.getUUID());
        }
    }

    @SubscribeEvent
    public void onServerChat(ServerChatEvent event) {
        if (sessionTracker != null) {
            sessionTracker.onActivity(event.getPlayer().getUUID());
        }
    }

    // ── Static Accessors (for commands and other classes) ──────────────────────

    @Nullable
    public static PlayerDataRepository getRepository() { return repository; }
    @Nullable
    public static RankConfig getRankConfig() { return rankConfig; }
    @Nullable
    public static LuckPermsService getLuckPerms() { return luckPermsService; }

    /**
     * Returns true if the LuckPermsService was successfully loaded AND the LP API is available.
     * Safe to call even when LP is not on the classpath.
     */
    public static boolean isLuckPermsAvailable() {
        LuckPermsService lp = luckPermsService;
        return lp != null && lp.isAvailable();
    }

    /**
     * Null-safe display colour helper. Returns LP group prefix colour when available,
     * otherwise the rank's fallback colour. Safe to call without LuckPerms installed.
     */
    public static String getDisplayColor(com.enviouse.playtime.data.RankDefinition rank) {
        LuckPermsService lp = luckPermsService;
        if (lp != null) return lp.getDisplayColor(rank);
        return rank.getFallbackColor();
    }

    /**
     * Null-safe styled rank name helper. Returns an LP-styled Component when available,
     * otherwise styles via ColorUtil with the rank's fallback colour.
     * Safe to call without LuckPerms installed.
     */
    public static net.minecraft.network.chat.MutableComponent getStyledRankName(com.enviouse.playtime.data.RankDefinition rank) {
        LuckPermsService lp = luckPermsService;
        if (lp != null) return lp.getStyledRankName(rank);
        return com.enviouse.playtime.util.ColorUtil.rankDisplay(rank.getFallbackColor(), rank.getDisplayName());
    }
    @Nullable
    public static OpacBridge getOpacBridge() { return opacBridge; }
    @Nullable
    public static RankEngine getRankEngine() { return rankEngine; }
    @Nullable
    public static SessionTracker getSessionTracker() { return sessionTracker; }
    @Nullable
    public static BackupService getBackupService() { return backupService; }
    @Nullable
    public static CleanupService getCleanupService() { return cleanupService; }

    // ── Helper ──────────────────────────────────────────────────────────────────

    /**
     * Checks if the LuckPerms API is on the classpath without triggering class loading
     * of any LP type. Safe to call in singleplayer / environments without LP.
     */
    private static boolean isLuckPermsPresent() {
        try {
            Class.forName("net.luckperms.api.LuckPermsProvider", false,
                    Playtime.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
