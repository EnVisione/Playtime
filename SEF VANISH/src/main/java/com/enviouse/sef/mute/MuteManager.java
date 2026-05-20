package com.enviouse.sef.mute;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.enviouse.sef.ServerEssentialsForge;
import com.enviouse.sef.TextFormatter;
import com.enviouse.sef.config.ConfigHandler;
import com.enviouse.sef.config.PermissionsHandler;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages persistent player mutes.
 * <p>
 * Mute data is saved to {@code <world>/serverconfig/sef/mutes.json} and
 * survives both player disconnects and full server restarts.
 * <p>
 * Duration is measured in <b>server ticks</b>.  The remaining-ticks counter is
 * decremented every server tick via {@link #tick(MinecraftServer)}, so time
 * only passes while the server is actually running.  It does <b>not</b>
 * matter whether the muted player is online or offline – the countdown still
 * proceeds.
 * <p>
 * A mute is removed only when:
 * <ol>
 *   <li>An admin runs {@code /unmute}</li>
 *   <li>The remaining tick counter reaches zero</li>
 * </ol>
 */
public class MuteManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type DATA_TYPE = new TypeToken<Map<String, MuteEntry>>() {}.getType();

    /** Map of player UUID string → mute entry */
    private final Map<String, MuteEntry> mutes = new ConcurrentHashMap<>();
    private Path filePath;

    // ── Data class ──────────────────────────────────────────────────────────
    public static class MuteEntry {
        /** UUID of the muted player (string form) */
        public String playerUUID;
        /** Display name at time of mute */
        public String playerName;
        /** Admin who muted them */
        public String adminName;
        /** Reason for the mute */
        public String reason;
        /** Remaining ticks until unmute.  -1 = infinite / permanent. */
        public long remainingTicks;
        /** Original duration in ticks (for display purposes). -1 = infinite */
        public long originalDurationTicks;
        /** Timestamp of when the mute was issued (epoch millis, purely informational) */
        public long mutedAtMillis;

        public MuteEntry() {} // for GSON

        public MuteEntry(String playerUUID, String playerName, String adminName,
                         String reason, long durationTicks) {
            this.playerUUID = playerUUID;
            this.playerName = playerName;
            this.adminName = adminName;
            this.reason = reason;
            this.remainingTicks = durationTicks;
            this.originalDurationTicks = durationTicks;
            this.mutedAtMillis = System.currentTimeMillis();
        }

        public boolean isPermanent() {
            return remainingTicks < 0;
        }

        public boolean isExpired() {
            return !isPermanent() && remainingTicks <= 0;
        }

        public String getDurationString(long ticks) {
            if (ticks < 0) return "Permanent";
            long totalSeconds = ticks / 20;
            if (totalSeconds <= 0) return "0s";
            long h = totalSeconds / 3600;
            long m = (totalSeconds % 3600) / 60;
            long s = totalSeconds % 60;
            StringBuilder sb = new StringBuilder();
            if (h > 0) sb.append(h).append("h ");
            if (m > 0) sb.append(m).append("m ");
            if (s > 0 || sb.length() == 0) sb.append(s).append("s");
            return sb.toString().trim();
        }

        public String getRemainingString() {
            return getDurationString(remainingTicks);
        }

        public String getOriginalDurationString() {
            return getDurationString(originalDurationTicks);
        }
    }

    // ── Load / Save ─────────────────────────────────────────────────────────
    public void load(MinecraftServer server) {
        filePath = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("serverconfig").resolve("sef").resolve("mutes.json");
        if (!Files.exists(filePath)) {
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(filePath)) {
            Map<String, MuteEntry> loaded = GSON.fromJson(reader, DATA_TYPE);
            if (loaded != null) {
                mutes.clear();
                mutes.putAll(loaded);
            }
            ServerEssentialsForge.LOGGER.info("[SEF] Loaded {} persistent mute(s)", mutes.size());
        } catch (Exception e) {
            ServerEssentialsForge.LOGGER.error("[SEF] Failed to load mutes", e);
        }
    }

    public void save() {
        if (filePath == null) return;
        try {
            Files.createDirectories(filePath.getParent());
            try (Writer writer = Files.newBufferedWriter(filePath)) {
                GSON.toJson(mutes, writer);
            }
        } catch (IOException e) {
            ServerEssentialsForge.LOGGER.error("[SEF] Failed to save mutes", e);
        }
    }

    // ── Core API ────────────────────────────────────────────────────────────

    /**
     * Mute a player.
     *
     * @param target       the player being muted
     * @param adminName    who issued the mute
     * @param reason       reason for the mute
     * @param durationTicks duration in server ticks (-1 = permanent)
     * @param server       the server instance
     */
    public void mutePlayer(ServerPlayer target, String adminName, String reason,
                           long durationTicks, MinecraftServer server) {
        String uuid = target.getUUID().toString();
        MuteEntry entry = new MuteEntry(uuid, target.getGameProfile().getName(),
                adminName, reason, durationTicks);
        mutes.put(uuid, entry);
        save();

        // Notify the muted player
        String playerMsg = ConfigHandler.config.muteNotifyPlayerFormat.get()
                .replace("$admin", adminName)
                .replace("$reason", reason)
                .replace("$duration", entry.getOriginalDurationString());
        for (String line : playerMsg.split("\\\\n|\\n")) {
            target.sendSystemMessage(TextFormatter.stringToFormattedText(line));
        }

        // Notify admins
        String adminMsg = ConfigHandler.config.muteAdminNotifyFormat.get()
                .replace("$player", target.getGameProfile().getName())
                .replace("$admin", adminName)
                .replace("$reason", reason)
                .replace("$duration", entry.getOriginalDurationString());
        MutableComponent adminComp = TextFormatter.stringToFormattedText(adminMsg);
        for (ServerPlayer op : server.getPlayerList().getPlayers()) {
            if (PermissionsHandler.playerHasPermission(op.getUUID(), PermissionsHandler.muteNotify)) {
                op.sendSystemMessage(adminComp);
            }
        }

        ServerEssentialsForge.LOGGER.info("[MUTE] {} muted by {} for {} - Reason: {}",
                target.getGameProfile().getName(), adminName, entry.getOriginalDurationString(), reason);
    }

    /**
     * Unmute a player by UUID.
     *
     * @return the removed MuteEntry, or null if the player was not muted
     */
    public MuteEntry unmutePlayer(UUID playerUUID, String adminName, MinecraftServer server) {
        MuteEntry entry = mutes.remove(playerUUID.toString());
        if (entry == null) return null;
        save();

        ServerPlayer target = server.getPlayerList().getPlayer(playerUUID);
        if (target != null) {
            String msg = ConfigHandler.config.unmuteNotifyPlayerFormat.get()
                    .replace("$admin", adminName);
            target.sendSystemMessage(TextFormatter.stringToFormattedText(msg));
        }

        // Notify admins
        String adminMsg = ConfigHandler.config.unmuteAdminNotifyFormat.get()
                .replace("$player", entry.playerName)
                .replace("$admin", adminName);
        MutableComponent adminComp = TextFormatter.stringToFormattedText(adminMsg);
        for (ServerPlayer op : server.getPlayerList().getPlayers()) {
            if (PermissionsHandler.playerHasPermission(op.getUUID(), PermissionsHandler.muteNotify)) {
                op.sendSystemMessage(adminComp);
            }
        }

        ServerEssentialsForge.LOGGER.info("[MUTE] {} unmuted by {}", entry.playerName, adminName);
        return entry;
    }

    /**
     * Returns true if the given player UUID is currently muted (entry exists
     * and has not expired).
     */
    public boolean isMuted(UUID playerUUID) {
        MuteEntry entry = mutes.get(playerUUID.toString());
        if (entry == null) return false;
        if (entry.isExpired()) return false; // will be cleaned up on next tick
        return true;
    }

    /**
     * Get the mute entry for a player, or null if not muted.
     */
    public MuteEntry getMuteEntry(UUID playerUUID) {
        return mutes.get(playerUUID.toString());
    }

    /**
     * Called every server tick.  Decrements remaining ticks for all
     * non-permanent mutes and removes expired entries.
     */
    public void tick(MinecraftServer server) {
        boolean changed = false;
        Iterator<Map.Entry<String, MuteEntry>> it = mutes.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, MuteEntry> mapEntry = it.next();
            MuteEntry entry = mapEntry.getValue();

            // Skip permanent mutes
            if (entry.isPermanent()) continue;

            // Decrement
            entry.remainingTicks--;

            // Check expiry
            if (entry.remainingTicks <= 0) {
                it.remove();
                changed = true;

                // Notify the player if online
                ServerPlayer target = server.getPlayerList()
                        .getPlayer(UUID.fromString(entry.playerUUID));
                if (target != null) {
                    String msg = ConfigHandler.config.unmuteNotifyPlayerFormat.get()
                            .replace("$admin", "Server (mute expired)");
                    target.sendSystemMessage(TextFormatter.stringToFormattedText(msg));
                }

                // Notify admins
                String adminMsg = ConfigHandler.config.unmuteAdminNotifyFormat.get()
                        .replace("$player", entry.playerName)
                        .replace("$admin", "Server (mute expired)");
                MutableComponent adminComp = TextFormatter.stringToFormattedText(adminMsg);
                for (ServerPlayer op : server.getPlayerList().getPlayers()) {
                    if (PermissionsHandler.playerHasPermission(op.getUUID(), PermissionsHandler.muteNotify)) {
                        op.sendSystemMessage(adminComp);
                    }
                }

                ServerEssentialsForge.LOGGER.info("[MUTE] {} auto-unmuted (mute expired)", entry.playerName);
            }
        }

        // Periodic save every 6000 ticks (5 minutes) to persist countdown progress
        if (server.getTickCount() % 6000 == 0 && !mutes.isEmpty()) {
            save();
        }

        if (changed) {
            save();
        }
    }

    /**
     * Returns all current mutes (for admin listing).
     */
    public Collection<MuteEntry> getAllMutes() {
        return Collections.unmodifiableCollection(mutes.values());
    }

    /**
     * Called on server stop — save final state (do NOT clear, data persists).
     */
    public void shutdown() {
        save();
    }

    // ── Duration parser ─────────────────────────────────────────────────────

    /**
     * Parses a duration string like "30s", "5m", "1h", "1d", "infinite"
     * into ticks.  Returns -1 for permanent/infinite.
     */
    public static long parseDuration(String input) {
        if (input == null || input.isEmpty()) return -1;
        String lower = input.trim().toLowerCase();
        if (lower.equals("infinite") || lower.equals("inf") || lower.equals("forever")
                || lower.equals("perm") || lower.equals("permanent")) {
            return -1;
        }
        try {
            if (lower.endsWith("s")) {
                return Long.parseLong(lower.substring(0, lower.length() - 1)) * 20;
            } else if (lower.endsWith("m")) {
                return Long.parseLong(lower.substring(0, lower.length() - 1)) * 20 * 60;
            } else if (lower.endsWith("h")) {
                return Long.parseLong(lower.substring(0, lower.length() - 1)) * 20 * 3600;
            } else if (lower.endsWith("d")) {
                return Long.parseLong(lower.substring(0, lower.length() - 1)) * 20 * 86400;
            } else {
                // Assume seconds if no suffix
                return Long.parseLong(lower) * 20;
            }
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}

