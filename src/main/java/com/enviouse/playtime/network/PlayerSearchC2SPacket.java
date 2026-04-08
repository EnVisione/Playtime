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
 * <p>
 * Supports pagination via the {@code offset} field — when the client clicks
 * "Load More", it sends the current list size as the offset so the server
 * skips already-sent entries.
 */
public class PlayerSearchC2SPacket {

    /** Maximum allowed query length (characters). */
    public static final int MAX_QUERY_LENGTH = 50;

    /** Per-player cooldown tracking (UUID → last request timestamp). */
    private static final Map<UUID, Long> COOLDOWNS = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 500; // 500ms

    private final String query;
    private final boolean onlineOnly;
    private final int offset; // number of results already loaded (skip these)

    public PlayerSearchC2SPacket(String query, boolean onlineOnly) {
        this(query, onlineOnly, 0);
    }

    public PlayerSearchC2SPacket(String query, boolean onlineOnly, int offset) {
        // Cap query length to prevent abuse
        this.query = query != null && query.length() > MAX_QUERY_LENGTH
                ? query.substring(0, MAX_QUERY_LENGTH) : (query != null ? query : "");
        this.onlineOnly = onlineOnly;
        this.offset = Math.max(0, offset);
    }

    public PlayerSearchC2SPacket(FriendlyByteBuf buf) {
        this.query = buf.readUtf(MAX_QUERY_LENGTH);
        this.onlineOnly = buf.readBoolean();
        this.offset = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(query, MAX_QUERY_LENGTH);
        buf.writeBoolean(onlineOnly);
        buf.writeVarInt(offset);
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

            PlaytimeCommand.sendSearchResults(player, query, onlineOnly, offset);
        });
        ctx.setPacketHandled(true);
    }
}
