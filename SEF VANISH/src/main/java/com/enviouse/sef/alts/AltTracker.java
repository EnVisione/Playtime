package com.enviouse.sef.alts;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.enviouse.sef.ServerEssentialsForge;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/**
 * Tracks player logins by IP address for alt-account detection.
 * Persists data to <world>/serverconfig/sef/alt_data.json.
 *
 * Data structure:
 *   Map<String(IP), List<AltEntry>>
 *
 * Each AltEntry records a UUID, name, and last-seen timestamp.
 */
public class AltTracker {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type DATA_TYPE = new TypeToken<Map<String, List<AltEntry>>>(){}.getType();

    /** Map of IP address -> list of known accounts from that IP */
    private final Map<String, List<AltEntry>> ipMap = new HashMap<>();
    /** Reverse map: UUID -> IP for quick lookup */
    private final Map<UUID, String> uuidToIp = new HashMap<>();
    private Path filePath;

    public static class AltEntry {
        public String uuid;
        public String name;
        public String lastSeen;

        public AltEntry() {}
        public AltEntry(String uuid, String name, String lastSeen) {
            this.uuid = uuid;
            this.name = name;
            this.lastSeen = lastSeen;
        }
    }

    public void load(MinecraftServer server) {
        Path dir = server.getServerDirectory().toPath().resolve("serverconfig").resolve("sef");
        filePath = dir.resolve("alt_data.json");
        ipMap.clear();
        uuidToIp.clear();
        if (!Files.exists(filePath)) return;
        try (Reader reader = Files.newBufferedReader(filePath)) {
            Map<String, List<AltEntry>> loaded = GSON.fromJson(reader, DATA_TYPE);
            if (loaded != null) {
                ipMap.putAll(loaded);
                // Build reverse map
                for (Map.Entry<String, List<AltEntry>> entry : ipMap.entrySet()) {
                    for (AltEntry alt : entry.getValue()) {
                        try {
                            uuidToIp.put(UUID.fromString(alt.uuid), entry.getKey());
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
            }
            ServerEssentialsForge.LOGGER.info("[SEF] Loaded alt data for {} IP(s)", ipMap.size());
        } catch (Exception e) {
            ServerEssentialsForge.LOGGER.error("[SEF] Failed to load alt data", e);
        }
    }

    public void save() {
        if (filePath == null) return;
        try {
            Files.createDirectories(filePath.getParent());
            try (Writer writer = Files.newBufferedWriter(filePath)) {
                GSON.toJson(ipMap, writer);
            }
        } catch (IOException e) {
            ServerEssentialsForge.LOGGER.error("[SEF] Failed to save alt data", e);
        }
    }

    /**
     * Records a player login. Call this from PlayerLoggedInEvent.
     * Skips local/LAN IPs to avoid false positives.
     */
    public void recordLogin(ServerPlayer player) {
        String ip = player.getIpAddress();
        if (ip == null || ip.equals("local") || ip.equals("127.0.0.1") || ip.equals("0:0:0:0:0:0:0:1")) {
            return; // Skip local connections
        }

        String uuid = player.getUUID().toString();
        String name = player.getGameProfile().getName();
        String now = Instant.now().toString();

        List<AltEntry> entries = ipMap.computeIfAbsent(ip, k -> new ArrayList<>());

        // Update existing entry or add new one
        boolean found = false;
        for (AltEntry entry : entries) {
            if (entry.uuid.equals(uuid)) {
                entry.name = name; // Update name (may have changed)
                entry.lastSeen = now;
                found = true;
                break;
            }
        }
        if (!found) {
            entries.add(new AltEntry(uuid, name, now));
        }

        uuidToIp.put(player.getUUID(), ip);
        save();
    }

    /**
     * Gets all alt entries for the given player's IP.
     * @return List of AltEntry for accounts sharing the same IP, or empty if none.
     */
    public List<AltEntry> getAltsForPlayer(UUID playerUUID) {
        String ip = uuidToIp.get(playerUUID);
        if (ip == null) return Collections.emptyList();
        List<AltEntry> all = ipMap.getOrDefault(ip, Collections.emptyList());
        // Return all entries (including the player themselves for context)
        return Collections.unmodifiableList(all);
    }

    /**
     * Gets the IP address for a given player UUID.
     */
    public String getIpForPlayer(UUID playerUUID) {
        return uuidToIp.get(playerUUID);
    }

    /**
     * Gets all alt entries for a specific IP address.
     */
    public List<AltEntry> getAltsForIp(String ip) {
        return ipMap.getOrDefault(ip, Collections.emptyList());
    }
}

