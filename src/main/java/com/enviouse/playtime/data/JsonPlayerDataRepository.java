package com.enviouse.playtime.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JSON-file-backed implementation of {@link PlayerDataRepository}.
 * Stores player records to {@code <world>/playtime/players.json}.
 * Wipe-safe: refuses to overwrite data if the load returned null/corrupt.
 */
public class JsonPlayerDataRepository implements PlayerDataRepository {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<PlayerRecord>>() {}.getType();

    private final Path filePath;
    private final Map<UUID, PlayerRecord> players = new ConcurrentHashMap<>();
    private volatile boolean loaded = false;
    private volatile boolean dirty = false;

    public JsonPlayerDataRepository(Path worldDir) {
        this.filePath = worldDir.resolve("playtime").resolve("players.json");
    }

    @Override
    public boolean load() {
        if (!Files.exists(filePath)) {
            // First run — no data file yet, start fresh
            LOGGER.info("[Playtime] No existing player data file; starting fresh.");
            loaded = true;
            return true;
        }

        try (Reader reader = Files.newBufferedReader(filePath)) {
            List<PlayerRecord> records = GSON.fromJson(reader, LIST_TYPE);
            if (records == null) {
                LOGGER.error("[Playtime] Player data file returned null; refusing to reset database.");
                loaded = false;
                return false;
            }

            players.clear();
            for (PlayerRecord record : records) {
                if (record.getUuid() != null) {
                    players.put(record.getUuid(), record);
                }
            }

            loaded = true;
            dirty = false;
            LOGGER.info("[Playtime] Loaded {} player records.", players.size());
            return true;
        } catch (Exception e) {
            LOGGER.error("[Playtime] Failed to load player data; refusing to reset database.", e);
            loaded = false;
            return false;
        }
    }

    @Override
    public void save(boolean force) {
        if (!loaded) return;
        if (!force && !dirty) return;

        try {
            Files.createDirectories(filePath.getParent());
            List<PlayerRecord> records = new ArrayList<>(players.values());
            try (Writer writer = Files.newBufferedWriter(filePath)) {
                GSON.toJson(records, LIST_TYPE, writer);
            }
            dirty = false;
        } catch (IOException e) {
            LOGGER.error("[Playtime] CRITICAL: Failed to save player data!", e);
        }
    }

    @Override
    @Nullable
    public PlayerRecord getPlayer(UUID uuid) {
        return players.get(uuid);
    }

    @Override
    @Nullable
    public PlayerRecord getPlayerByName(String username) {
        if (username == null) return null;
        String lower = username.toLowerCase(Locale.ROOT);
        for (PlayerRecord record : players.values()) {
            if (record.getLastUsername() != null && record.getLastUsername().toLowerCase(Locale.ROOT).equals(lower)) {
                return record;
            }
        }
        return null;
    }

    @Override
    public void putPlayer(PlayerRecord record) {
        players.put(record.getUuid(), record);
        dirty = true;
    }

    @Override
    public void removePlayer(UUID uuid) {
        if (players.remove(uuid) != null) {
            dirty = true;
        }
    }

    @Override
    public Collection<PlayerRecord> getAllPlayers() {
        return Collections.unmodifiableCollection(players.values());
    }

    @Override
    public void markDirty() {
        dirty = true;
    }

    @Override
    public boolean isLoaded() {
        return loaded;
    }

    /** Returns the path this repository saves to (for backup use). */
    public Path getFilePath() {
        return filePath;
    }
}

