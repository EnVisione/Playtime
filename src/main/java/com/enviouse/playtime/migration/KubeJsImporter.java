package com.enviouse.playtime.migration;

import com.enviouse.playtime.data.JsonPlayerDataRepository;
import com.enviouse.playtime.data.PlayerRecord;
import com.enviouse.playtime.data.RankDefinition;
import com.enviouse.playtime.service.RankEngine;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

/**
 * Imports player data from the old KubeJS playtime_data.json format.
 * <p>
 * Reads from {@code <world>/playtime/imports.json}. Old format is a JSON object
 * keyed by player NAME with values:
 * <pre>
 * {
 *   "PlayerName": {
 *     "totalPlaytime": 9046380,
 *     "firstJoin": 1767469993127,
 *     "lastSeen": 1772482403837,
 *     "uuid": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
 *     "currentRank": "Cosmonaut"
 *   }
 * }
 * </pre>
 * <p>
 * Only {@code totalPlaytime} (ticks), {@code firstJoin}, {@code lastSeen}, {@code uuid},
 * and the player name (JSON key) are imported. The old {@code currentRank} is <b>ignored</b>
 * — ranks are recalculated from {@code totalPlaytime} using the current rank definitions.
 * <p>
 * Entries with placeholder UUID ({@code "--"} or empty) are skipped.
 * After a successful import the file is renamed to {@code imports.json.imported}
 * and a full set of backup files is created.
 */
public class KubeJsImporter {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String UUID_PLACEHOLDER = "--";
    public static final String IMPORT_FILE = "imports.json";
    private static final String IMPORT_FILE_DONE = "imports.json.imported";

    /**
     * Check for {@code <world>/playtime/imports.json} and auto-import if present.
     * Called during server start after all services are initialised.
     *
     * @return number of records imported, or -1 if no import file was found
     */
    public static int autoImport(Path worldDir, JsonPlayerDataRepository repository,
                                  RankEngine rankEngine, MinecraftServer server) {
        Path playtimeDir = worldDir.resolve("playtime");
        Path importFile = playtimeDir.resolve(IMPORT_FILE);

        if (!Files.exists(importFile)) {
            return -1; // no import file — nothing to do
        }

        LOGGER.info("[Playtime Import] Found {}. Starting automatic import...", IMPORT_FILE);
        try {
            int imported = doImport(importFile, repository, rankEngine);

            // Save all player data
            repository.save(true);

            // Create backup files
            createImportBackups(playtimeDir, repository);

            // Rename imports.json → imports.json.imported so it doesn't re-run
            Path doneFile = playtimeDir.resolve(IMPORT_FILE_DONE);
            Files.move(importFile, doneFile, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("[Playtime Import] Renamed {} → {}. Import complete.", IMPORT_FILE, IMPORT_FILE_DONE);

            return imported;
        } catch (Exception e) {
            LOGGER.error("[Playtime Import] Auto-import failed!", e);
            return 0;
        }
    }

    /**
     * Import from the fixed imports.json location (for the admin command).
     * Returns number of records imported.
     *
     * @throws Exception on I/O or parse failure
     */
    public static int importFromFile(Path worldDir, JsonPlayerDataRepository repository,
                                      RankEngine rankEngine) throws Exception {
        Path playtimeDir = worldDir.resolve("playtime");
        Path importFile = playtimeDir.resolve(IMPORT_FILE);
        if (!Files.exists(importFile)) {
            throw new IllegalArgumentException("No imports.json found. Place your KubeJS data at: " +
                    importFile.toAbsolutePath());
        }

        int imported = doImport(importFile, repository, rankEngine);

        // Save all player data
        repository.save(true);

        // Create backup files
        createImportBackups(playtimeDir, repository);

        // Rename imports.json → imports.json.imported
        Path doneFile = playtimeDir.resolve(IMPORT_FILE_DONE);
        Files.move(importFile, doneFile, StandardCopyOption.REPLACE_EXISTING);
        LOGGER.info("[Playtime Import] Renamed {} → {}.", IMPORT_FILE, IMPORT_FILE_DONE);

        return imported;
    }

    // ── Internal ───────────────────────────────────────────────────────────────

    private static int doImport(Path importFile, JsonPlayerDataRepository repository,
                                 RankEngine rankEngine) throws Exception {
        JsonObject root;
        try (Reader reader = Files.newBufferedReader(importFile)) {
            root = GSON.fromJson(reader, JsonObject.class);
        }

        if (root == null || root.size() == 0) {
            throw new IllegalArgumentException("Import file is empty or not a valid JSON object.");
        }

        int imported = 0;
        int skipped = 0;

        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            String playerName = entry.getKey();
            JsonObject data = entry.getValue().getAsJsonObject();

            // ── UUID validation ──────────────────────────────────────────────────
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

            // Skip duplicates
            if (repository.getPlayer(uuid) != null) {
                LOGGER.info("[Playtime Import] Skipping '{}' — already exists in database.", playerName);
                skipped++;
                continue;
            }

            // ── Build record — import ONLY: name, uuid, totalPlaytime, firstJoin, lastSeen ──
            PlayerRecord record = new PlayerRecord(uuid, playerName);

            long totalTicks = getLongOrDefault(data, "totalPlaytime", 0);
            record.setTotalPlaytimeTicks(totalTicks);
            record.setFirstJoinEpochMs(getLongOrDefault(data, "firstJoin", System.currentTimeMillis()));
            record.setLastSeenEpochMs(getLongOrDefault(data, "lastSeen", System.currentTimeMillis()));

            // Rank: recalculate from playtime ticks — old currentRank is IGNORED
            RankDefinition earned = rankEngine.getCurrentRank(totalTicks);
            record.setCurrentRankId(earned.getId());

            // displayRank starts empty
            record.setDisplayRank("");

            repository.putPlayer(record);

            LOGGER.debug("[Playtime Import] Imported '{}' — {} ticks → rank '{}'",
                    playerName, totalTicks, earned.getId());
            imported++;
        }

        LOGGER.info("[Playtime Import] Imported {} records, skipped {}.", imported, skipped);
        return imported;
    }

