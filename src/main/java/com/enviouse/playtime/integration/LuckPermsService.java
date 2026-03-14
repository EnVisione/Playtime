package com.enviouse.playtime.integration;

import com.enviouse.playtime.Config;
import com.enviouse.playtime.data.RankDefinition;
import com.mojang.logging.LogUtils;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.SuffixNode;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Optional LuckPerms integration.
 * All methods are safe to call even if LP is not installed — they will no-op.
 */
public class LuckPermsService {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Legacy KubeJS rank group names that don't exist in the new rank system. */
    private static final Set<String> LEGACY_GROUPS = Set.of(
            "scout", "mechanic", "specialist", "solarfarer"
    );

    private LuckPerms api;
    private boolean available = false;

    /** Try to bind to the LuckPerms API. Call on server start. */
    public void initialize() {
        if (!Config.luckpermsEnabled) {
            LOGGER.info("[Playtime] LuckPerms integration disabled in config.");
            return;
        }
        try {
            api = LuckPermsProvider.get();
            available = true;
            LOGGER.info("[Playtime] LuckPerms API found and bound.");
        } catch (Exception | NoClassDefFoundError e) {
            api = null;
            available = false;
            LOGGER.info("[Playtime] LuckPerms not available — running without LP integration.");
        }
    }

    public boolean isAvailable() {
        return available && api != null;
    }

    /**
     * Sync a player's rank change: remove old group, add new group.
     */
    public void syncRank(UUID playerUuid, @Nullable RankDefinition oldRank, RankDefinition newRank, MinecraftServer server) {
        if (!isAvailable()) return;

        try {
            UserManager userManager = api.getUserManager();
            userManager.loadUser(playerUuid).thenAcceptAsync(user -> {
                if (user == null) return;

                // Remove old group
                if (oldRank != null && !oldRank.getLuckpermsGroup().equalsIgnoreCase(newRank.getLuckpermsGroup())) {
                    InheritanceNode oldNode = InheritanceNode.builder(oldRank.getLuckpermsGroup().toLowerCase()).build();
                    user.data().remove(oldNode);
                }

                // Add new group
                InheritanceNode newNode = InheritanceNode.builder(newRank.getLuckpermsGroup().toLowerCase()).build();
                user.data().add(newNode);

                userManager.saveUser(user);
                LOGGER.info("[Playtime] LP sync: {} → group '{}'", playerUuid, newRank.getLuckpermsGroup());
            }).exceptionally(ex -> {
                LOGGER.warn("[Playtime] LP sync failed for {}: {}", playerUuid, ex.getMessage());
                return null;
            });
        } catch (Exception e) {
            LOGGER.warn("[Playtime] LP sync error for {}: {}", playerUuid, e.getMessage());
        }
    }

    /** Add a single group to a player. */
    public void addGroup(UUID playerUuid, RankDefinition rank) {
        if (!isAvailable()) return;
        try {
            api.getUserManager().loadUser(playerUuid).thenAcceptAsync(user -> {
                if (user == null) return;
                InheritanceNode node = InheritanceNode.builder(rank.getLuckpermsGroup().toLowerCase()).build();
                user.data().add(node);
                api.getUserManager().saveUser(user);
            });
        } catch (Exception e) {
            LOGGER.warn("[Playtime] LP addGroup error: {}", e.getMessage());
        }
    }

    /** Remove a single group from a player. Respects syncWithLuckPerms flag. */
    public void removeGroup(UUID playerUuid, RankDefinition rank) {
        if (!isAvailable()) return;
        if (!rank.isSyncWithLuckPerms()) return;
        try {
            api.getUserManager().loadUser(playerUuid).thenAcceptAsync(user -> {
                if (user == null) return;
                InheritanceNode node = InheritanceNode.builder(rank.getLuckpermsGroup().toLowerCase()).build();
                user.data().remove(node);
                api.getUserManager().saveUser(user);
            });
        } catch (Exception e) {
            LOGGER.warn("[Playtime] LP removeGroup error: {}", e.getMessage());
        }
    }

    /**
     * Set a suffix on a player via LuckPerms API.
     * Equivalent to: lp user <name> meta setsuffix <priority> <suffix>
     * Removes any existing suffix at the same priority first.
     */
    public void setSuffix(UUID playerUuid, int priority, String suffix) {
        if (!isAvailable()) return;
        try {
            api.getUserManager().loadUser(playerUuid).thenAcceptAsync(user -> {
                if (user == null) return;
                // Remove existing suffixes at this priority
                user.data().toCollection().stream()
                        .filter(n -> n instanceof SuffixNode && ((SuffixNode) n).getPriority() == priority)
                        .forEach(n -> user.data().remove(n));
                // Add new suffix
                SuffixNode node = SuffixNode.builder(suffix, priority).build();
                user.data().add(node);
                api.getUserManager().saveUser(user);
                LOGGER.info("[Playtime] LP setSuffix: {} → priority={} suffix='{}'", playerUuid, priority, suffix);
            });
        } catch (Exception e) {
            LOGGER.warn("[Playtime] LP setSuffix error: {}", e.getMessage());
        }
    }

