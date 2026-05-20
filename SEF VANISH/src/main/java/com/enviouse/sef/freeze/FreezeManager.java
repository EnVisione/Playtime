package com.enviouse.sef.freeze;

import com.enviouse.sef.ServerEssentialsForge;
import com.enviouse.sef.TextFormatter;
import com.enviouse.sef.config.ConfigHandler;
import com.enviouse.sef.config.PermissionsHandler;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages frozen player state. Frozen players cannot move, look around,
 * jump, mine, break, place, use commands (except chat if allowed).
 * All messages/formats are config-driven.
 */
public class FreezeManager {
    /**
     * Data class holding freeze state for one player.
     */
    public static class FreezeData {
        public final UUID playerUUID;
        public final String playerName;
        public final String adminName;
        public final String reason;
        public final long frozenAtTick;
        /** Duration in ticks, -1 = infinite */
        public final long durationTicks;
        /** The position the player should be locked at */
        public final Vec3 frozenPos;
        public final float frozenYRot;
        public final float frozenXRot;
        /** Last tick a reminder was sent */
        public long lastReminderTick;

        public FreezeData(UUID playerUUID, String playerName, String adminName,
                          String reason, long frozenAtTick, long durationTicks,
                          Vec3 frozenPos, float frozenYRot, float frozenXRot) {
            this.playerUUID = playerUUID;
            this.playerName = playerName;
            this.adminName = adminName;
            this.reason = reason;
            this.frozenAtTick = frozenAtTick;
            this.durationTicks = durationTicks;
            this.frozenPos = frozenPos;
            this.frozenYRot = frozenYRot;
            this.frozenXRot = frozenXRot;
            this.lastReminderTick = frozenAtTick;
        }

        public boolean isExpired(long currentTick) {
            if (durationTicks < 0) return false; // infinite
            return currentTick >= frozenAtTick + durationTicks;
        }

        public String getDurationString() {
            if (durationTicks < 0) return "Infinite";
            long totalSeconds = durationTicks / 20;
            if (totalSeconds < 60) return totalSeconds + "s";
            if (totalSeconds < 3600) return (totalSeconds / 60) + "m " + (totalSeconds % 60) + "s";
            return (totalSeconds / 3600) + "h " + ((totalSeconds % 3600) / 60) + "m";
        }
    }

    private static final Map<UUID, FreezeData> frozenPlayers = new ConcurrentHashMap<>();

    public static boolean isFrozen(UUID uuid) {
        return frozenPlayers.containsKey(uuid);
    }

    public static FreezeData getFreezeData(UUID uuid) {
        return frozenPlayers.get(uuid);
    }

    /**
     * Freezes a player. Sends configurable messages to the player and admins.
     */
    public static void freezePlayer(ServerPlayer target, String adminName, String reason, long durationTicks, MinecraftServer server) {
        long currentTick = server.getTickCount();
        FreezeData data = new FreezeData(
                target.getUUID(), target.getGameProfile().getName(),
                adminName, reason, currentTick, durationTicks,
                target.position(), target.getYRot(), target.getXRot()
        );
        frozenPlayers.put(target.getUUID(), data);

        // Send freeze message to the player
        String msg = ConfigHandler.config.freezeMessageToPlayer.get()
                .replace("$reason", reason)
                .replace("$admin", adminName)
                .replace("$duration", data.getDurationString());
        // Handle \n in config strings for multi-line messages
        for (String line : msg.split("\\\\n|\\n")) {
            target.sendSystemMessage(TextFormatter.stringToFormattedText(line));
        }

        // Play sound
        if (ConfigHandler.config.freezePlaySound.get()) {
            target.playNotifySound(SoundEvents.ANVIL_LAND, SoundSource.MASTER, 1.0f, 0.5f);
        }

        // Notify admins
        String adminMsg = ConfigHandler.config.freezeAdminNotifyFormat.get()
                .replace("$player", target.getGameProfile().getName())
                .replace("$admin", adminName)
                .replace("$reason", reason)
                .replace("$duration", data.getDurationString());
        MutableComponent adminComp = TextFormatter.stringToFormattedText(adminMsg);
        for (ServerPlayer op : server.getPlayerList().getPlayers()) {
            if (PermissionsHandler.playerHasPermission(op.getUUID(), PermissionsHandler.freezeNotify)) {
                op.sendSystemMessage(adminComp);
            }
        }

        ServerEssentialsForge.LOGGER.info("[FREEZE] {} frozen by {} for {} - Reason: {}",
                target.getGameProfile().getName(), adminName, data.getDurationString(), reason);
    }

