package com.enviouse.playtime.network;

import com.enviouse.playtime.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server-to-client packet carrying all playtime stats and top 3 leaderboard data.
 */
public class PlaytimeDataS2CPacket {

    // ── Player stats ────────────────────────────────────────────────────────────
    private final String playerName;
    private final UUID playerUuid;
    private final long totalTicks;
    private final String currentRankName;
    private final String currentRankColor;
    private final String nextRankName;
    private final String nextRankColor;
    private final long ticksToNextRank;
    private final boolean isAfk;
    private final int claims;
    private final int forceloads;
    private final int inactivityDays;
    private final boolean claimsEnabled;
    private final boolean forceloadsEnabled;
    private final boolean isMaxRank;

    // ── Top 3 leaderboard ───────────────────────────────────────────────────────
    private final int top3Count;
    private final String[] top3Names;
    private final UUID[] top3Uuids;
    private final long[] top3Ticks;
    private final String[] top3RankNames;
    private final String[] top3RankColors;

    public PlaytimeDataS2CPacket(String playerName, UUID playerUuid, long totalTicks,
                                  String currentRankName, String currentRankColor,
                                  String nextRankName, String nextRankColor,
                                  long ticksToNextRank, boolean isAfk,
                                  int claims, int forceloads, int inactivityDays,
                                  boolean claimsEnabled, boolean forceloadsEnabled,
                                  boolean isMaxRank,
                                  int top3Count, String[] top3Names, UUID[] top3Uuids,
                                  long[] top3Ticks, String[] top3RankNames, String[] top3RankColors) {
        this.playerName = playerName;
        this.playerUuid = playerUuid;
        this.totalTicks = totalTicks;
        this.currentRankName = currentRankName;
        this.currentRankColor = currentRankColor;
        this.nextRankName = nextRankName;
        this.nextRankColor = nextRankColor;
        this.ticksToNextRank = ticksToNextRank;
        this.isAfk = isAfk;
        this.claims = claims;
        this.forceloads = forceloads;
        this.inactivityDays = inactivityDays;
        this.claimsEnabled = claimsEnabled;
        this.forceloadsEnabled = forceloadsEnabled;
        this.isMaxRank = isMaxRank;
        this.top3Count = top3Count;
        this.top3Names = top3Names;
        this.top3Uuids = top3Uuids;
        this.top3Ticks = top3Ticks;
        this.top3RankNames = top3RankNames;
        this.top3RankColors = top3RankColors;
    }

    public PlaytimeDataS2CPacket(FriendlyByteBuf buf) {
        this.playerName = buf.readUtf();
        this.playerUuid = buf.readUUID();
        this.totalTicks = buf.readLong();
        this.currentRankName = buf.readUtf();
        this.currentRankColor = buf.readUtf();
        this.nextRankName = buf.readUtf();
        this.nextRankColor = buf.readUtf();
        this.ticksToNextRank = buf.readLong();
        this.isAfk = buf.readBoolean();
        this.claims = buf.readInt();
        this.forceloads = buf.readInt();
        this.inactivityDays = buf.readInt();
        this.claimsEnabled = buf.readBoolean();
        this.forceloadsEnabled = buf.readBoolean();
        this.isMaxRank = buf.readBoolean();
        // Top 3
        this.top3Count = buf.readInt();
        this.top3Names = new String[3];
        this.top3Uuids = new UUID[3];
        this.top3Ticks = new long[3];
        this.top3RankNames = new String[3];
        this.top3RankColors = new String[3];
        for (int i = 0; i < top3Count; i++) {
            top3Names[i] = buf.readUtf();
            top3Uuids[i] = buf.readUUID();
            top3Ticks[i] = buf.readLong();
            top3RankNames[i] = buf.readUtf();
            top3RankColors[i] = buf.readUtf();
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(playerName);
        buf.writeUUID(playerUuid);
        buf.writeLong(totalTicks);
        buf.writeUtf(currentRankName);
        buf.writeUtf(currentRankColor);
        buf.writeUtf(nextRankName);
        buf.writeUtf(nextRankColor);
        buf.writeLong(ticksToNextRank);
        buf.writeBoolean(isAfk);
        buf.writeInt(claims);
        buf.writeInt(forceloads);
        buf.writeInt(inactivityDays);
        buf.writeBoolean(claimsEnabled);
        buf.writeBoolean(forceloadsEnabled);
        buf.writeBoolean(isMaxRank);
        // Top 3
        buf.writeInt(top3Count);
        for (int i = 0; i < top3Count; i++) {
            buf.writeUtf(top3Names[i]);
            buf.writeUUID(top3Uuids[i]);
            buf.writeLong(top3Ticks[i]);
            buf.writeUtf(top3RankNames[i]);
            buf.writeUtf(top3RankColors[i]);
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
            ClientPacketHandler.openPlaytimeScreen(this));
    }

    // ── Player stat getters ─────────────────────────────────────────────────────
    public String getPlayerName() { return playerName; }
    public UUID getPlayerUuid() { return playerUuid; }
    public long getTotalTicks() { return totalTicks; }
    public String getCurrentRankName() { return currentRankName; }
    public String getCurrentRankColor() { return currentRankColor; }
    public String getNextRankName() { return nextRankName; }
    public String getNextRankColor() { return nextRankColor; }
    public long getTicksToNextRank() { return ticksToNextRank; }
    public boolean isAfk() { return isAfk; }
    public int getClaims() { return claims; }
    public int getForceloads() { return forceloads; }
    public int getInactivityDays() { return inactivityDays; }
    public boolean isClaimsEnabled() { return claimsEnabled; }
    public boolean isForceloadsEnabled() { return forceloadsEnabled; }
    public boolean isMaxRank() { return isMaxRank; }

    // ── Top 3 getters ───────────────────────────────────────────────────────────
    public int getTop3Count() { return top3Count; }
    public String[] getTop3Names() { return top3Names; }
    public UUID[] getTop3Uuids() { return top3Uuids; }
    public long[] getTop3Ticks() { return top3Ticks; }
    public String[] getTop3RankNames() { return top3RankNames; }
    public String[] getTop3RankColors() { return top3RankColors; }
}
