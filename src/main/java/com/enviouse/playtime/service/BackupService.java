package com.enviouse.playtime.service;

import com.enviouse.playtime.Config;
import com.enviouse.playtime.data.JsonPlayerDataRepository;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/**
 * Rotating backup system: hourly, daily, weekly.
 * Copies the current players.json to dated backup files.
 * Timestamps are persisted to survive server restarts.
 */
public class BackupService {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TS_TYPE = new TypeToken<Map<String, Long>>() {}.getType();

    private static final long HOUR_MS = 60 * 60 * 1000L;
    private static final long DAY_MS  = 24 * HOUR_MS;
    private static final long WEEK_MS =  7 * DAY_MS;

    private final Path backupDir;
    private final Path timestampsFile;
    private final JsonPlayerDataRepository repository;

    private long lastHourly = 0;
    private long lastDaily  = 0;
    private long lastWeekly = 0;

    public BackupService(Path worldDir, JsonPlayerDataRepository repository) {
        this.backupDir = worldDir.resolve("playtime").resolve("backups");
        this.timestampsFile = worldDir.resolve("playtime").resolve("backup_timestamps.json");
        this.repository = repository;
    }

    /** Initialize: load timestamps and run an immediate check. */
    public void initialize() {
        loadTimestamps();
        tick();
    }

    /** Called periodically from server tick (at Config.backupCheckIntervalTicks). */
    public void tick() {
        if (!Config.backupEnabled) return;
        if (!repository.isLoaded()) return;

        boolean madeBackup = false;
        long now = System.currentTimeMillis();

        if (now - lastHourly >= HOUR_MS) {
            if (createBackup("backup-hourly.json")) {
                lastHourly = now;
                madeBackup = true;
            }
        }

        if (now - lastDaily >= DAY_MS) {
            if (createBackup("backup-daily.json")) {
                lastDaily = now;
                madeBackup = true;
            }
        }

        if (now - lastWeekly >= WEEK_MS) {
            if (createBackup("backup-weekly.json")) {
                lastWeekly = now;
                madeBackup = true;
            }
        }

        if (madeBackup) {
            saveTimestamps();
        }
    }

    /** Force a backup now (admin command). */
    public boolean backupNow() {
        return createBackup("backup-manual-" + System.currentTimeMillis() + ".json");
    }

    // ── Internal ───────────────────────────────────────────────────────────────

    private boolean createBackup(String filename) {
        Path source = repository.getFilePath();
        if (!Files.exists(source)) return false;

        try {
            Files.createDirectories(backupDir);
            Path target = backupDir.resolve(filename);
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("[Playtime] Created backup: {}", filename);
            return true;
        } catch (IOException e) {
            LOGGER.error("[Playtime] Backup failed for {}", filename, e);
            return false;
        }
    }

    private void loadTimestamps() {
        if (!Files.exists(timestampsFile)) return;
        try (Reader reader = Files.newBufferedReader(timestampsFile)) {
            Map<String, Long> data = GSON.fromJson(reader, TS_TYPE);
            if (data != null) {
                lastHourly = data.getOrDefault("lastHourly", 0L);
                lastDaily  = data.getOrDefault("lastDaily", 0L);
                lastWeekly = data.getOrDefault("lastWeekly", 0L);
            }
            LOGGER.info("[Playtime] Loaded backup timestamps.");
        } catch (Exception e) {
            LOGGER.warn("[Playtime] Could not load backup timestamps, starting fresh.", e);
        }
    }

    private void saveTimestamps() {
        try {
            Files.createDirectories(timestampsFile.getParent());
            Map<String, Long> data = Map.of(
                    "lastHourly", lastHourly,
                    "lastDaily", lastDaily,
                    "lastWeekly", lastWeekly
            );
            try (Writer writer = Files.newBufferedWriter(timestampsFile)) {
                GSON.toJson(data, TS_TYPE, writer);
            }
        } catch (IOException e) {
            LOGGER.error("[Playtime] Failed to save backup timestamps.", e);
        }
    }
}

