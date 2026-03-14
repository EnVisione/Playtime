package com.enviouse.playtime.network;

import com.enviouse.playtime.Playtime;
import com.enviouse.playtime.command.PlaytimeCommand;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Client-to-server packet requesting a fresh PlaytimeDataS2CPacket.
 * Sent periodically by the PlaytimeScreen to keep the GUI data live.
 * Rate-limited to one request per 2 seconds per player.
 */
public class RequestRefreshC2SPacket {

    /** Per-player cooldown tracking (UUID → last request timestamp). */
    private static final Map<UUID, Long> COOLDOWNS = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 2000; // 2 seconds

    public RequestRefreshC2SPacket() {}

    public RequestRefreshC2SPacket(FriendlyByteBuf buf) {
        // empty payload
    }

    public void encode(FriendlyByteBuf buf) {
        // empty payload
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            if (Playtime.getRepository() == null || !Playtime.getRepository().isLoaded()) return;

            // Rate-limit: reject if last request was less than 2 seconds ago
            UUID uuid = player.getUUID();
            long now = System.currentTimeMillis();
            Long lastRequest = COOLDOWNS.get(uuid);
            if (lastRequest != null && (now - lastRequest) < COOLDOWN_MS) {
                return; // silently drop
            }
            COOLDOWNS.put(uuid, now);

            PlaytimeCommand.sendPlaytimePacket(player);
        });
        ctx.setPacketHandled(true);
    }
}

