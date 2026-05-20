package com.enviouse.sef.announcements;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.enviouse.sef.ServerEssentialsForge;
import com.enviouse.sef.TextFormatter;
import com.enviouse.sef.config.ConfigHandler;
import com.enviouse.sef.config.PermissionsHandler;

import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Manages scheduled server announcements (text & command) with per-announcement
 * interval scheduling. Persists to serverconfig/sef/announcements.json and
 * per-player toggle-off prefs to serverconfig/sef/announcement_prefs.json.
 *
 * Announcement fields:
 *  - id: unique identifier
 *  - type: "text" or "command"
 *  - message: the text (supports <br> for newlines and & color codes) or command string
 *  - intervalSeconds: seconds between fires (min 1). 0 means fire-once (/textannouncement ontime uses direct broadcast, not this flag)
 *  - toggleable: whether players can opt out via /toggle (only meaningful for text)
 *  - target: "@a", "@server", or a player username. For command announcements this is unused.
 *  - enabled: admin on/off flag (currently always true; reserved for pausing)
 *  - offsetSeconds: random 0..intervalSeconds initial offset to stagger bursts
 */
public class AnnouncementManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public record Announcement(
        String id,
        String type,
        String message,
        long intervalSeconds,
        boolean toggleable,
        String target,
        boolean enabled,
        long offsetSeconds
    ) {
        public Announcement withMessage(String newMessage) {
            return new Announcement(id, type, newMessage, intervalSeconds, toggleable, target, enabled, offsetSeconds);
        }
        public Announcement with(long interval, boolean tog, String tgt, String newMessage) {
            return new Announcement(id, type, newMessage, interval, tog, tgt, enabled, offsetSeconds);
        }
    }

    private static final Type LIST_TYPE = new TypeToken<List<Announcement>>(){}.getType();

    private final List<Announcement> announcements = new ArrayList<>();
    /** Per-player toggled-off announcement IDs. */
    private final Map<UUID, Set<String>> playerToggles = new HashMap<>();
    /** Tick count when each announcement should fire next. */
    private final Map<String, Long> nextFireAt = new HashMap<>();
    private Path filePath;
    private Path prefsPath;
    private long tickCounter = 0;
    private final Random random = new Random();

    public void load(MinecraftServer server) {
        Path dir = server.getServerDirectory().toPath().resolve("serverconfig").resolve("sef");
        filePath = dir.resolve("announcements.json");
        prefsPath = dir.resolve("announcement_prefs.json");
        loadAnnouncements();
        loadPrefs();
        rescheduleAll();
    }

    private void loadAnnouncements() {
        announcements.clear();
        if (filePath == null || !Files.exists(filePath)) return;
        try (Reader reader = Files.newBufferedReader(filePath)) {
            List<Announcement> loaded = GSON.fromJson(reader, LIST_TYPE);
            if (loaded != null) {
                for (Announcement a : loaded) {
                    announcements.add(migrate(a));
                }
            }
            ServerEssentialsForge.LOGGER.info("[SEF] Loaded {} announcement(s)", announcements.size());
        } catch (Exception e) {
            ServerEssentialsForge.LOGGER.error("[SEF] Failed to load announcements", e);
        }
    }

    /** Backfill defaults for old records missing the new fields. */
    private Announcement migrate(Announcement a) {
        if (a == null) return null;
        long interval = a.intervalSeconds <= 0 ? Math.max(1, ConfigHandler.config.announcementIntervalSeconds.get()) : a.intervalSeconds;
        String target = (a.target == null || a.target.isBlank()) ? "@a" : a.target;
        String type = (a.type == null) ? "text" : a.type.toLowerCase();
        boolean toggleable = "text".equalsIgnoreCase(type) && a.toggleable;
        long offset = a.offsetSeconds < 0 ? 0 : a.offsetSeconds;
        return new Announcement(a.id, type, a.message, interval, toggleable, target, a.enabled, offset);
    }

    private void loadPrefs() {
        playerToggles.clear();
        if (prefsPath == null || !Files.exists(prefsPath)) return;
        try (Reader reader = Files.newBufferedReader(prefsPath)) {
            Map<String, List<String>> loaded = GSON.fromJson(
                reader, new TypeToken<Map<String, List<String>>>(){}.getType());
            if (loaded != null) {
                loaded.forEach((uuidStr, ids) -> {
                    try {
                        playerToggles.put(UUID.fromString(uuidStr), new HashSet<>(ids));
                    } catch (IllegalArgumentException ignored) {}
                });
            }
        } catch (Exception e) {
            ServerEssentialsForge.LOGGER.error("[SEF] Failed to load announcement prefs", e);
        }
    }

    public void save() {
        if (filePath == null) return;
        try {
            Files.createDirectories(filePath.getParent());
            try (Writer writer = Files.newBufferedWriter(filePath)) {
                GSON.toJson(announcements, writer);
            }
        } catch (IOException e) {
            ServerEssentialsForge.LOGGER.error("[SEF] Failed to save announcements", e);
        }
    }

    private void savePrefs() {
        if (prefsPath == null) return;
        try {
            Files.createDirectories(prefsPath.getParent());
            Map<String, List<String>> out = new HashMap<>();
            playerToggles.forEach((uuid, ids) -> out.put(uuid.toString(), new ArrayList<>(ids)));
            try (Writer writer = Files.newBufferedWriter(prefsPath)) {
                GSON.toJson(out, writer);
            }
        } catch (IOException e) {
            ServerEssentialsForge.LOGGER.error("[SEF] Failed to save announcement prefs", e);
        }
    }

    // ---------- Scheduling ----------

    private void rescheduleAll() {
        nextFireAt.clear();
        for (Announcement a : announcements) {
            scheduleInitial(a);
        }
    }

    private void scheduleInitial(Announcement a) {
        if (a.intervalSeconds <= 0) return;
        long intervalTicks = a.intervalSeconds * 20L;
        long offsetTicks = a.offsetSeconds * 20L;
        // Add a small random stagger (0..intervalTicks/4) to desync multiple announcements
        long stagger = random.nextLong(Math.max(1, intervalTicks / 4));
        nextFireAt.put(a.id, tickCounter + offsetTicks + stagger);
    }

    public void tick(MinecraftServer server) {
        tickCounter++;
        if (announcements.isEmpty()) return;

        for (Announcement a : announcements) {
            if (!a.enabled) continue;
            if (a.intervalSeconds <= 0) continue;
            Long next = nextFireAt.get(a.id);
            if (next == null) {
                scheduleInitial(a);
                continue;
            }
            if (tickCounter < next) continue;
            fire(server, a);
            nextFireAt.put(a.id, tickCounter + a.intervalSeconds * 20L);
        }
    }

    private void fire(MinecraftServer server, Announcement a) {
        if ("command".equalsIgnoreCase(a.type)) {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), a.message);
            return;
        }
        broadcastText(server, a.message, a.target, a.id, a.toggleable);
    }

    /** Public helper for /textannouncement ontime and similar direct broadcasts. */
    public void broadcastText(MinecraftServer server, String message, String target, String announcementId, boolean toggleable) {
        List<ServerPlayer> recipients = resolveTargets(server, target);
        MutableComponent component = renderMultiLine(message);
        for (ServerPlayer player : recipients) {
            if (toggleable && announcementId != null && isToggledOff(player.getUUID(), announcementId)) {
                // Skip unless they have bypass permission
                if (!PermissionsHandler.playerHasPermission(player.getUUID(), PermissionsHandler.announcementBypass)) {
                    continue;
                }
            }
            player.sendSystemMessage(component);
        }
    }

    /** Resolves a target spec (@a, @server, <playername>) to a list of recipients. */
    private List<ServerPlayer> resolveTargets(MinecraftServer server, String target) {
        if (target == null || target.isBlank() || "@a".equals(target) || "@server".equalsIgnoreCase(target) || "all".equalsIgnoreCase(target)) {
            return new ArrayList<>(server.getPlayerList().getPlayers());
        }
        ServerPlayer p = server.getPlayerList().getPlayerByName(target);
        if (p != null) return List.of(p);
        ServerEssentialsForge.LOGGER.warn("[SEF] Announcement target '{}' did not match any player; sending to all", target);
        return new ArrayList<>(server.getPlayerList().getPlayers());
    }

    /** Renders a message with <br> converted to newlines and & color codes applied. */
    public static MutableComponent renderMultiLine(String raw) {
        // Normalise <br>, <br/>, <br /> → \n
        String normalised = raw.replaceAll("(?i)<br\\s*/?>", "\n");
        return TextFormatter.stringToFormattedText(normalised);
    }

    // ---------- CRUD ----------

    public List<Announcement> getAnnouncements() {
        return announcements;
    }

    public Announcement getById(String id) {
        for (Announcement a : announcements) if (a.id.equalsIgnoreCase(id)) return a;
        return null;
    }

    public boolean add(Announcement a) {
        if (getById(a.id) != null) return false;
        announcements.add(a);
        scheduleInitial(a);
        save();
        return true;
    }

    public boolean remove(String id) {
        boolean removed = announcements.removeIf(a -> a.id.equalsIgnoreCase(id));
        if (removed) {
            nextFireAt.remove(id);
            save();
        }
        return removed;
    }

    public boolean modify(String id, long intervalSeconds, boolean toggleable, String target, String message) {
        for (int i = 0; i < announcements.size(); i++) {
            Announcement a = announcements.get(i);
            if (a.id.equalsIgnoreCase(id)) {
                Announcement replacement = a.with(intervalSeconds, toggleable, target, message);
                announcements.set(i, replacement);
                scheduleInitial(replacement);
                save();
                return true;
            }
        }
        return false;
    }

    // ---------- Player toggles ----------

    /** Returns true if player has toggled OFF this announcement. */
    public boolean isToggledOff(UUID uuid, String announcementId) {
        Set<String> set = playerToggles.get(uuid);
        return set != null && set.contains(announcementId.toLowerCase());
    }

    /** Toggles a single announcement; returns new state (true = ON/receiving). */
    public boolean togglePlayer(UUID uuid, String announcementId) {
        String key = announcementId.toLowerCase();
        Set<String> set = playerToggles.computeIfAbsent(uuid, k -> new HashSet<>());
        boolean nowOn;
        if (set.contains(key)) {
            set.remove(key);
            nowOn = true;
        } else {
            set.add(key);
            nowOn = false;
        }
        if (set.isEmpty()) playerToggles.remove(uuid);
        savePrefs();
        return nowOn;
    }

    public List<Announcement> getToggleable() {
        return announcements.stream().filter(a -> "text".equalsIgnoreCase(a.type) && a.toggleable).toList();
    }
}
