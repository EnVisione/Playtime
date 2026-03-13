package com.enviouse.playtime.network;

import com.enviouse.playtime.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server-to-client packet carrying all playtime stats needed to render the GUI.
 */
public class PlaytimeDataS2CPacket {

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

    public PlaytimeDataS2CPacket(String playerName, UUID playerUuid, long totalTicks,
                                  String currentRankName, String currentRankColor,
                                  String nextRankName, String nextRankColor,
                                  long ticksToNextRank, boolean isAfk,
                                  int claims, int forceloads, int inactivityDays,
                                  boolean claimsEnabled, boolean forceloadsEnabled,
                                  boolean isMaxRank) {
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
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        // consumerMainThread already enqueues work and sets packet handled
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
            ClientPacketHandler.openPlaytimeScreen(this));
    }

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
}