    /**
     * Create the full set of backup files after a successful import:
     * backup_timestamps.json, backup-hourly.json, backup-daily.json,
     * backup-weekly.json, and playtime_data.json.
     */
    private static void createImportBackups(Path playtimeDir, JsonPlayerDataRepository repository) {
        Path backupsDir = playtimeDir.resolve("backups");
        Path playersFile = repository.getFilePath();

        if (!Files.exists(playersFile)) {
            LOGGER.warn("[Playtime Import] No players.json to back up — skipping backup creation.");
            return;
        }

        try {
            Files.createDirectories(backupsDir);

            // Copy players.json → each backup slot
            Files.copy(playersFile, backupsDir.resolve("backup-hourly.json"), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(playersFile, backupsDir.resolve("backup-daily.json"), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(playersFile, backupsDir.resolve("backup-weekly.json"), StandardCopyOption.REPLACE_EXISTING);

            // playtime_data.json — reference copy in the playtime folder
            Files.copy(playersFile, playtimeDir.resolve("playtime_data.json"), StandardCopyOption.REPLACE_EXISTING);

            // Write backup_timestamps.json with current time
            long now = System.currentTimeMillis();
            Type tsType = new TypeToken<Map<String, Long>>() {}.getType();
            Map<String, Long> timestamps = Map.of(
                    "lastHourly", now,
                    "lastDaily", now,
                    "lastWeekly", now
            );
            try (Writer writer = Files.newBufferedWriter(playtimeDir.resolve("backup_timestamps.json"))) {
                GSON.toJson(timestamps, tsType, writer);
            }

            LOGGER.info("[Playtime Import] Created backup files: backup-hourly.json, backup-daily.json, " +
                    "backup-weekly.json, playtime_data.json, backup_timestamps.json");
        } catch (Exception e) {
            LOGGER.error("[Playtime Import] Failed to create backup files.", e);
        }
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