    /**
     * Remove all suffixes at a given priority from a player.
     * Equivalent to: lp user <name> meta removesuffix <priority>
     */
    public void removeSuffix(UUID playerUuid, int priority) {
        if (!isAvailable()) return;
        try {
            api.getUserManager().loadUser(playerUuid).thenAcceptAsync(user -> {
                if (user == null) return;
                user.data().toCollection().stream()
                        .filter(n -> n instanceof SuffixNode && ((SuffixNode) n).getPriority() == priority)
                        .forEach(n -> user.data().remove(n));
                api.getUserManager().saveUser(user);
                LOGGER.info("[Playtime] LP removeSuffix: {} → priority={}", playerUuid, priority);
            });
        } catch (Exception e) {
            LOGGER.warn("[Playtime] LP removeSuffix error: {}", e.getMessage());
        }
    }

    /**
     * Get the prefix string for a LuckPerms group, or null if unavailable.
     */
    @Nullable
    public String getGroupPrefix(String groupName) {
        if (!isAvailable()) return null;
        try {
            Group group = api.getGroupManager().getGroup(groupName.toLowerCase());
            if (group == null) return null;
            CachedMetaData meta = group.getCachedData().getMetaData();
            return meta.getPrefix();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get a raw display color/prefix string for a rank.
     * Tries LP group prefix first, falls back to rank's configured fallback color.
     * Preserves &-codes and &#RRGGBB hex codes for processing by ColorUtil.
     */
    public String getDisplayColor(RankDefinition rank) {
        if (isAvailable()) {
            String prefix = getGroupPrefix(rank.getLuckpermsGroup());
            if (prefix != null && !prefix.isEmpty()) {
                return prefix;
            }
        }
        return rank.getFallbackColor();
    }

    /**
     * Build a styled Component for displaying a rank name with proper colours.
     * Supports hex (&#RRGGBB), legacy §-codes, and &-codes from LP prefixes.
     */
    public net.minecraft.network.chat.MutableComponent getStyledRankName(RankDefinition rank) {
        String colorStr = getDisplayColor(rank);
        return com.enviouse.playtime.util.ColorUtil.rankDisplay(colorStr, rank.getDisplayName());
    }

    /**
     * Full login sync: remove ALL known rank groups (legacy KubeJS + current system)
     * and add only the correct rank group. Called on every player login to ensure
     * imported players are properly migrated and LP groups stay consistent.
     *
     * @param playerUuid   the player's UUID
     * @param allRanks     all current rank definitions (for group name lookup)
     * @param correctRank  the rank the player should have
     */
    public void fullLoginSync(UUID playerUuid, List<RankDefinition> allRanks, RankDefinition correctRank) {
        if (!isAvailable()) return;
        if (!Config.loginRankSync) return;
        try {
            api.getUserManager().loadUser(playerUuid).thenAcceptAsync(user -> {
                if (user == null) return;

                // Remove ALL known rank groups (both new system and legacy KubeJS)
                for (RankDefinition rank : allRanks) {
                    InheritanceNode node = InheritanceNode.builder(rank.getLuckpermsGroup().toLowerCase()).build();
                    user.data().remove(node);
                }
                for (String legacy : LEGACY_GROUPS) {
                    InheritanceNode node = InheritanceNode.builder(legacy).build();
                    user.data().remove(node);
                }

                // Add only the correct rank group
                InheritanceNode newNode = InheritanceNode.builder(correctRank.getLuckpermsGroup().toLowerCase()).build();
                user.data().add(newNode);

                api.getUserManager().saveUser(user);
                LOGGER.info("[Playtime] LP login sync: {} → group '{}'", playerUuid, correctRank.getLuckpermsGroup());
            }).exceptionally(ex -> {
                LOGGER.warn("[Playtime] LP login sync failed for {}: {}", playerUuid, ex.getMessage());
                return null;
            });
        } catch (Exception e) {
            LOGGER.warn("[Playtime] LP login sync error for {}: {}", playerUuid, e.getMessage());
        }
    }

    public void shutdown() {
        api = null;
        available = false;
    }
}

