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
        // Ensure Art directory exists for custom rank artwork
        try {
            Files.createDirectories(filePath.getParent().resolve("Art"));
        } catch (IOException e) {
            LOGGER.warn("[Playtime] Could not create Art directory: {}", e.getMessage());
        }

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

    /** Get the next available sortOrder (max + 1). */
    public int getNextSortOrder() {
        int max = -1;
        for (RankDefinition rank : ranks) {
            if (rank.getSortOrder() > max) max = rank.getSortOrder();
        }
        return max + 1;
    }

    /** Add a rank and re-sort. Returns false if id already exists. */
    public boolean addRank(RankDefinition rank) {
        if (getRankById(rank.getId()) != null) return false;
        ranks.add(rank);
        Collections.sort(ranks);
        save();
        return true;
    }

    /** Remove a rank by id. Returns the removed rank, or null. */
    public RankDefinition removeRank(String id) {
        RankDefinition found = getRankById(id);
        if (found == null) return null;
        ranks.remove(found);
        save();
        return found;
    }

    /** Re-sort ranks after an edit and save. */
    public void resortAndSave() {
        Collections.sort(ranks);
        save();
    }

    /** Get all rank IDs as a list (for tab-completion). */
    public List<String> getRankIds() {
        List<String> ids = new ArrayList<>();
        for (RankDefinition rank : ranks) ids.add(rank.getId());
        return ids;
    }

    // ── Defaults matching the 32-rank progression system ─────────────────────

    private static List<RankDefinition> createDefaults() {
        List<RankDefinition> list = new ArrayList<>();
        int order = 0;

        // ── Phase 1: The Grounded (Survival & Settlement) ──────────────────────
        list.add(rank("starter",        "Starter",        true,     1,     2,   0,   1, "Starter",        "&#D3D3D3",                 order++, "minecraft:wooden_pickaxe"));
        list.add(rank("explorer",       "Explorer",       true,     3,     8,   0,   3, "Explorer",       "&#228B22",                 order++, "minecraft:compass"));
        list.add(rank("gatherer",       "Gatherer",       true,     8,    16,   0,   5, "Gatherer",       "&#D2691E",                 order++, "minecraft:iron_pickaxe"));
        list.add(rank("settler",        "Settler",        true,    16,    32,   0,   7, "Settler",        "&#4682B4",                 order++, "minecraft:campfire"));

        // ── Phase 2: The Arcane (Magic & Mysticism) ────────────────────────────
        list.add(rank("apprentice",     "Apprentice",     true,    24,    50,   0,   9, "Apprentice",     "&#DDA0DD",                 order++, "minecraft:brewing_stand"));
        list.add(rank("alchemist",      "Alchemist",      true,    36,    75,   0,  11, "Alchemist",      "&#00CED1",                 order++, "minecraft:dragon_breath"));
        list.add(rank("sage",           "Sage",           true,    50,   100,   0,  14, "Sage",           "&#FFD700",                 order++, "minecraft:enchanting_table"));
        list.add(rank("wizard",         "Wizard",         true,    70,   130,   0,  17, "Wizard",         "&#8A2BE2",                 order++, "minecraft:end_rod"));

        // ── Phase 3: The Industrial (Steampunk Era) ────────────────────────────
        list.add(rank("tinker",         "Tinker",         true,    95,   170,   1,  20, "Tinker",         "&#8B6B4A",                 order++, "minecraft:leather"));
        list.add(rank("machinist",      "Machinist",      true,   125,   220,   2,  23, "Machinist",      "&#4A7FA8",                 order++, "minecraft:anvil"));
        list.add(rank("cogwright",      "Cogwright",      true,   160,   280,   3,  26, "Cogwright",      "&#B8860B",                 order++, "minecraft:clock"));
        list.add(rank("steamlord",      "Steamlord",      true,   200,   350,   4,  30, "Steamlord",      "&#8B0000",                 order++, "minecraft:blast_furnace"));

        // ── Phase 4: The Technological (Modern Engineering) ────────────────────
        list.add(rank("technician",     "Technician",     true,   245,   420,   5,  35, "Technician",     "gradient:#00FFFF-#00008B", order++, "minecraft:redstone"));
        list.add(rank("engineer",       "Engineer",       true,   295,   500,   6,  40, "Engineer",       "gradient:#FFA500-#FF4500", order++, "minecraft:piston"));
        list.add(rank("architect",      "Architect",      true,   350,   580,   6,  45, "Architect",      "gradient:#F5F5DC-#696969", order++, "minecraft:bricks"));
        list.add(rank("commander",      "Commander",      true,   410,   670,   7,  50, "Commander",      "gradient:#2F6700-#000000", order++, "minecraft:shield"));

        // ── Phase 5: The Ascent (Atmosphere & Early Space) ─────────────────────
        list.add(rank("aviator",        "Aviator",        true,   480,   760,   8,  55, "Aviator",        "gradient:#87CEEB-#FFFFFF", order++, "minecraft:elytra"));
        list.add(rank("astronaut",      "Astronaut",      true,   560,   850,   9,  60, "Astronaut",      "gradient:#4169E1-#0A0A2E", order++, "minecraft:netherite_helmet"));
        list.add(rank("cosmonaut",      "Cosmonaut",      true,   650,   950,  10,  65, "Cosmonaut",      "gradient:#FF0000-#800000", order++, "minecraft:firework_rocket"));
        list.add(rank("orbiteer",       "Orbiteer",       true,   750,  1050,  11,  70, "Orbiteer",       "gradient:#9400D3-#4B0082", order++, "minecraft:ender_eye"));

        // ── Phase 6: The Interplanetary (Deep Space & Colonization) ────────────
        list.add(rank("spacefarer",     "Spacefarer",     true,   860,  1150,  12,  75, "Spacefarer",     "gradient:#00FA9A-#0000CD", order++, "minecraft:ender_pearl"));
        list.add(rank("planetwalker",   "Planetwalker",   true,   980,  1250,  13,  80, "Planetwalker",   "gradient:#8B4513-#2E8B57", order++, "minecraft:grass_block"));
        list.add(rank("galaxytamer",    "Galaxytamer",    true,  1110,  1350,  14,  85, "Galaxytamer",    "gradient:#FF1493-#00BFFF", order++, "minecraft:dragon_head"));
        list.add(rank("starseeker",     "Starseeker",     true,  1250,  1450,  15,  90, "Starseeker",     "gradient:#FFFF00-#FF8C00", order++, "minecraft:glowstone"));

        // ── Phase 7: The Cosmic Manipulators (Bending Physics) ─────────────────
        list.add(rank("riftshaper",     "Riftshaper",     true,  1400,  1550,  16, 100, "Riftshaper",     "gradient:#00FF00-#8B008B", order++, "minecraft:end_portal_frame"));
        list.add(rank("eclipsebringer", "Eclipsebringer", true,  1560,  1650,  17, 110, "Eclipsebringer", "gradient:#FFFFFF-#000000", order++, "minecraft:crying_obsidian"));
        list.add(rank("voidweaver",     "Voidweaver",     true,  1730,  1740,  18, 120, "Voidweaver",     "gradient:#4B0082-#191970", order++, "minecraft:obsidian"));
        list.add(rank("chronoshifter",  "Chronoshifter",  true,  1910,  1820,  19, 130, "Chronoshifter",  "gradient:#00FFFF-#FF00FF", order++, "minecraft:end_crystal"));

        // ── Phase 8: The Absolute (God-Tier Physics & The End) — BOLD ──────────
        list.add(rank("ascendant",      "Ascendant",      true,  2100,  1890,  20, 150, "Ascendant",      "gradient:#FFD700-#FFFFFF§l", order++, "minecraft:totem_of_undying"));
        list.add(rank("celestial",      "Celestial",      true,  2250,  1940,  24, 180, "Celestial",      "gradient:#E0FFFF-#00FFFF§l", order++, "minecraft:beacon"));
        list.add(rank("hypernova",      "Hypernova",      true,  2380,  1980,  28, 240, "Hypernova",      "gradient:#FF0000-#FFFF00§l", order++, "minecraft:dragon_egg"));
        list.add(rank("singularity",    "Singularity",    true,  2500,  2000,  32,  -1, "Singularity",    "gradient:#1E0037-#FFFFFF§l", order++, "minecraft:nether_star"));

        // ── Phase texts (shown to the player when they claim the first rank of each phase) ──
        list.get(0).setPhaseText("You wake up with nothing. You learn the land, gather its resources, and build the foundation of a new civilization.");
        list.get(4).setPhaseText("The physical world is no longer enough. You discover the ancient forces of the earth, brewing potions and bending mana to your will.");
        list.get(8).setPhaseText("Magic meets machinery. You harness the power of fire and water, building massive brass contraptions and steam-powered empires.");
        list.get(12).setPhaseText("Steam gives way to electricity and automation. You design complex systems, command factories, and construct massive infrastructure.");
        list.get(16).setPhaseText("You look to the sky and wonder what is out there. You conquer gravity, pierce the atmosphere, and float in the dark for the first time.");
        list.get(20).setPhaseText("The solar system is just the beginning. You build warp drives, step foot on alien dirt, and chart the unknown edges of the galaxy.");
        list.get(24).setPhaseText("You are no longer just traveling through space; you are controlling it. You tear open dimensions, weave dark matter, and harvest the power of dying suns.");
        list.get(28).setPhaseText("You shed your mortal constraints entirely. You become the universe itself\u2014a destructive, all-knowing force that defies all laws of nature.");

        return list;
    }

    private static RankDefinition rank(String id, String display, boolean visible,
                                        long hours, int claims, int forceloads,
                                        int inactivityDays, String lpGroup,
                                        String color, int order, String defaultItem) {
        return new RankDefinition(id, display, visible, hours * 72_000L,
                claims, forceloads, inactivityDays, lpGroup, color, order, defaultItem);
    }
}
