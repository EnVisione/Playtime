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
 * Client-to-server packet requesting a filtered player list.
 * Sent when the player types in the search bar of the PlaytimeScreen.
 * Rate-limited to one request per 500ms per player.
 */
public class PlayerSearchC2SPacket {

    /** Per-player cooldown tracking (UUID → last request timestamp). */
    private static final Map<UUID, Long> COOLDOWNS = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 500; // 500ms

    private final String query;
    private final boolean onlineOnly;

    public PlayerSearchC2SPacket(String query, boolean onlineOnly) {
        // Cap query length to prevent abuse
        this.query = query != null && query.length() > 50 ? query.substring(0, 50) : (query != null ? query : "");
        this.onlineOnly = onlineOnly;
    }

    public PlayerSearchC2SPacket(FriendlyByteBuf buf) {
        this.query = buf.readUtf(50);
        this.onlineOnly = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(query, 50);
        buf.writeBoolean(onlineOnly);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            if (Playtime.getRepository() == null || !Playtime.getRepository().isLoaded()) return;

            // Rate-limit
            UUID uuid = player.getUUID();
            long now = System.currentTimeMillis();
            Long lastRequest = COOLDOWNS.get(uuid);
            if (lastRequest != null && (now - lastRequest) < COOLDOWN_MS) {
                return; // silently drop
            }
            COOLDOWNS.put(uuid, now);

            PlaytimeCommand.sendSearchResults(player, query, onlineOnly);
        });
        ctx.setPacketHandled(true);
    }
}

