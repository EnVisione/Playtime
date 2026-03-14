package com.enviouse.playtime.network;

import com.enviouse.playtime.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Lightweight server-to-client packet that pushes a player's AFK state change.
 * Sent to ALL online players so every open PlaytimeScreen updates in real-time.
 * Payload: UUID (16 bytes) + boolean (1 byte) = 17 bytes total.
 */
public class AfkSyncS2CPacket {

    private final UUID playerUuid;
    private final boolean afk;

    public AfkSyncS2CPacket(UUID playerUuid, boolean afk) {
        this.playerUuid = playerUuid;
        this.afk = afk;
    }

    public AfkSyncS2CPacket(FriendlyByteBuf buf) {
        this.playerUuid = buf.readUUID();
        this.afk = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(playerUuid);
        buf.writeBoolean(afk);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        ClientPacketHandler.handleAfkSync(playerUuid, afk)
                )
        );
        ctx.setPacketHandled(true);
    }
}

