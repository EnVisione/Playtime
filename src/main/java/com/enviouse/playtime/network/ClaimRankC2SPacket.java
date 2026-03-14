package com.enviouse.playtime.network;

import com.enviouse.playtime.Playtime;
import com.enviouse.playtime.command.PlaytimeCommand;
import com.enviouse.playtime.config.RankConfig;
import com.enviouse.playtime.data.PlayerDataRepository;
import com.enviouse.playtime.data.PlayerRecord;
import com.enviouse.playtime.data.RankDefinition;
import com.enviouse.playtime.service.RankEngine;
import com.enviouse.playtime.service.SessionTracker;
import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;

import java.util.List;
import java.util.function.Supplier;

/**
 * Client-to-server packet sent when a player clicks to claim an earned rank.
 * The server verifies eligibility, applies the rank (with LP sync),
 * broadcasts the rank-up message, and re-sends the data packet.
 */
public class ClaimRankC2SPacket {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final String rankId;

    public ClaimRankC2SPacket(String rankId) {
        this.rankId = rankId;
    }

    public ClaimRankC2SPacket(FriendlyByteBuf buf) {
        this.rankId = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(rankId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            PlayerDataRepository repo = Playtime.getRepository();
            SessionTracker tracker = Playtime.getSessionTracker();
            RankEngine engine = Playtime.getRankEngine();
            RankConfig rankConfig = Playtime.getRankConfig();

            if (repo == null || !repo.isLoaded() || engine == null || rankConfig == null) {
                player.sendSystemMessage(Component.literal("§cPlaytime system not ready."));
                return;
            }

            RankDefinition targetRank = rankConfig.getRankById(rankId);
            if (targetRank == null) {
                player.sendSystemMessage(Component.literal("§cUnknown rank: " + rankId));
                return;
            }

            PlayerRecord record = repo.getPlayer(player.getUUID());
            if (record == null) {
                player.sendSystemMessage(Component.literal("§cNo playtime data found."));
                return;
            }

            // Flush session first
            if (tracker != null) {
                tracker.flushAll(player.getServer());
            }

            long totalTicks = record.getTotalPlaytimeTicks();

            if (totalTicks < targetRank.getThresholdTicks()) {
                player.sendSystemMessage(Component.literal("§cYou haven't earned enough playtime for this rank yet."));
                return;
            }

            // Determine the old rank (current stored rank)
            String storedRankId = record.getCurrentRankId();
            RankDefinition oldRank = storedRankId != null ? rankConfig.getRankById(storedRankId) : null;

            // Find the highest rank the player has earned (they might skip multiple)
            RankDefinition highestEarned = engine.getCurrentRank(totalTicks);

            // Use whichever is higher: the clicked rank or the highest earned
            RankDefinition claimRank = targetRank;
            if (highestEarned.getSortOrder() > targetRank.getSortOrder()) {
                claimRank = highestEarned;
            }

            // Don't re-claim the same rank
            if (oldRank != null && oldRank.getId().equals(claimRank.getId())) {
                player.sendSystemMessage(Component.literal("§7You already have this rank."));
                // Still refresh GUI
                PlaytimeCommand.sendPlaytimePacket(player);
                return;
            }

            // Apply the rank claim (updates stored rank, syncs LP)
            engine.applyRankClaim(player.getServer(), player.getUUID(), oldRank, claimRank);
            repo.save(false);

            LOGGER.info("[Playtime] Player {} claimed rank '{}' (from '{}')",
                    player.getGameProfile().getName(), claimRank.getId(),
                    oldRank != null ? oldRank.getId() : "none");

            // Re-send the playtime data packet to refresh the GUI
            PlaytimeCommand.sendPlaytimePacket(player);
        });
        ctx.setPacketHandled(true);
    }
}

