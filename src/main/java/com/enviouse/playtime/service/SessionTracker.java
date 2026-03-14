package com.enviouse.playtime.service;

import com.enviouse.playtime.Config;
import com.enviouse.playtime.Playtime;
import com.enviouse.playtime.data.PlayerDataRepository;
import com.enviouse.playtime.data.PlayerRecord;
import com.enviouse.playtime.data.RankDefinition;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks per-player session activity and AFK state.
 * Called from Forge tick and player events.
 */
public class SessionTracker {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Per-player in-memory session state. */
    private static class PlayerSession {
        float lastYaw;
        float lastPitch;
        int afkTicks;
        long activeSessionTicks;
        boolean afkNotified;
    }

    private final Map<UUID, PlayerSession> sessions = new ConcurrentHashMap<>();
    private final PlayerDataRepository repository;
    private final RankEngine rankEngine;

    public SessionTracker(PlayerDataRepository repository, RankEngine rankEngine) {
        this.repository = repository;
        this.rankEngine = rankEngine;
    }

    /** Called when a player logs in. */
    public void onPlayerJoin(MinecraftServer server, ServerPlayer player) {
        UUID uuid = player.getUUID();
        String name = player.getGameProfile().getName();

        PlayerSession session = new PlayerSession();
        session.lastYaw = player.getYRot();
        session.lastPitch = player.getXRot();
        session.afkTicks = 0;
        session.activeSessionTicks = 0;
        session.afkNotified = false;
        sessions.put(uuid, session);

        PlayerRecord record = repository.getPlayer(uuid);
        boolean isFirstJoin = (record == null);

        if (isFirstJoin) {
            record = new PlayerRecord(uuid, name);
            repository.putPlayer(record);
            LOGGER.info("[Playtime] First join for {} ({})", name, uuid);

            // Set initial rank directly (first rank is auto-claimed, no action needed from player)
            RankDefinition initialRank = rankEngine.getCurrentRank(0);
            record.setCurrentRankId(initialRank.getId());
            repository.markDirty();

            // Sync with LuckPerms
            Playtime.getLuckPerms().syncRank(uuid, null, initialRank, server);

            // First-join broadcast
            if (Config.firstJoinBroadcast) {
                server.getPlayerList().broadcastSystemMessage(
                        Component.literal("§a" + name + " has joined the server for the first time, say hi!"),
                        false
                );
            }

            // Set default OPAC claim color
            if (Config.opacEnabled) {
                server.getCommands().performPrefixedCommand(
                        player.createCommandSourceStack().withSuppressedOutput(),
                        "openpac player-config set claims.color " + Config.defaultClaimColorHex
                );
            }

            repository.save(false);
        } else {
            // Update mutable display data
            record.setLastUsername(name);
            record.setLastSeenEpochMs(System.currentTimeMillis());
            repository.markDirty();

            // Verify rank progression is up to date
            rankEngine.checkAndApplyProgression(server, uuid, record.getTotalPlaytimeTicks());
            repository.save(false);
        }
    }

    /** Called when a player logs out. */
    public void onPlayerLeave(MinecraftServer server, ServerPlayer player) {
        UUID uuid = player.getUUID();

        flushSession(server, uuid);

        PlayerRecord record = repository.getPlayer(uuid);
        if (record != null) {
            record.setLastSeenEpochMs(System.currentTimeMillis());
            repository.markDirty();
            repository.save(false);
        }

        sessions.remove(uuid);
    }

    /** Called every server tick. */
    public void onServerTick(MinecraftServer server, int tickCount) {
        if (!repository.isLoaded()) return;

        // AFK / activity check
        if (tickCount % Config.afkCheckInterval == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                tickPlayer(server, player);
            }
        }

        // Periodic flush of session ticks to totals
        if (tickCount % Config.flushIntervalTicks == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                flushSession(server, player.getUUID());
            }
        }

        // Periodic save
        if (tickCount % Config.saveIntervalTicks == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                flushSession(server, player.getUUID());
            }
            repository.save(false);
        }
    }

    /** Check one player's activity state. */
    private void tickPlayer(MinecraftServer server, ServerPlayer player) {
        UUID uuid = player.getUUID();
        PlayerSession session = sessions.get(uuid);
        if (session == null) return;

        PlayerRecord record = repository.getPlayer(uuid);
        if (record == null) return;

        float currentYaw = Math.round(player.getYRot() * 10f) / 10f;
        float currentPitch = Math.round(player.getXRot() * 10f) / 10f;

        double yawDelta = yawDelta(currentYaw, session.lastYaw);
        double pitchDelta = Math.abs(currentPitch - session.lastPitch);

        boolean isActive = yawDelta >= Config.afkLookThreshold || pitchDelta >= Config.afkLookThreshold;

        if (isActive) {
            boolean wasAfk = session.afkTicks >= Config.afkTimeoutTicks;
            session.afkTicks = 0;
            session.afkNotified = false;

            if (wasAfk) {
                player.displayClientMessage(Component.literal("§a✓ Playtime tracking resumed!"), true);
            }

            session.activeSessionTicks += Config.afkCheckInterval;
            session.lastYaw = currentYaw;
            session.lastPitch = currentPitch;
        } else {
            session.afkTicks += Config.afkCheckInterval;

            if (session.afkTicks < Config.afkTimeoutTicks) {
                // Not yet AFK, still counting time
                session.activeSessionTicks += Config.afkCheckInterval;
            } else {
                // AFK — paused
                if (!session.afkNotified || session.afkTicks % Config.afkNotifyInterval == 0) {
                    player.displayClientMessage(Component.literal("§c⚠ AFK detected — Playtime tracking paused"), true);
                    session.afkNotified = true;
                }
            }
        }
    }

    /** Flush accumulated session ticks into the player's total. */
    private void flushSession(MinecraftServer server, UUID uuid) {
        PlayerSession session = sessions.get(uuid);
        if (session == null) return;

        long ticks = session.activeSessionTicks;
        if (ticks <= 0) return;

        PlayerRecord record = repository.getPlayer(uuid);
        if (record == null) return;

        long newTotal = record.addPlaytimeTicks(ticks);
        session.activeSessionTicks = 0;
        repository.markDirty();

        rankEngine.checkAndApplyProgression(server, uuid, newTotal);
    }

    /** Is the given player currently AFK? */
    public boolean isAfk(UUID uuid) {
        PlayerSession session = sessions.get(uuid);
        return session != null && session.afkTicks >= Config.afkTimeoutTicks;
    }

    /** Get the current unflushed session ticks for a player. */
    public long getSessionTicks(UUID uuid) {
        PlayerSession session = sessions.get(uuid);
        return session != null ? session.activeSessionTicks : 0;
    }

    /** Flush all online players. */
    public void flushAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            flushSession(server, player.getUUID());
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static double yawDelta(float a, float b) {
        double d = Math.abs((a - b) % 360);
        return d > 180 ? 360 - d : d;
    }
}