    /**
     * Unfreezes a player. Sends configurable messages.
     */
    public static void unfreezePlayer(UUID playerUUID, String adminName, MinecraftServer server) {
        FreezeData data = frozenPlayers.remove(playerUUID);
        if (data == null) return;

        ServerPlayer target = server.getPlayerList().getPlayer(playerUUID);
        if (target != null) {
            String msg = ConfigHandler.config.unfreezeMessageToPlayer.get()
                    .replace("$admin", adminName);
            target.sendSystemMessage(TextFormatter.stringToFormattedText(msg));
        }

        // Notify admins
        String adminMsg = ConfigHandler.config.unfreezeAdminNotifyFormat.get()
                .replace("$player", data.playerName)
                .replace("$admin", adminName);
        MutableComponent adminComp = TextFormatter.stringToFormattedText(adminMsg);
        for (ServerPlayer op : server.getPlayerList().getPlayers()) {
            if (PermissionsHandler.playerHasPermission(op.getUUID(), PermissionsHandler.freezeNotify)) {
                op.sendSystemMessage(adminComp);
            }
        }

        ServerEssentialsForge.LOGGER.info("[FREEZE] {} unfrozen by {}", data.playerName, adminName);
    }

    /**
     * Called every server tick. Handles:
     * - Expiring freezes
     * - Sending periodic reminders
     * - Teleporting frozen players back to their frozen position
     */
    public static void tick(MinecraftServer server) {
        long currentTick = server.getTickCount();
        int reminderInterval = ConfigHandler.config.freezeReminderIntervalSeconds.get() * 20;

        Iterator<Map.Entry<UUID, FreezeData>> it = frozenPlayers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, FreezeData> entry = it.next();
            FreezeData data = entry.getValue();

            // Check expiry
            if (data.isExpired(currentTick)) {
                it.remove();
                ServerPlayer target = server.getPlayerList().getPlayer(data.playerUUID);
                if (target != null) {
                    String msg = ConfigHandler.config.unfreezeMessageToPlayer.get()
                            .replace("$admin", "Server (timer expired)");
                    target.sendSystemMessage(TextFormatter.stringToFormattedText(msg));
                }
                // Notify admins
                String adminMsg = ConfigHandler.config.unfreezeAdminNotifyFormat.get()
                        .replace("$player", data.playerName)
                        .replace("$admin", "Server (timer expired)");
                MutableComponent adminComp = TextFormatter.stringToFormattedText(adminMsg);
                for (ServerPlayer op : server.getPlayerList().getPlayers()) {
                    if (PermissionsHandler.playerHasPermission(op.getUUID(), PermissionsHandler.freezeNotify)) {
                        op.sendSystemMessage(adminComp);
                    }
                }
                ServerEssentialsForge.LOGGER.info("[FREEZE] {} auto-unfrozen (timer expired)", data.playerName);
                continue;
            }

            ServerPlayer target = server.getPlayerList().getPlayer(data.playerUUID);
            if (target == null) continue;

            // Force position back — teleport every tick to prevent any movement
            target.teleportTo(target.serverLevel(),
                    data.frozenPos.x, data.frozenPos.y, data.frozenPos.z,
                    data.frozenYRot, data.frozenXRot);

            // Periodic reminder
            if (reminderInterval > 0 && currentTick - data.lastReminderTick >= reminderInterval) {
                data.lastReminderTick = currentTick;
                String reminder = ConfigHandler.config.freezeReminderFormat.get()
                        .replace("$reason", data.reason)
                        .replace("$admin", data.adminName);
                target.sendSystemMessage(TextFormatter.stringToFormattedText(reminder));
            }
        }
    }

    /**
     * Called on server stop — clear all freeze data.
     */
    public static void clear() {
        frozenPlayers.clear();
    }

    /**
     * Returns all currently frozen players (for /freezelist if needed).
     */
    public static Collection<FreezeData> getAllFrozen() {
        return Collections.unmodifiableCollection(frozenPlayers.values());
    }

    /**
     * Parses a duration string like "30s", "5m", "1h", "infinite" into ticks.
     * Returns -1 for infinite.
     */
    public static long parseDuration(String input) {
        if (input == null || input.isEmpty()) return -1;
        String lower = input.trim().toLowerCase();
        if (lower.equals("infinite") || lower.equals("inf") || lower.equals("forever") || lower.equals("perm")) {
            return -1;
        }
        try {
            if (lower.endsWith("s")) {
                return Long.parseLong(lower.substring(0, lower.length() - 1)) * 20;
            } else if (lower.endsWith("m")) {
                return Long.parseLong(lower.substring(0, lower.length() - 1)) * 20 * 60;
            } else if (lower.endsWith("h")) {
                return Long.parseLong(lower.substring(0, lower.length() - 1)) * 20 * 3600;
            } else {
                // Assume seconds if no suffix
                return Long.parseLong(lower) * 20;
            }
        } catch (NumberFormatException e) {
            return -1; // Treat unparseable as infinite
        }
    }
}

