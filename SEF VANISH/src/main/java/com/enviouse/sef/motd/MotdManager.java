package com.enviouse.sef.motd;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.enviouse.sef.ServerEssentialsForge;

import net.minecraft.server.MinecraftServer;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Manages the server MOTD. Config stored in config/sef/motd.json.
 */
public class MotdManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path configPath;
    private MotdData data = new MotdData("", "");

    public record MotdData(String line1, String line2) {}

    public MotdManager(Path configDir) {
        this.configPath = configDir.resolve("motd.json");
    }

    public void load() {
        if (!Files.exists(configPath)) {
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(configPath)) {
            MotdData loaded = GSON.fromJson(reader, MotdData.class);
            if (loaded != null) {
                // Gson can deserialize record fields as null — normalize to empty strings
                String l1 = loaded.line1() != null ? loaded.line1() : "";
                String l2 = loaded.line2() != null ? loaded.line2() : "";
                data = new MotdData(l1, l2);
            }
            ServerEssentialsForge.LOGGER.info("[SEF] MOTD loaded");
        } catch (Exception e) {
            ServerEssentialsForge.LOGGER.error("[SEF] Failed to load MOTD", e);
        }
    }

    public void save() {
        try {
            Files.createDirectories(configPath.getParent());
            try (Writer writer = Files.newBufferedWriter(configPath)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            ServerEssentialsForge.LOGGER.error("[SEF] Failed to save MOTD", e);
        }
    }

    public void setMotd(String line1, String line2) {
        data = new MotdData(
            line1 != null ? line1 : "",
            line2 != null ? line2 : ""
        );
        save();
    }

    public MotdData getData() {
        return data;
    }

    public void applyToServer(MinecraftServer server) {
        // Guard against null data fields (e.g., singleplayer first launch, corrupted JSON)
        String line1 = data.line1() != null ? data.line1() : "";
        String line2 = data.line2() != null ? data.line2() : "";
        String motdStr = line1;
        if (!line2.isEmpty()) {
            motdStr += "\n" + line2;
        }
        if (motdStr.isEmpty()) {
            ServerEssentialsForge.LOGGER.debug("[SEF] MOTD is empty, skipping apply");
            return;
        }
        // Convert & color codes to § for vanilla MOTD rendering
        motdStr = motdStr.replace("&0", "\u00A70").replace("&1", "\u00A71").replace("&2", "\u00A72")
            .replace("&3", "\u00A73").replace("&4", "\u00A74").replace("&5", "\u00A75")
            .replace("&6", "\u00A76").replace("&7", "\u00A77").replace("&8", "\u00A78")
            .replace("&9", "\u00A79").replace("&a", "\u00A7a").replace("&b", "\u00A7b")
            .replace("&c", "\u00A7c").replace("&d", "\u00A7d").replace("&e", "\u00A7e")
            .replace("&f", "\u00A7f").replace("&l", "\u00A7l").replace("&o", "\u00A7o")
            .replace("&n", "\u00A7n").replace("&m", "\u00A7m").replace("&k", "\u00A7k")
            .replace("&r", "\u00A7r");
        server.setMotd(motdStr);
        ServerEssentialsForge.LOGGER.info("[SEF] Server MOTD applied");
    }
}

