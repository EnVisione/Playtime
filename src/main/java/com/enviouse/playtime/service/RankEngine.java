package com.enviouse.playtime.service;

import com.enviouse.playtime.Config;
import com.enviouse.playtime.config.RankConfig;
import com.enviouse.playtime.data.PlayerDataRepository;
import com.enviouse.playtime.data.PlayerRecord;
import com.enviouse.playtime.data.RankDefinition;
import com.enviouse.playtime.integration.LuckPermsService;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Calculates ranks from playtime and applies rank-up effects.
 */
public class RankEngine {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final RankConfig rankConfig;
    private final PlayerDataRepository repository;
    private final LuckPermsService luckPerms;

    // Track which rank we last notified each player about (to prevent spam)
    private final Map<UUID, String> lastNotifiedRank = new ConcurrentHashMap<>();

    public RankEngine(RankConfig rankConfig, PlayerDataRepository repository, LuckPermsService luckPerms) {
        this.rankConfig = rankConfig;
        this.repository = repository;
        this.luckPerms = luckPerms;
    }

    /** Get the rank a player has earned based on total ticks. */
    public RankDefinition getCurrentRank(long totalTicks) {
        List<RankDefinition> ranks = rankConfig.getRanks();
        RankDefinition current = ranks.get(0);
        for (RankDefinition rank : ranks) {
            if (totalTicks >= rank.getThresholdTicks()) {
                current = rank;
            } else {
                break;
            }
        }
        return current;
    }

    /** Get the next rank after the given rank, or null if maxed. */
    @Nullable
    public RankDefinition getNextRank(RankDefinition current) {
        List<RankDefinition> ranks = rankConfig.getRanks();
        boolean found = false;
        for (RankDefinition rank : ranks) {
            if (found) return rank;
            if (rank.getId().equals(current.getId())) found = true;
        }
        return null;
    }

    /**
     * Check if a player has earned a new rank and notify them it's available.
     * Does NOT auto-apply the rank — player must claim via /playtime GUI.
     */
    public void checkAndApplyProgression(MinecraftServer server, UUID playerUuid, long totalTicks) {
        PlayerRecord record = repository.getPlayer(playerUuid);
        if (record == null) return;

        RankDefinition earnedRank = getCurrentRank(totalTicks);
        String storedRankId = record.getCurrentRankId();

        // If stored rank matches or exceeds earned, nothing to do
        if (storedRankId != null) {
            RankDefinition storedRank = rankConfig.getRankById(storedRankId);
            if (storedRank != null && storedRank.getSortOrder() >= earnedRank.getSortOrder()) {
                return; // already at or above earned rank
            }
        }

        // Player has earned a higher rank but hasn't claimed it yet — notify them
        // Only notify if we haven't already notified for this rank
        String lastNotified = lastNotifiedRank.get(playerUuid);
        if (lastNotified != null && lastNotified.equals(earnedRank.getId())) {
            return; // already notified for this rank
        }
        lastNotifiedRank.put(playerUuid, earnedRank.getId());

        ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
        if (player != null) {
            notifyRankAvailable(server, player, earnedRank);
        }
    }

