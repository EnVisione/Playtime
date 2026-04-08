package com.enviouse.playtime.network;

import com.enviouse.playtime.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server-to-client packet carrying filtered player search results.
 * Sent in response to {@link PlayerSearchC2SPacket} or as part of a refresh
 * when the client has an active search query.
 * <p>
 * Re-uses {@link PlaytimeDataS2CPacket.PlayerListEntry} for the entries.
 * Includes the query string echoed back so the client can discard stale responses.
 */
public class PlayerSearchResultS2CPacket {

    /** Maximum number of results per packet. */
    public static final int MAX_RESULTS = 200;

    private final String query;
    private final boolean onlineOnly;
    private final List<PlaytimeDataS2CPacket.PlayerListEntry> results;
    private final int totalMatches;

    public PlayerSearchResultS2CPacket(String query, boolean onlineOnly,
                                        List<PlaytimeDataS2CPacket.PlayerListEntry> results,
                                        int totalMatches) {
        this.query = query != null ? query : "";
        this.onlineOnly = onlineOnly;
        this.results = results;
        this.totalMatches = totalMatches;
    }

    public PlayerSearchResultS2CPacket(FriendlyByteBuf buf) {
        this.query = buf.readUtf(50);
        this.onlineOnly = buf.readBoolean();
        this.totalMatches = buf.readInt();
        int count = buf.readInt();
        this.results = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            results.add(new PlaytimeDataS2CPacket.PlayerListEntry(
                    buf.readUtf(), buf.readUUID(), buf.readLong(),
                    buf.readUtf(), buf.readUtf(), buf.readByte(),
                    buf.readLong(), buf.readLong(), buf.readUtf(), buf.readUtf()));
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(query, 50);
        buf.writeBoolean(onlineOnly);
        buf.writeInt(totalMatches);
        buf.writeInt(results.size());
        for (PlaytimeDataS2CPacket.PlayerListEntry p : results) {
            buf.writeUtf(p.name);
            buf.writeUUID(p.uuid);
            buf.writeLong(p.totalTicks);
            buf.writeUtf(p.rankName);
            buf.writeUtf(p.rankColor);
            buf.writeByte(p.status);
            buf.writeLong(p.firstJoinMs);
            buf.writeLong(p.lastSeenMs);
            buf.writeUtf(p.displayRank);
            buf.writeUtf(p.skinUrl);
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientPacketHandler.handleSearchResults(this));
    }

    // ── Getters ─────────────────────────────────────────────────────────────────
    public String getQuery() { return query; }
    public boolean isOnlineOnly() { return onlineOnly; }
    public List<PlaytimeDataS2CPacket.PlayerListEntry> getResults() { return results; }
    public int getTotalMatches() { return totalMatches; }
}

