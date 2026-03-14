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

import java.util.function.Supplier;

/**
 * Client-to-server packet sent when a player clicks to claim an earned rank.
 * The server verifies eligibility and re-sends the data packet to refresh the GUI.
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

            long sessionTicks = tracker != null ? tracker.getSessionTicks(player.getUUID()) : 0;
            long totalTicks = record.getTotalPlaytimeTicks() + sessionTicks;

            if (totalTicks < targetRank.getThresholdTicks()) {
                player.sendSystemMessage(Component.literal("§cYou haven't earned enough playtime for this rank yet."));
                return;
            }

            // Flush session and re-run progression to ensure the rank is applied
            if (tracker != null) {
                tracker.flushAll(player.getServer());
            }
            engine.checkAndApplyProgression(player.getServer(), player.getUUID(),
                    record.getTotalPlaytimeTicks() + (tracker != null ? tracker.getSessionTicks(player.getUUID()) : 0));
            repo.save(false);

            LOGGER.info("[Playtime] Player {} claimed rank '{}'", player.getGameProfile().getName(), rankId);

            // Re-send the playtime data packet to refresh the GUI
            PlaytimeCommand.sendPlaytimePacket(player);
        });
        ctx.setPacketHandled(true);
    }
}

