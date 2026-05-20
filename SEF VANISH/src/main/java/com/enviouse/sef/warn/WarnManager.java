package com.enviouse.sef.warn;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.enviouse.sef.ServerEssentialsForge;
import net.minecraft.server.MinecraftServer;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/**
 * Manages player warnings with persistence.
 * Data is saved to <world>/serverconfig/sef/warns.json.
 *
 * Each player can have multiple warnings, each with an optional expiration duration.
 * Expired warnings are not removed — they display as "(expired)" when checked.
 */
public class WarnManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type DATA_TYPE = new TypeToken<Map<String, List<WarnEntry>>>(){}.getType();

    /** Map of player UUID string -> list of warnings */
    private final Map<String, List<WarnEntry>> warns = new HashMap<>();
    private Path filePath;

    public static class WarnEntry {
        public int id;
        public String reason;
        public String adminName;
        public String adminUuid;
        public String timestamp;
        /** Duration in milliseconds, -1 = permanent (never expires) */
        public long durationMs;
        /** Whether this warn has been explicitly removed (soft delete) */
        public boolean removed;

        public WarnEntry() {}

        public WarnEntry(int id, String reason, String adminName, String adminUuid, String timestamp, long durationMs) {
            this.id = id;
            this.reason = reason;
            this.adminName = adminName;
            this.adminUuid = adminUuid;
            this.timestamp = timestamp;
            this.durationMs = durationMs;
            this.removed = false;
        }

        public boolean isExpired() {
            if (durationMs < 0) return false; // permanent
            try {
                Instant issued = Instant.parse(timestamp);
                return Instant.now().isAfter(issued.plusMillis(durationMs));
            } catch (Exception e) {
                return false;
            }
        }

        public String getDurationString() {
            if (durationMs < 0) return "Permanent";
            long totalSeconds = durationMs / 1000;
            if (totalSeconds < 60) return totalSeconds + "s";
            if (totalSeconds < 3600) return (totalSeconds / 60) + "m " + (totalSeconds % 60) + "s";
            if (totalSeconds < 86400) return (totalSeconds / 3600) + "h " + ((totalSeconds % 3600) / 60) + "m";
            long days = totalSeconds / 86400;
            return days + "d " + ((totalSeconds % 86400) / 3600) + "h";
        }
    }

    public void load(MinecraftServer server) {
        Path dir = server.getServerDirectory().toPath().resolve("serverconfig").resolve("sef");
        filePath = dir.resolve("warns.json");
        warns.clear();
        if (!Files.exists(filePath)) return;
        try (Reader reader = Files.newBufferedReader(filePath)) {
            Map<String, List<WarnEntry>> loaded = GSON.fromJson(reader, DATA_TYPE);
            if (loaded != null) warns.putAll(loaded);
            int total = warns.values().stream().mapToInt(List::size).sum();
            ServerEssentialsForge.LOGGER.info("[SEF] Loaded {} warning(s) for {} player(s)", total, warns.size());
        } catch (Exception e) {
            ServerEssentialsForge.LOGGER.error("[SEF] Failed to load warns", e);
        }
    }

    public void save() {
        if (filePath == null) return;
        try {
            Files.createDirectories(filePath.getParent());
            try (Writer writer = Files.newBufferedWriter(filePath)) {
                GSON.toJson(warns, writer);
            }
        } catch (IOException e) {
            ServerEssentialsForge.LOGGER.error("[SEF] Failed to save warns", e);
        }
    }

    /**
     * Adds a warning for a player.
     * @return the new WarnEntry
     */
    public WarnEntry addWarn(UUID playerUUID, String reason, String adminName, String adminUuid, long durationMs) {
        String key = playerUUID.toString();
        List<WarnEntry> list = warns.computeIfAbsent(key, k -> new ArrayList<>());

        // Generate next ID (max existing + 1)
        int nextId = list.stream().mapToInt(w -> w.id).max().orElse(0) + 1;

        WarnEntry entry = new WarnEntry(nextId, reason, adminName, adminUuid, Instant.now().toString(), durationMs);
        list.add(entry);
        save();
        return entry;
    }

    /**
     * Removes (soft-deletes) a warning by ID.
     * @return true if found and removed
     */
    public boolean removeWarn(UUID playerUUID, int warnId) {
        String key = playerUUID.toString();
        List<WarnEntry> list = warns.get(key);
        if (list == null) return false;
        for (WarnEntry entry : list) {
            if (entry.id == warnId && !entry.removed) {
                entry.removed = true;
                save();
                return true;
            }
        }
        return false;
    }

    /**
     * Gets all warnings for a player (including expired, excluding removed).
     */
    public List<WarnEntry> getWarns(UUID playerUUID) {
        String key = playerUUID.toString();
        List<WarnEntry> list = warns.getOrDefault(key, Collections.emptyList());
        List<WarnEntry> result = new ArrayList<>();
        for (WarnEntry entry : list) {
            if (!entry.removed) result.add(entry);
        }
        return result;
    }

    /**
     * Gets only active (non-expired, non-removed) warnings for a player.
     */
    public List<WarnEntry> getActiveWarns(UUID playerUUID) {
        List<WarnEntry> all = getWarns(playerUUID);
        List<WarnEntry> active = new ArrayList<>();
        for (WarnEntry entry : all) {
            if (!entry.isExpired()) active.add(entry);
        }
        return active;
    }

    /**
     * Parses a duration string like "30s", "5m", "1h", "7d", "permanent" into milliseconds.
     * Returns -1 for permanent.
     */
    public static long parseDuration(String input) {
        if (input == null || input.isEmpty()) return -1;
        String lower = input.trim().toLowerCase();
        if (lower.equals("permanent") || lower.equals("perm") || lower.equals("forever") || lower.equals("inf")) {
            return -1;
        }
        try {
            if (lower.endsWith("s")) {
                return Long.parseLong(lower.substring(0, lower.length() - 1)) * 1000;
            } else if (lower.endsWith("m")) {
                return Long.parseLong(lower.substring(0, lower.length() - 1)) * 1000 * 60;
            } else if (lower.endsWith("h")) {
                return Long.parseLong(lower.substring(0, lower.length() - 1)) * 1000 * 3600;
            } else if (lower.endsWith("d")) {
                return Long.parseLong(lower.substring(0, lower.length() - 1)) * 1000 * 86400;
            } else {
                // Assume seconds if no suffix
                return Long.parseLong(lower) * 1000;
            }
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}