    /** Notify a player that they have earned a rank available for claiming. */
    private void notifyRankAvailable(MinecraftServer server, ServerPlayer player, RankDefinition earnedRank) {
        String playerName = player.getGameProfile().getName();

        // Chat message (only to this player)
        Component chatMsg = Component.literal("§b✦ ")
                .append(Component.literal("§bNew rank available: "))
                .append(luckPerms.getStyledRankName(earnedRank))
                .append(Component.literal("§b! Use §f/playtime §bto claim it."));
        player.sendSystemMessage(chatMsg);

        // Title (only to this player) — light blue
        server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withSuppressedOutput(),
                "title " + playerName + " times 10 40 10"
        );
        server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withSuppressedOutput(),
                "title " + playerName + " title {\"text\":\"Rank Available!\",\"color\":\"aqua\"}"
        );
        server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withSuppressedOutput(),
                "title " + playerName + " subtitle {\"text\":\"" + earnedRank.getDisplayName() + " — /playtime to claim\",\"color\":\"aqua\"}"
        );
    }

    /**
     * Actually apply a rank claim: update stored rank, sync LP, broadcast, play effects.
     * Called from ClaimRankC2SPacket when the player clicks to claim.
     *
     * @param fromRank the rank the player currently has (may be null for first rank)
     * @param toRank   the rank being claimed (the highest earned rank)
     */
    public void applyRankClaim(MinecraftServer server, UUID playerUuid,
                                RankDefinition fromRank, RankDefinition toRank) {
        PlayerRecord record = repository.getPlayer(playerUuid);
        if (record == null) return;

        // Update stored rank
        record.setCurrentRankId(toRank.getId());
        repository.markDirty();

        // Clear notification tracking
        lastNotifiedRank.remove(playerUuid);

        // Sync LuckPerms groups
        luckPerms.syncRank(playerUuid, fromRank, toRank, server);

        // Apply rank-up effects (only if player is online)
        ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
        if (player != null) {
            applyRankUpEffects(server, player, fromRank, toRank);
        }
    }

    /** Apply visual/audio rank-up effects. */
    private void applyRankUpEffects(MinecraftServer server, ServerPlayer player,
                                     @Nullable RankDefinition oldRank, RankDefinition newRank) {
        String playerName = player.getGameProfile().getName();

        // Phase text — shown only to this player when entering a new phase
        String phaseText = newRank.getPhaseText();
        if (phaseText != null && !phaseText.isEmpty()) {
            player.sendSystemMessage(Component.literal(""));
            player.sendSystemMessage(Component.literal("§8§m                                                    "));
            player.sendSystemMessage(Component.literal("§7§o" + phaseText));
            player.sendSystemMessage(Component.literal("§8§m                                                    "));
            player.sendSystemMessage(Component.literal(""));
        }

        // Broadcast message
        if (Config.rankupBroadcast) {
            Component msg;
            if (oldRank != null) {
                msg = Component.literal("§6" + playerName + " ranked up: ")
                        .append(luckPerms.getStyledRankName(oldRank))
                        .append(Component.literal("§r §f→ "))
                        .append(luckPerms.getStyledRankName(newRank));
            } else {
                msg = Component.literal("§6" + playerName + " reached rank: ")
                        .append(luckPerms.getStyledRankName(newRank));
            }
            server.getPlayerList().broadcastSystemMessage(msg, false);
        }

        // Title
        server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withSuppressedOutput(),
                "title " + playerName + " times " + Config.rankupTitleFadeIn + " " + Config.rankupTitleStay + " " + Config.rankupTitleFadeOut
        );
        server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withSuppressedOutput(),
                "title " + playerName + " title {\"text\":\"RANK UP!\",\"color\":\"gold\",\"bold\":true}"
        );
        server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withSuppressedOutput(),
                "title " + playerName + " subtitle {\"text\":\"" + newRank.getDisplayName() + "\",\"color\":\"yellow\"}"
        );

        // Sound
        try {
            ResourceLocation soundRL = ResourceLocation.tryParse(Config.rankupSound);
            if (soundRL != null) {
                SoundEvent sound = SoundEvent.createVariableRangeEvent(soundRL);
                player.playNotifySound(sound, SoundSource.MASTER, (float) Config.rankupSoundVolume, (float) Config.rankupSoundPitch);
            }
        } catch (Exception e) {
            LOGGER.warn("[Playtime] Failed to play rank-up sound: {}", e.getMessage());
        }

        // Force LP sync for tab list update
        if (Config.luckpermsForceSync) {
            server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack().withSuppressedOutput(),
                    "lp sync"
            );
        }
    }

    /** Force recalculate and sync rank for a player (admin use). */
    public void forceResync(MinecraftServer server, UUID playerUuid) {
        PlayerRecord record = repository.getPlayer(playerUuid);
        if (record == null) return;

        RankDefinition correctRank = getCurrentRank(record.getTotalPlaytimeTicks());
        RankDefinition oldRank = record.getCurrentRankId() != null ? rankConfig.getRankById(record.getCurrentRankId()) : null;

        // Remove ALL rank groups first, then add the correct one
        for (RankDefinition rank : rankConfig.getRanks()) {
            if (oldRank == null || !rank.getId().equals(correctRank.getId())) {
                luckPerms.removeGroup(playerUuid, rank);
            }
        }

        // Apply the correct rank directly (admin force)
        applyRankClaim(server, playerUuid, oldRank, correctRank);

        if (Config.luckpermsForceSync) {
            server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack().withSuppressedOutput(),
                    "lp sync"
            );
        }
    }
}

