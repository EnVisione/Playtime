package com.enviouse.playtime.network;

import com.enviouse.playtime.Config;
import com.enviouse.playtime.Playtime;
import com.enviouse.playtime.command.PlaytimeCommand;
import com.enviouse.playtime.data.PlayerDataRepository;
import com.enviouse.playtime.data.PlayerRecord;
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
 * C2S packet: operator adds or removes playtime ticks for a target player.
 * Positive ticksDelta = add time, negative = remove time.
 */
public class AdminModifyTimeC2SPacket {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final UUID targetUuid;
    private final long ticksDelta;

    public AdminModifyTimeC2SPacket(UUID targetUuid, long ticksDelta) {
        this.targetUuid = targetUuid;
        this.ticksDelta = ticksDelta;
    }

    public AdminModifyTimeC2SPacket(FriendlyByteBuf buf) {
        this.targetUuid = buf.readUUID();
        this.ticksDelta = buf.readLong();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(targetUuid);
        buf.writeLong(ticksDelta);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender == null) return;

            // Verify operator permission server-side
            if (!sender.hasPermissions(Config.adminPermissionLevel)) {
                sender.sendSystemMessage(Component.literal("\u00A7cYou don't have permission to do this."));
                return;
            }

            PlayerDataRepository repo = Playtime.getRepository();
            RankEngine engine = Playtime.getRankEngine();
            SessionTracker tracker = Playtime.getSessionTracker();

            if (repo == null || !repo.isLoaded() || engine == null) {
                sender.sendSystemMessage(Component.literal("\u00A7cPlaytime system not ready."));
                return;
            }

            PlayerRecord record = repo.getPlayer(targetUuid);
            if (record == null) {
                sender.sendSystemMessage(Component.literal("\u00A7cPlayer not found."));
                return;
            }

            // Flush sessions first
            if (tracker != null) tracker.flushAll(sender.getServer());

            long oldTicks = record.getTotalPlaytimeTicks();
            long newTicks = Math.max(0, oldTicks + ticksDelta);
            record.setTotalPlaytimeTicks(newTicks);
            engine.checkAndApplyProgression(sender.getServer(), targetUuid, newTicks);
            repo.save(false);

            String targetName = record.getLastUsername() != null ? record.getLastUsername() : targetUuid.toString().substring(0, 8);
            LOGGER.info("[Playtime] {} modified playtime for {} by {} ticks (now {})",
                    sender.getGameProfile().getName(), targetName, ticksDelta, newTicks);

            // Refresh the GUI
            PlaytimeCommand.sendPlaytimePacket(sender);
        });
        ctx.setPacketHandled(true);
    }
}

