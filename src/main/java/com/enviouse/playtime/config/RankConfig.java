package com.enviouse.playtime.config;

import com.enviouse.playtime.data.RankDefinition;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Loads and manages rank definitions from {@code <world>/playtime/ranks.json}.
 * On first run, writes the default ranks that match the original KubeJS system.
 */
public class RankConfig {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<RankDefinition>>() {}.getType();

    private final Path filePath;
    private List<RankDefinition> ranks = new ArrayList<>();

    public RankConfig(Path worldDir) {
        this.filePath = worldDir.resolve("playtime").resolve("ranks.json");
    }

    /** Load ranks from disk or write defaults. Returns true on success. */
    public boolean load() {
        if (!Files.exists(filePath)) {
            LOGGER.info("[Playtime] No ranks.json found — writing defaults.");
            ranks = createDefaults();
            save();
            return true;
        }

        try (Reader reader = Files.newBufferedReader(filePath)) {
            List<RankDefinition> loaded = GSON.fromJson(reader, LIST_TYPE);
            if (loaded == null || loaded.isEmpty()) {
                LOGGER.error("[Playtime] ranks.json was empty or corrupt — using defaults.");
                ranks = createDefaults();
                return true;
            }
            ranks = loaded;
            Collections.sort(ranks);
            LOGGER.info("[Playtime] Loaded {} rank definitions.", ranks.size());
            return true;
        } catch (Exception e) {
            LOGGER.error("[Playtime] Failed to load ranks.json — using defaults.", e);
            ranks = createDefaults();
            return true;
        }
    }

    /** Write current ranks to disk. */
    public void save() {
        try {
            Files.createDirectories(filePath.getParent());
            try (Writer writer = Files.newBufferedWriter(filePath)) {
                GSON.toJson(ranks, LIST_TYPE, writer);
            }
        } catch (IOException e) {
            LOGGER.error("[Playtime] Failed to save ranks.json", e);
        }
    }

    /** Sorted list of all ranks (by sortOrder ascending). */
    public List<RankDefinition> getRanks() {
        return Collections.unmodifiableList(ranks);
    }

    /** Get a rank by its id, or null. */
    public RankDefinition getRankById(String id) {
        if (id == null) return null;
        for (RankDefinition rank : ranks) {
            if (rank.getId().equalsIgnoreCase(id)) return rank;
        }
        return null;
    }

    /** Get the first (lowest) rank. */
    public RankDefinition getFirstRank() {
        return ranks.isEmpty() ? null : ranks.get(0);
    }

    // ── Defaults mirroring the original KubeJS rank table ──────────────────────

    private static List<RankDefinition> createDefaults() {
        List<RankDefinition> list = new ArrayList<>();
        int order = 0;
        list.add(rank("beginner",     "Beginner",     true,    1,    4,   0,   1, "Beginner",     "§7§o", order++));
        list.add(rank("gatherer",     "Gatherer",     true,    3,    9,   0,   3, "Gatherer",     "§f",   order++));
        list.add(rank("scout",        "Scout",        true,    8,   18,   0,   5, "Scout",        "§a",   order++));
        list.add(rank("explorer",     "Explorer",     true,   16,   32,   0,   7, "Explorer",     "§2",   order++));
        list.add(rank("technician",   "Technician",   true,   24,   50,   0,   9, "Technician",   "§9",   order++));
        list.add(rank("mechanic",     "Mechanic",     true,   36,   75,   0,  11, "Mechanic",     "§3",   order++));
        list.add(rank("engineer",     "Engineer",     true,   48,  110,   0,  13, "Engineer",     "§b",   order++));
        list.add(rank("specialist",   "Specialist",   true,   60,  160,   1,  15, "Specialist",   "§e",   order++));
        list.add(rank("commander",    "Commander",    true,   72,  220,   2,  17, "Commander",    "§6",   order++));
        list.add(rank("aviator",      "Aviator",      true,   84,  280,   3,  18, "Aviator",      "§c",   order++));
        list.add(rank("astronaut",    "Astronaut",    true,   96,  325,   4,  20, "Astronaut",    "§4",   order++));
        list.add(rank("cosmonaut",    "Cosmonaut",    true,  120,  400,   5,  22, "Cosmonaut",    "§1",   order++));
        list.add(rank("orbiteer",     "Orbiteer",     true,  150,  500,   6,  24, "Orbiteer",     "§8",   order++));
        list.add(rank("solarfarer",   "Solarfarer",   true,  200,  600,   7,  30, "Solarfarer",   "§6§l§n", order++));
        list.add(rank("galaxytamer",  "Galaxytamer",  true,  300,  675,   8,  90, "Galaxytamer",  "§b§l§n", order++));
        list.add(rank("starseeker",   "Starseeker",   true,  500,  750,   9,  -1, "Starseeker",   "§c§l§n", order++));
        return list;
    }

    private static RankDefinition rank(String id, String display, boolean visible,
                                        long hours, int claims, int forceloads,
                                        int inactivityDays, String lpGroup,
                                        String color, int order) {
        return new RankDefinition(id, display, visible, hours * 72_000L,
                claims, forceloads, inactivityDays, lpGroup, color, order);
    }
}

