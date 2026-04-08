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
 * <p>
 * Optionally carries the current search query so the server can also
 * refresh search results alongside the main data packet.
 */
public class RequestRefreshC2SPacket {

    /** Per-player cooldown tracking (UUID → last request timestamp). */
    private static final Map<UUID, Long> COOLDOWNS = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 2000; // 2 seconds

    private final String searchQuery;
    private final boolean onlineOnly;

    /** No active search — just refresh the main data. */
    public RequestRefreshC2SPacket() {
        this.searchQuery = "";
        this.onlineOnly = false;
    }

    /** Refresh main data AND re-send search results for the active query. */
    public RequestRefreshC2SPacket(String searchQuery, boolean onlineOnly) {
        this.searchQuery = searchQuery != null ? searchQuery : "";
        this.onlineOnly = onlineOnly;
    }

    public RequestRefreshC2SPacket(FriendlyByteBuf buf) {
        this.searchQuery = buf.readUtf(50);
        this.onlineOnly = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(searchQuery, 50);
        buf.writeBoolean(onlineOnly);
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

            // Always refresh main data (stats, top 3, ranks, top 100 players)
            PlaytimeCommand.sendPlaytimePacket(player);

            // If the client has an active search or filter, also refresh those results
            if (!searchQuery.isEmpty() || onlineOnly) {
                PlaytimeCommand.sendSearchResults(player, searchQuery, onlineOnly);
            }
        });
        ctx.setPacketHandled(true);
    }
}
