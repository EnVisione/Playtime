package com.enviouse.playtime.service;

import com.enviouse.playtime.Config;
import com.enviouse.playtime.config.RankConfig;
import com.enviouse.playtime.data.PlayerDataRepository;
import com.enviouse.playtime.data.PlayerRecord;
import com.enviouse.playtime.data.RankDefinition;
import com.enviouse.playtime.integration.LuckPermsService;
import com.enviouse.playtime.util.ColorUtil;
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
import java.util.UUID;

/**
 * Calculates ranks from playtime and applies rank-up effects.
 */
public class RankEngine {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final RankConfig rankConfig;
    private final PlayerDataRepository repository;
    private final LuckPermsService luckPerms;

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
     * Check if a player's rank should change based on their ticks.
     * If it changed, apply rank-up effects and persist.
     */
    public void checkAndApplyProgression(MinecraftServer server, UUID playerUuid, long totalTicks) {
        PlayerRecord record = repository.getPlayer(playerUuid);
        if (record == null) return;

        RankDefinition newRank = getCurrentRank(totalTicks);
        String storedRankId = record.getCurrentRankId();

        if (storedRankId != null && storedRankId.equals(newRank.getId())) {
            return; // no change
        }

        RankDefinition oldRank = storedRankId != null ? rankConfig.getRankById(storedRankId) : null;

        // Update stored rank
        record.setCurrentRankId(newRank.getId());
        repository.markDirty();

        // Sync LuckPerms groups
        luckPerms.syncRank(playerUuid, oldRank, newRank, server);

        // Apply rank-up effects (only if server player is online)
        ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
        if (player != null) {
            applyRankUpEffects(server, player, oldRank, newRank);
        }
    }

    /** Apply visual/audio rank-up effects. */
    private void applyRankUpEffects(MinecraftServer server, ServerPlayer player,
                                     @Nullable RankDefinition oldRank, RankDefinition newRank) {
        String playerName = player.getGameProfile().getName();

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

        record.setCurrentRankId(correctRank.getId());
        repository.markDirty();
        luckPerms.addGroup(playerUuid, correctRank);

        if (Config.luckpermsForceSync) {
            server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack().withSuppressedOutput(),
                    "lp sync"
            );
        }
    }
}

