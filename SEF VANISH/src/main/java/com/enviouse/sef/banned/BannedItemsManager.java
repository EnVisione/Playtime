package com.enviouse.sef.banned;

import com.enviouse.sef.ServerEssentialsForge;
import com.enviouse.sef.TextFormatter;
import com.enviouse.sef.config.ConfigHandler;
import com.enviouse.sef.utils.moddeps.CuriosInventoryHelper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonArray;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Banned-item manager.
 *
 * <ul>
 *   <li>Stores ban entries (with reason / duration / banner / announce flag), bypass
 *       UUIDs, excepted blocks, and runtime tuning state in
 *       {@code <world>/serverconfig/sef/banned_items.json}.</li>
 *   <li>Inventory + cursor + open container + Curios are scanned every tick for
 *       any online player.</li>
 *   <li>Block sweeps run on a configurable interval/radius around each player.</li>
 *   <li>Players in creative or with {@code sef.banned.bypass} or in the manual bypass
 *       list are exempt.</li>
 *   <li>Auto-purges expired ban entries.</li>
 * </ul>
 */
public class BannedItemsManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Map<String, BannedEntry> entries = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> bypassed = ConcurrentHashMap.newKeySet();
    private final List<BannedExceptedBlock> excepted = new CopyOnWriteArrayList<>();

    private boolean enabledItems = true;
    private boolean enabledBlocks = true;
    private boolean dropOnDestroy = false;
    private int radiusOverride = -1;       // -1 = use config
    private int intervalOverride = -1;     // -1 = use config

    private Path filePath;
    private int lastScanTick = 0;

    // ── Persistence ─────────────────────────────────────────────────────────

    public void load(MinecraftServer server) {
        Path dir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("serverconfig").resolve("sef");
        filePath = dir.resolve("banned_items.json");
        entries.clear();
        bypassed.clear();
        excepted.clear();
        if (!Files.exists(filePath)) {
            // attempt to migrate the legacy flat-Set format from older builds
            tryMigrateLegacy(dir);
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(filePath)) {
            JsonElement root = com.google.gson.JsonParser.parseReader(reader);
            if (root == null || root.isJsonNull()) return;
            // Legacy: a JSON array of registry-id strings
            if (root.isJsonArray()) {
                for (JsonElement e : root.getAsJsonArray()) {
                    if (e.isJsonPrimitive()) {
                        String id = e.getAsString();
                        entries.put(id.toLowerCase(), new BannedEntry(id, "", "Console", -1L, false));
                    }
                }
                save();
                return;
            }
            JsonObject obj = root.getAsJsonObject();
            if (obj.has("entries") && obj.get("entries").isJsonObject()) {
                JsonObject ents = obj.getAsJsonObject("entries");
                for (Map.Entry<String, JsonElement> e : ents.entrySet()) {
                    BannedEntry be = GSON.fromJson(e.getValue(), BannedEntry.class);
                    if (be != null && be.pattern != null && !be.pattern.isEmpty()) {
                        entries.put(e.getKey(), be);
                    }
                }
            }
            if (obj.has("bypassed") && obj.get("bypassed").isJsonArray()) {
                for (JsonElement e : obj.getAsJsonArray("bypassed")) {
                    try { bypassed.add(UUID.fromString(e.getAsString())); }
                    catch (Exception ignored) {}
                }
            }
            if (obj.has("excepted") && obj.get("excepted").isJsonArray()) {
                for (JsonElement e : obj.getAsJsonArray("excepted")) {
                    BannedExceptedBlock b = GSON.fromJson(e, BannedExceptedBlock.class);
                    if (b != null && b.dimension != null) excepted.add(b);
                }
            }
            if (obj.has("settings") && obj.get("settings").isJsonObject()) {
                JsonObject s = obj.getAsJsonObject("settings");
                if (s.has("enabledItems")) enabledItems = s.get("enabledItems").getAsBoolean();
                if (s.has("enabledBlocks")) enabledBlocks = s.get("enabledBlocks").getAsBoolean();
                if (s.has("dropOnDestroy")) dropOnDestroy = s.get("dropOnDestroy").getAsBoolean();
                if (s.has("radiusOverride")) radiusOverride = s.get("radiusOverride").getAsInt();
                if (s.has("intervalOverride")) intervalOverride = s.get("intervalOverride").getAsInt();
            }
            ServerEssentialsForge.LOGGER.info("[SEF] Loaded {} banned entry(ies), {} bypass, {} excepted",
                    entries.size(), bypassed.size(), excepted.size());
        } catch (Exception e) {
            ServerEssentialsForge.LOGGER.error("[SEF] Failed to load banned items", e);
        }
    }

    private void tryMigrateLegacy(Path dir) {
        Path legacy = dir.resolve("banned_items.json");
        if (Files.exists(legacy)) return; // load() already handled it
    }

    public void save() {
        if (filePath == null) return;
        try {
            Files.createDirectories(filePath.getParent());
            JsonObject root = new JsonObject();
            JsonObject ents = new JsonObject();
            for (Map.Entry<String, BannedEntry> e : entries.entrySet()) {
                ents.add(e.getKey(), GSON.toJsonTree(e.getValue()));
            }
            root.add("entries", ents);
            JsonArray byp = new JsonArray();
            for (UUID u : bypassed) byp.add(u.toString());
            root.add("bypassed", byp);
            JsonArray exc = new JsonArray();
            for (BannedExceptedBlock b : excepted) exc.add(GSON.toJsonTree(b));
            root.add("excepted", exc);
            JsonObject s = new JsonObject();
            s.addProperty("enabledItems", enabledItems);
            s.addProperty("enabledBlocks", enabledBlocks);
            s.addProperty("dropOnDestroy", dropOnDestroy);
            s.addProperty("radiusOverride", radiusOverride);
            s.addProperty("intervalOverride", intervalOverride);
            root.add("settings", s);
            try (Writer w = Files.newBufferedWriter(filePath)) {
                GSON.toJson(root, w);
            }
        } catch (Exception e) {
            ServerEssentialsForge.LOGGER.error("[SEF] Failed to save banned items", e);
        }
    }

    public void reload(MinecraftServer server) { load(server); }

    // ── Entry CRUD ──────────────────────────────────────────────────────────

    public Map<String, BannedEntry> getEntries() {
        return Collections.unmodifiableMap(entries);
    }

    public java.util.Set<String> getPatterns() {
        return Collections.unmodifiableSet(entries.keySet());
    }

    public BannedEntry getEntry(String pattern) {
        return entries.get(pattern.toLowerCase());
    }

    public boolean addBan(String pattern, String reason, long durationMs,
                          String bannedBy, boolean announce) {
        if (pattern == null) return false;
        String key = pattern.toLowerCase();
        if (entries.containsKey(key)) return false;
        entries.put(key, new BannedEntry(pattern, reason, bannedBy, durationMs, announce));
        save();
        return true;
    }

    public boolean removeBan(String pattern) {
        if (pattern == null) return false;
        boolean removed = entries.remove(pattern.toLowerCase()) != null;
        if (removed) save();
        return removed;
    }

    public boolean updateBan(String pattern, String reason, Long durationMs, Boolean announce) {
        if (pattern == null) return false;
        BannedEntry e = entries.get(pattern.toLowerCase());
        if (e == null) return false;
        if (reason != null) e.reason = reason;
        if (durationMs != null) {
            // re-anchor so "1h" means "1h from now" on update
            e.durationMs = durationMs;
            e.bannedAtMillis = System.currentTimeMillis();
        }
        if (announce != null) e.announce = announce;
        save();
        return true;
    }

    public int clearAll() {
        int n = entries.size();
        entries.clear();
        save();
        return n;
    }

    /** Removes any ban entries whose duration has expired. Returns how many were dropped. */
    private int purgeExpired() {
        int n = 0;
        for (Map.Entry<String, BannedEntry> e : new java.util.ArrayList<>(entries.entrySet())) {
            if (e.getValue().isExpired()) {
                entries.remove(e.getKey());
                n++;
            }
        }
        if (n > 0) save();
        return n;
    }

    // ── Matching ────────────────────────────────────────────────────────────

    public BannedEntry matchItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        Item item = stack.getItem();
        ResourceLocation rl = ForgeRegistries.ITEMS.getKey(item);
        if (rl == null) return null;
        for (BannedEntry e : entries.values()) {
            if (e.isExpired()) continue;
            if (e.matchesItem(rl, stack)) return e;
        }
        return null;
    }

    public BannedEntry matchBlock(BlockState state) {
        if (state == null || state.isAir()) return null;
        Block block = state.getBlock();
        ResourceLocation rl = ForgeRegistries.BLOCKS.getKey(block);
        if (rl == null) return null;
        for (BannedEntry e : entries.values()) {
            if (e.isExpired()) continue;
            if (e.matchesBlock(rl, state)) return e;
            // also catch tag/wildcard bans expressed as item ids by checking the block's item form
            Item itemForm = block.asItem();
            if (itemForm != null) {
                ResourceLocation irl = ForgeRegistries.ITEMS.getKey(itemForm);
                if (irl != null && e.matchesItem(irl, new ItemStack(itemForm))) return e;
            }
        }
        return null;
    }

    // ── Bypass ──────────────────────────────────────────────────────────────

    public boolean isBypassed(ServerPlayer player) {
        if (player == null) return false;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return true;
        if (bypassed.contains(player.getUUID())) return true;
        try {
            // permission-based bypass; falls through to false on any error
            net.minecraftforge.server.permission.nodes.PermissionNode<Boolean> node =
                    com.enviouse.sef.config.PermissionsHandler.bannedBypassNode;
            if (node != null && net.minecraftforge.server.permission.PermissionAPI.getPermission(player, node)) {
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public boolean setBypass(UUID uuid, boolean on) {
        boolean changed = on ? bypassed.add(uuid) : bypassed.remove(uuid);
        if (changed) save();
        return changed;
    }

    public boolean isManualBypass(UUID uuid) { return bypassed.contains(uuid); }

    public java.util.Set<UUID> getBypassed() { return Collections.unmodifiableSet(bypassed); }

    // ── Exceptions ──────────────────────────────────────────────────────────

    public List<BannedExceptedBlock> getExceptions() { return Collections.unmodifiableList(excepted); }

    public void addException(ServerLevel level, BlockPos pos, String itemId, String addedBy) {
        if (level == null || pos == null) return;
        String dim = level.dimension().location().toString();
        if (isExcepted(dim, pos.getX(), pos.getY(), pos.getZ())) return;
        excepted.add(new BannedExceptedBlock(dim, pos.getX(), pos.getY(), pos.getZ(), itemId, addedBy));
        save();
    }

    public boolean isExcepted(ServerLevel level, BlockPos pos) {
        return isExcepted(level.dimension().location().toString(),
                pos.getX(), pos.getY(), pos.getZ());
    }

    public boolean isExcepted(String dim, int x, int y, int z) {
        for (BannedExceptedBlock b : excepted) {
            if (b.matches(dim, x, y, z)) return true;
        }
        return false;
    }

    public boolean removeExceptionAt(int index) {
        if (index < 0 || index >= excepted.size()) return false;
        excepted.remove(index);
        save();
        return true;
    }

    public int clearExceptions() {
        int n = excepted.size();
        excepted.clear();
        save();
        return n;
    }

    // ── Toggles ─────────────────────────────────────────────────────────────

    public boolean isItemsEnabled() { return enabledItems; }
    public boolean isBlocksEnabled() { return enabledBlocks; }
    public boolean isDropOnDestroy() { return dropOnDestroy; }

    public void setItemsEnabled(boolean v) { enabledItems = v; save(); }
    public void setBlocksEnabled(boolean v) { enabledBlocks = v; save(); }
    public void setDropOnDestroy(boolean v) { dropOnDestroy = v; save(); }

    public int getEffectiveRadius() {
        if (radiusOverride > 0) return radiusOverride;
        return ConfigHandler.config.bannedBlockScanRadius.get();
    }

    public int getEffectiveInterval() {
        if (intervalOverride > 0) return intervalOverride;
        return ConfigHandler.config.bannedBlockScanInterval.get();
    }

    public void setRadiusOverride(int v) { radiusOverride = Math.max(1, v); save(); }
    public void setIntervalOverride(int v) { intervalOverride = Math.max(1, v); save(); }

    // ── Tick ────────────────────────────────────────────────────────────────

    public void tick(MinecraftServer server, int tickCount) {
        if (entries.isEmpty()) return;

        // Drop expired entries every ~5s so /banned list stays accurate.
        if (tickCount % 100 == 0) purgeExpired();

        if (enabledItems) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (isBypassed(player)) continue;
                scanInventory(player);
            }
        }

        if (enabledBlocks && ConfigHandler.config.enableBannedBlockScanning.get()) {
            int interval = getEffectiveInterval();
            if (tickCount - lastScanTick >= interval) {
                lastScanTick = tickCount;
                int radius = getEffectiveRadius();
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    scanBlocksAround(player, radius);
                }
            }
        }
    }

    // ── Scans ───────────────────────────────────────────────────────────────

    /** Force a full inventory + container + curios scan for one player. */
    public int forceScan(ServerPlayer player) {
        int total = scanInventory(player);
        return total;
    }

    /**
     * Scans the player's main inventory (hotbar + main + armor + offhand), the
     * cursor stack, the currently open container, and Curios slots.
     */
    public int scanInventory(ServerPlayer player) {
        int removed = 0;
        // Main inventory
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            BannedEntry hit = matchItem(stack);
            if (hit != null) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
                notifyConfiscation(player, stack, hit);
                removed++;
            }
        }
        // Cursor (held while a container is open)
        AbstractContainerMenu menu = player.containerMenu;
        if (menu != null) {
            ItemStack carried = menu.getCarried();
            BannedEntry hit = matchItem(carried);
            if (hit != null) {
                menu.setCarried(ItemStack.EMPTY);
                menu.broadcastChanges();
                notifyConfiscation(player, carried, hit);
                removed++;
            }
            // Currently-open container slots (chests etc.)
            for (Slot slot : menu.slots) {
                ItemStack s = slot.getItem();
                BannedEntry h = matchItem(s);
                if (h != null) {
                    slot.set(ItemStack.EMPTY);
                    notifyConfiscation(player, s, h);
                    removed++;
                }
            }
            if (removed > 0) menu.broadcastChanges();
        }
        // Curios
        if (CuriosInventoryHelper.isCuriosLoaded()) {
            int curiosRemoved = CuriosInventoryHelper.clearMatching(player, stack -> {
                BannedEntry e = matchItem(stack);
                if (e != null) {
                    notifyConfiscation(player, stack, e);
                    return true;
                }
                return false;
            });
            removed += curiosRemoved;
        }
        if (removed > 0) {
            player.inventoryMenu.broadcastChanges();
        }
        return removed;
    }

    /** Cubic radius sweep around the player. Excepted blocks are skipped. */
    public int scanBlocksAround(ServerPlayer player, int radius) {
        ServerLevel level = player.serverLevel();
        BlockPos center = player.blockPosition();
        int destroyed = 0;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    if (isExcepted(level, pos)) continue;
                    BlockState state = level.getBlockState(pos);
                    BannedEntry hit = matchBlock(state);
                    if (hit != null) {
                        ResourceLocation rl = ForgeRegistries.BLOCKS.getKey(state.getBlock());
                        level.destroyBlock(pos, dropOnDestroy);
                        ServerEssentialsForge.LOGGER.info(
                                "[SEF] Destroyed banned block {} at {} near {}",
                                rl, pos, player.getGameProfile().getName());
                        destroyed++;
                    }
                }
            }
        }
        return destroyed;
    }

    // ── Notification ────────────────────────────────────────────────────────

    private void notifyConfiscation(ServerPlayer player, ItemStack stack, BannedEntry hit) {
        ResourceLocation rl = ForgeRegistries.ITEMS.getKey(stack.getItem());
        String idStr = rl != null ? rl.toString() : "unknown";

        String configFmt = ConfigHandler.config.bannedItemRemovedMsg.get();
        String fmt = (configFmt == null || configFmt.isEmpty())
                ? "&cBanned item removed: &e$item&7 (&fReason: $reason&7)"
                : configFmt;
        String text = fmt.replace("$item", idStr)
                .replace("$reason", hit.reason == null || hit.reason.isEmpty() ? "no reason given" : hit.reason)
                .replace("$by", hit.bannedBy == null ? "" : hit.bannedBy)
                .replace("$remaining", hit.getRemainingString());
        Component msg = TextFormatter.stringToFormattedText(text);
        player.sendSystemMessage(msg);

        ServerEssentialsForge.LOGGER.info("[SEF] Confiscated banned item {} from {}",
                idStr, player.getGameProfile().getName());

        if (hit.announce && player.getServer() != null) {
            String anFmt = ConfigHandler.config.bannedAnnounceFormat.get();
            if (anFmt == null || anFmt.isEmpty()) {
                anFmt = "&7[&cBanned&7] &e$player &7had banned item &e$item &7removed.";
            }
            String anText = anFmt
                    .replace("$player", player.getGameProfile().getName())
                    .replace("$item", idStr)
                    .replace("$reason", hit.reason == null ? "" : hit.reason);
            Component aMsg = TextFormatter.stringToFormattedText(anText);
            for (ServerPlayer p : player.getServer().getPlayerList().getPlayers()) {
                p.sendSystemMessage(aMsg);
            }
        }
    }

    // ── Suggestion helpers (used by Brigadier) ──────────────────────────────

    public List<String> suggestAllItems() {
        List<String> out = new ArrayList<>(ForgeRegistries.ITEMS.getKeys().size());
        for (ResourceLocation rl : ForgeRegistries.ITEMS.getKeys()) out.add(rl.toString());
        return out;
    }
}
