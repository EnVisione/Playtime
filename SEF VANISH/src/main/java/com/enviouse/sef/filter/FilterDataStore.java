package com.enviouse.sef.filter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.enviouse.sef.ServerEssentialsForge;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class FilterDataStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, FilterRecord>>(){}.getType();

    public record FilterRecord(String wordToFilter, String replacement, boolean caseSensitive) {}

    private final Map<String, FilterRecord> filters = new LinkedHashMap<>();
    private Path filePath;

    public void setPath(Path path) {
        this.filePath = path;
    }

    public Map<String, FilterRecord> getFilters() {
        return filters;
    }

    public void load() {
        filters.clear();
        if (filePath == null || !Files.exists(filePath)) return;
        try (Reader reader = Files.newBufferedReader(filePath)) {
            Map<String, FilterRecord> loaded = GSON.fromJson(reader, MAP_TYPE);
            if (loaded != null) filters.putAll(loaded);
            ServerEssentialsForge.LOGGER.info("[SEF] Loaded {} word filter(s)", filters.size());
        } catch (Exception e) {
            ServerEssentialsForge.LOGGER.error("[SEF] Failed to load filters", e);
        }
    }

    public void save() {
        if (filePath == null) return;
        try {
            Files.createDirectories(filePath.getParent());
            try (Writer writer = Files.newBufferedWriter(filePath)) {
                GSON.toJson(filters, writer);
            }
        } catch (IOException e) {
            ServerEssentialsForge.LOGGER.error("[SEF] Failed to save filters", e);
        }
    }
}

