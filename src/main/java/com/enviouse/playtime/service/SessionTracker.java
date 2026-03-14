package com.enviouse.playtime.service;

import com.enviouse.playtime.Config;
import com.enviouse.playtime.Playtime;
import com.enviouse.playtime.data.PlayerDataRepository;
import com.enviouse.playtime.data.PlayerRecord;
import com.enviouse.playtime.data.RankDefinition;
import com.enviouse.playtime.network.AfkSyncS2CPacket;
import com.enviouse.playtime.network.PlaytimeNetwork;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks per-player session activity and AFK state.
 * Uses multi-signal detection: position, rotation, hotbar, sprint, and
 * interaction events must all contribute to prove the player is human.
 * A player must produce at least {@code Config.afkMinSignals} distinct
 * signal types within the timeout window to be considered active.
 * <p>
 * This is server-authoritative — clients cannot bypass it.
 * AFK state changes are broadcast to all clients via {@link AfkSyncS2CPacket}.
 */
public class SessionTracker {

    private static final Logger LOGGER = LogUtils.getLogger();

    // Signal type bitmask constants
    private static final int SIG_ROTATION    = 1;
    private static final int SIG_POSITION    = 1 << 1;
    private static final int SIG_HOTBAR      = 1 << 2;
    private static final int SIG_SPRINT      = 1 << 3;
    private static final int SIG_INTERACTION = 1 << 4;

    /** Per-player in-memory session state. */
    private static class PlayerSession {
        float lastYaw, lastPitch;
        double lastX, lastY, lastZ;
        int lastHotbarSlot;
        boolean lastSprinting;
        int afkTicks;
        boolean wasAfk;
        boolean afkNotified;
        int signalMask;
        long activeSessionTicks;
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
        session.lastX = player.getX();
        session.lastY = player.getY();
        session.lastZ = player.getZ();
        session.lastHotbarSlot = player.getInventory().selected;
        session.lastSprinting = player.isSprinting();
        session.afkTicks = 0;
        session.wasAfk = false;
        session.afkNotified = false;
        session.signalMask = 0;
        session.activeSessionTicks = 0;
        sessions.put(uuid, session);

        PlayerRecord record = repository.getPlayer(uuid);
        boolean isFirstJoin = (record == null);

        if (isFirstJoin) {
            record = new PlayerRecord(uuid, name);
            repository.putPlayer(record);
            LOGGER.info("[Playtime] First join for {} ({})", name, uuid);

            RankDefinition initialRank = rankEngine.getCurrentRank(0);
            record.setCurrentRankId(initialRank.getId());
            repository.markDirty();

            Playtime.getLuckPerms().syncRank(uuid, null, initialRank, server);

            if (Config.firstJoinBroadcast) {
                server.getPlayerList().broadcastSystemMessage(
                        Component.literal("§a" + name + " has joined the server for the first time, say hi!"),
                        false
                );
            }

            if (Config.opacEnabled) {
                server.getCommands().performPrefixedCommand(
                        player.createCommandSourceStack().withSuppressedOutput(),
                        "openpac player-config set claims.color " + Config.defaultClaimColorHex
                );
            }

            repository.save(false);
        } else {
            record.setLastUsername(name);
            record.setLastSeenEpochMs(System.currentTimeMillis());
            repository.markDirty();

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

        if (tickCount % Config.afkCheckInterval == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                tickPlayer(server, player);
            }
        }

        if (tickCount % Config.flushIntervalTicks == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                flushSession(server, player.getUUID());
            }
        }

        if (tickCount % Config.saveIntervalTicks == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                flushSession(server, player.getUUID());
            }
            repository.save(false);
        }
    }

    /**
     * Multi-signal activity check. Samples position, rotation, hotbar, sprint.
     * Each changed value sets a bit in signalMask. The player is active only
     * when enough distinct signal types have fired (Config.afkMinSignals).
     */
    private void tickPlayer(MinecraftServer server, ServerPlayer player) {
        UUID uuid = player.getUUID();
        PlayerSession session = sessions.get(uuid);
        if (session == null) return;

        PlayerRecord record = repository.getPlayer(uuid);
        if (record == null) return;

        // Sample rotation
        float curYaw = Math.round(player.getYRot() * 10f) / 10f;
        float curPitch = Math.round(player.getXRot() * 10f) / 10f;
        double yawDelta = yawDelta(curYaw, session.lastYaw);
        double pitchDelta = Math.abs(curPitch - session.lastPitch);
        if (yawDelta >= Config.afkLookThreshold || pitchDelta >= Config.afkLookThreshold) {
            session.signalMask |= SIG_ROTATION;
            session.lastYaw = curYaw;
            session.lastPitch = curPitch;
        }

        // Sample position
        double dx = player.getX() - session.lastX;
        double dy = player.getY() - session.lastY;
        double dz = player.getZ() - session.lastZ;
        double distSq = dx * dx + dy * dy + dz * dz;
        if (distSq >= Config.afkMoveThreshold * Config.afkMoveThreshold) {
            session.signalMask |= SIG_POSITION;
            session.lastX = player.getX();
            session.lastY = player.getY();
            session.lastZ = player.getZ();
        }

        // Sample hotbar slot
        int curSlot = player.getInventory().selected;
        if (curSlot != session.lastHotbarSlot) {
            session.signalMask |= SIG_HOTBAR;
            session.lastHotbarSlot = curSlot;
        }

        // Sample sprint toggle
        boolean curSprint = player.isSprinting();
        if (curSprint != session.lastSprinting) {
            session.signalMask |= SIG_SPRINT;
            session.lastSprinting = curSprint;
        }

        // Evaluate: enough distinct signals?
        int signalCount = Integer.bitCount(session.signalMask);
        boolean isActive = signalCount >= Config.afkMinSignals;

        if (isActive) {
            session.signalMask = 0;
            session.afkTicks = 0;
            session.afkNotified = false;

            if (session.wasAfk) {
                session.wasAfk = false;
                player.displayClientMessage(Component.literal("§a✓ Playtime tracking resumed!"), true);
                broadcastAfkState(server, uuid, false);
            }

            session.activeSessionTicks += Config.afkCheckInterval;

            long projectedTotal = record.getTotalPlaytimeTicks() + session.activeSessionTicks;
            rankEngine.checkAndApplyProgression(server, uuid, projectedTotal);
        } else {
            session.afkTicks += Config.afkCheckInterval;

            if (session.afkTicks < Config.afkTimeoutTicks) {
                session.activeSessionTicks += Config.afkCheckInterval;
            } else {
                if (!session.wasAfk) {
                    session.wasAfk = true;
                    broadcastAfkState(server, uuid, true);
                }
                if (!session.afkNotified || session.afkTicks % Config.afkNotifyInterval == 0) {
                    player.displayClientMessage(Component.literal("§c⚠ AFK detected — Playtime tracking paused"), true);
                    session.afkNotified = true;
                }
            }
        }
    }

    /**
     * Called from Forge event handlers when a player performs a meaningful action
     * (block break/place, attack entity, chat). Sets the INTERACTION signal bit.
     */
    public void onActivity(UUID uuid) {
        PlayerSession session = sessions.get(uuid);
        if (session != null) {
            session.signalMask |= SIG_INTERACTION;
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

    /** Broadcast AFK state change to all online players. */
    private void broadcastAfkState(MinecraftServer server, UUID uuid, boolean afk) {
        PlaytimeNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), new AfkSyncS2CPacket(uuid, afk));
    }

    private static double yawDelta(float a, float b) {
        double d = Math.abs((a - b) % 360);
        return d > 180 ? 360 - d : d;
    }
}
