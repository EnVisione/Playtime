package com.enviouse.playtime.network;

import com.enviouse.playtime.Config;
import com.enviouse.playtime.Playtime;
import com.enviouse.playtime.command.PlaytimeCommand;
import com.enviouse.playtime.data.PlayerDataRepository;
import com.enviouse.playtime.data.PlayerRecord;
import com.enviouse.playtime.data.RankDefinition;
import com.enviouse.playtime.integration.LuckPermsService;
import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;

import java.util.function.Supplier;

/**
 * Client-to-server packet sent when a player selects a display rank from the GUI.
 * Contains the rank ID to set as display rank, or an empty string to clear it.
 */
public class SetDisplayRankC2SPacket {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int LP_SUFFIX_PRIORITY = 50;

    private final String rankId;

    public SetDisplayRankC2SPacket(String rankId) {
        this.rankId = rankId;
    }

    public SetDisplayRankC2SPacket(FriendlyByteBuf buf) {
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
            if (repo == null || !repo.isLoaded()) return;

            PlayerRecord record = repo.getPlayer(player.getUUID());
            if (record == null) return;

            // Check if player has reached the minimum rank for display rank (configurable)
            String currentRankId = record.getCurrentRankId();
            if (currentRankId == null) return;
            RankDefinition currentRank = Playtime.getRankConfig().getRankById(currentRankId);
            if (currentRank == null || !meetsDisplayRankMinimum(currentRank)) {
                String minId = Config.displayRankMinimumId;
                String minName = (minId != null && !minId.isEmpty()) ? minId : "the required";
                player.sendSystemMessage(Component.literal("§cYou must reach " + minName + " rank or higher to set a display rank."));
                return;
            }

            LuckPermsService lp = Playtime.getLuckPerms();

            // Clear display rank
            if (rankId.isEmpty()) {
                record.setDisplayRank("");
                repo.markDirty();
                if (lp != null && lp.isAvailable()) {
                    lp.removeSuffix(player.getUUID(), LP_SUFFIX_PRIORITY);
                }
                player.sendSystemMessage(Component.literal("§aDisplay rank cleared."));
                // Refresh GUI
                PlaytimeCommand.sendPlaytimePacket(player);
                return;
            }

            // Look up the rank definition
            RankDefinition targetRank = Playtime.getRankConfig().getRankById(rankId);
            if (targetRank == null) {
                LOGGER.warn("[Playtime] SetDisplayRank: unknown rank ID '{}'", rankId);
                return;
            }

            // Set display rank to the rank's display name
            String displayName = targetRank.getDisplayName();
            record.setDisplayRank(displayName);
            repo.markDirty();

            // Set LP suffix with the rank's actual colour (priority 50)
            if (lp != null && lp.isAvailable()) {
                String colorStr = targetRank.getFallbackColor();
                lp.setSuffix(player.getUUID(), LP_SUFFIX_PRIORITY,
                        com.enviouse.playtime.util.ColorUtil.buildLPSuffix(colorStr, displayName));
            }

            player.sendSystemMessage(Component.literal("§aDisplay rank set to: §n" + displayName));
            // Refresh GUI
            PlaytimeCommand.sendPlaytimePacket(player);
        });
        ctx.setPacketHandled(true);
    }

    /**
     * Check whether a rank meets the configurable minimum for the display rank feature.
     * Uses Config.displayRankMinimumId to look up the threshold rank's sort order.
     * Returns true if the config value is empty (all ranks allowed).
     */
    private static boolean meetsDisplayRankMinimum(RankDefinition playerRank) {
        String minId = Config.displayRankMinimumId;
        if (minId == null || minId.isEmpty()) return true;
        if (Playtime.getRankConfig() == null) return false;
        RankDefinition threshold = Playtime.getRankConfig().getRankById(minId);
        if (threshold == null) return true; // unknown rank ID → don't block
        return playerRank.getSortOrder() >= threshold.getSortOrder();
    }
}

