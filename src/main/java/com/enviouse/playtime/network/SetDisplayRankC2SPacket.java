package com.enviouse.playtime.network;

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
    private static final int DISPLAY_RANK_MIN_ORDER = 13; // Technician
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

            // Check if player has Technician+ rank
            String currentRankId = record.getCurrentRankId();
            if (currentRankId == null) return;
            RankDefinition currentRank = Playtime.getRankConfig().getRankById(currentRankId);
            if (currentRank == null || currentRank.getSortOrder() < DISPLAY_RANK_MIN_ORDER) {
                player.sendSystemMessage(Component.literal("§cYou must reach Technician rank or higher to set a display rank."));
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

            // Set LP suffix with underline formatting + reset (priority 50)
            if (lp != null && lp.isAvailable()) {
                lp.setSuffix(player.getUUID(), LP_SUFFIX_PRIORITY, " &n" + displayName + " &r");
            }

            player.sendSystemMessage(Component.literal("§aDisplay rank set to: §n" + displayName));
            // Refresh GUI
            PlaytimeCommand.sendPlaytimePacket(player);
        });
        ctx.setPacketHandled(true);
    }
}

