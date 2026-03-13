package com.enviouse.playtime;

import com.enviouse.playtime.command.CommandRegistration;
import com.enviouse.playtime.config.RankConfig;
import com.enviouse.playtime.data.JsonPlayerDataRepository;
import com.enviouse.playtime.data.PlayerDataRepository;
import com.enviouse.playtime.integration.LuckPermsService;
import com.enviouse.playtime.integration.OpacBridge;
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
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.nio.file.Path;

/**
 * Main mod entry point for the Playtime mod.
 * Server-side only playtime tracking system with rank progression,
 * LuckPerms integration, OpenPAC claim cleanup, and rotating backups.
 */
@Mod(Playtime.MODID)
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
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        // Register ourselves for game events
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(CommandRegistration.class);
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

        // 3. Integration bridges
        luckPermsService = new LuckPermsService();
        luckPermsService.initialize();

        opacBridge = new OpacBridge();

        // 4. Core services
        rankEngine = new RankEngine(rankConfig, repository, luckPermsService);
        sessionTracker = new SessionTracker(repository, rankEngine);

        // 5. Backup service
        backupService = new BackupService(worldDir, repository);
        backupService.initialize();

        // 6. Cleanup service
        cleanupService = new CleanupService(repository, rankConfig, opacBridge);

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

    // ── Static Accessors (for commands and other classes) ──────────────────────

    @Nullable
    public static PlayerDataRepository getRepository() { return repository; }
    @Nullable
    public static RankConfig getRankConfig() { return rankConfig; }
    @Nullable
    public static LuckPermsService getLuckPerms() { return luckPermsService; }
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
}
