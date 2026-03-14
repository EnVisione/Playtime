package com.enviouse.playtime.network;

import com.enviouse.playtime.Config;
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

import java.util.UUID;
import java.util.function.Supplier;

/**
 * C2S packet: operator assigns a specific rank to a target player.
 * Sets the player's playtime to the rank threshold and applies the rank.
 */
public class AdminSetRankC2SPacket {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final UUID targetUuid;
    private final String rankId;

    public AdminSetRankC2SPacket(UUID targetUuid, String rankId) {
        this.targetUuid = targetUuid;
        this.rankId = rankId;
    }

    public AdminSetRankC2SPacket(FriendlyByteBuf buf) {
        this.targetUuid = buf.readUUID();
        this.rankId = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(targetUuid);
        buf.writeUtf(rankId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender == null) return;

            if (!sender.hasPermissions(Config.adminPermissionLevel)) {
                sender.sendSystemMessage(Component.literal("\u00A7cYou don't have permission to do this."));
                return;
            }

            PlayerDataRepository repo = Playtime.getRepository();
            RankEngine engine = Playtime.getRankEngine();
            RankConfig rankConfig = Playtime.getRankConfig();
            SessionTracker tracker = Playtime.getSessionTracker();

            if (repo == null || !repo.isLoaded() || engine == null || rankConfig == null) {
                sender.sendSystemMessage(Component.literal("\u00A7cPlaytime system not ready."));
                return;
            }

            RankDefinition targetRank = rankConfig.getRankById(rankId);
            if (targetRank == null) {
                sender.sendSystemMessage(Component.literal("\u00A7cUnknown rank: " + rankId));
                return;
            }

            PlayerRecord record = repo.getPlayer(targetUuid);
            if (record == null) {
                sender.sendSystemMessage(Component.literal("\u00A7cPlayer not found."));
                return;
            }

            if (tracker != null) tracker.flushAll(sender.getServer());

            // Set playtime to rank threshold so the rank applies
            long threshold = targetRank.getThresholdTicks();
            if (record.getTotalPlaytimeTicks() < threshold) {
                record.setTotalPlaytimeTicks(threshold);
            }
            engine.checkAndApplyProgression(sender.getServer(), targetUuid,
                    record.getTotalPlaytimeTicks());
            repo.save(false);

            String targetName = record.getLastUsername() != null ? record.getLastUsername() : targetUuid.toString().substring(0, 8);
            LOGGER.info("[Playtime] {} set rank '{}' for {}", sender.getGameProfile().getName(), rankId, targetName);

            PlaytimeCommand.sendPlaytimePacket(sender);
        });
        ctx.setPacketHandled(true);
    }
}

