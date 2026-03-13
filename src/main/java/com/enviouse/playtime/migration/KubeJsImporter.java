package com.enviouse.playtime.migration;

import com.enviouse.playtime.data.PlayerDataRepository;
import com.enviouse.playtime.data.PlayerRecord;
import com.enviouse.playtime.service.RankEngine;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

/**
 * Imports player data from the old KubeJS playtime_data.json format.
 * <p>
 * Old format is a JSON object keyed by player NAME with values:
 * <pre>
 * {
 *   "PlayerName": {
 *     "totalPlaytime": 123456,
 *     "firstJoin": 1700000000000,
 *     "lastSeen": 1700000000000,
 *     "uuid": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
 *     "currentRank": "Beginner"
 *   }
 * }
 * </pre>
 * Entries with placeholder UUID ("--" or empty) are skipped.
 */
public class KubeJsImporter {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().create();
    private static final String UUID_PLACEHOLDER = "--";

    /**
     * Import from a file path. Returns number of records imported.
     *
     * @throws Exception on I/O or parse failure
     */
    public static int importFromFile(String filepath, PlayerDataRepository repository,
                                      RankEngine rankEngine, MinecraftServer server) throws Exception {
        Path path = Paths.get(filepath);
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("File not found: " + filepath);
        }

        JsonObject root;
        try (Reader reader = Files.newBufferedReader(path)) {
            root = GSON.fromJson(reader, JsonObject.class);
        }

        if (root == null || root.size() == 0) {
            throw new IllegalArgumentException("File is empty or not a valid JSON object.");
        }

        int imported = 0;
        int skipped = 0;

        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            String playerName = entry.getKey();
            JsonObject data = entry.getValue().getAsJsonObject();

            // Get UUID
            String uuidStr = getStringOrDefault(data, "uuid", UUID_PLACEHOLDER);
            if (uuidStr.equals(UUID_PLACEHOLDER) || uuidStr.isEmpty()) {
                LOGGER.warn("[Playtime Import] Skipping '{}' — no valid UUID.", playerName);
                skipped++;
                continue;
            }

            UUID uuid;
            try {
                uuid = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException e) {
                LOGGER.warn("[Playtime Import] Skipping '{}' — invalid UUID: {}", playerName, uuidStr);
                skipped++;
                continue;
            }

            // Check if already exists
            if (repository.getPlayer(uuid) != null) {
                LOGGER.info("[Playtime Import] Skipping '{}' — already exists in database.", playerName);
                skipped++;
                continue;
            }

            // Build record
            PlayerRecord record = new PlayerRecord(uuid, playerName);
            record.setTotalPlaytimeTicks(getLongOrDefault(data, "totalPlaytime", 0));
            record.setFirstJoinEpochMs(getLongOrDefault(data, "firstJoin", System.currentTimeMillis()));
            record.setLastSeenEpochMs(getLongOrDefault(data, "lastSeen", System.currentTimeMillis()));

            // Map old rank name to new rank id (lowercase)
            String oldRank = getStringOrDefault(data, "currentRank", "beginner");
            record.setCurrentRankId(oldRank.toLowerCase());

            repository.putPlayer(record);

            // Recalculate rank from actual ticks
            rankEngine.checkAndApplyProgression(server, uuid, record.getTotalPlaytimeTicks());

            imported++;
        }

        repository.save(true);
        LOGGER.info("[Playtime Import] Imported {} records, skipped {}.", imported, skipped);
        return imported;
    }

    private static String getStringOrDefault(JsonObject obj, String key, String defaultValue) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return defaultValue;
    }

    private static long getLongOrDefault(JsonObject obj, String key, long defaultValue) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsLong();
        }
        return defaultValue;
    }
}

