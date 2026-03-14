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
 * Server-to-client packet carrying playtime stats, top 3 leaderboard, and full rank list.
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
    private final boolean isOperator; // whether the viewing player is an operator
    private final String displayRank; // cosmetic display rank (empty = use actual rank)

    // ── Top 3 leaderboard ───────────────────────────────────────────────────────
    private final int top3Count;
    private final String[] top3Names;
    private final UUID[] top3Uuids;
    private final long[] top3Ticks;
    private final String[] top3RankNames;
    private final String[] top3RankColors;
    private final boolean[] top3IsAfk;

    // ── Full rank list (for the ranks panel) ────────────────────────────────────
    private final List<RankEntry> allRanks;

    // ── Full player list (for the list view) ────────────────────────────────────
    private final List<PlayerListEntry> playerList;

    /** Lightweight rank data for client-side rendering. */
    public static class RankEntry {
        public final String id;
        public final String displayName;
        public final String color;
        public final long thresholdTicks;
        public final String defaultItem;
        public final int claims;
        public final int forceloads;
        public final int inactivityDays;
        public final boolean earned;   // player has enough ticks for this rank
        public final boolean claimed;  // rank has been applied/claimed

        public RankEntry(String id, String displayName, String color, long thresholdTicks,
                         String defaultItem, int claims, int forceloads, int inactivityDays,
                         boolean earned, boolean claimed) {
            this.id = id;
            this.displayName = displayName;
            this.color = color;
            this.thresholdTicks = thresholdTicks;
            this.defaultItem = defaultItem;
            this.claims = claims;
            this.forceloads = forceloads;
            this.inactivityDays = inactivityDays;
            this.earned = earned;
            this.claimed = claimed;
        }
    }

    /** Player entry for the full server list view. */
    public static class PlayerListEntry {
        public final String name;
        public final UUID uuid;
        public long totalTicks;
        public final String rankName;
        public final String rankColor;
        public byte status; // 0=online, 1=afk, 2=offline (non-final for client-side AFK updates)
        public final long firstJoinMs;
        public final long lastSeenMs;
        public final String displayRank; // cosmetic display rank (empty = use actual rank)

        public PlayerListEntry(String name, UUID uuid, long totalTicks,
                               String rankName, String rankColor, byte status,
                               long firstJoinMs, long lastSeenMs, String displayRank) {
            this.name = name;
            this.uuid = uuid;
            this.totalTicks = totalTicks;
            this.rankName = rankName;
            this.rankColor = rankColor;
            this.status = status;
            this.firstJoinMs = firstJoinMs;
            this.lastSeenMs = lastSeenMs;
            this.displayRank = displayRank != null ? displayRank : "";
        }
    }

    public PlaytimeDataS2CPacket(String playerName, UUID playerUuid, long totalTicks,
                                  String currentRankName, String currentRankColor,
                                  String nextRankName, String nextRankColor,
                                  long ticksToNextRank, boolean isAfk,
                                  int claims, int forceloads, int inactivityDays,
                                  boolean claimsEnabled, boolean forceloadsEnabled,
                                  boolean isMaxRank, boolean isOperator, String displayRank,
                                  int top3Count, String[] top3Names, UUID[] top3Uuids,
                                  long[] top3Ticks, String[] top3RankNames, String[] top3RankColors,
                                  boolean[] top3IsAfk,
                                  List<RankEntry> allRanks,
                                  List<PlayerListEntry> playerList) {
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
        this.isOperator = isOperator;
        this.displayRank = displayRank != null ? displayRank : "";
        this.top3Count = top3Count;
        this.top3Names = top3Names;
        this.top3Uuids = top3Uuids;
        this.top3Ticks = top3Ticks;
        this.top3RankNames = top3RankNames;
        this.top3RankColors = top3RankColors;
        this.top3IsAfk = top3IsAfk;
        this.allRanks = allRanks;
        this.playerList = playerList;
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
        this.isOperator = buf.readBoolean();
        this.displayRank = buf.readUtf();
        // Top 3
        this.top3Count = buf.readInt();
        this.top3Names = new String[3];
        this.top3Uuids = new UUID[3];
        this.top3Ticks = new long[3];
        this.top3RankNames = new String[3];
        this.top3RankColors = new String[3];
        this.top3IsAfk = new boolean[3];
        for (int i = 0; i < top3Count; i++) {
            top3Names[i] = buf.readUtf();
            top3Uuids[i] = buf.readUUID();
            top3Ticks[i] = buf.readLong();
            top3RankNames[i] = buf.readUtf();
            top3RankColors[i] = buf.readUtf();
            top3IsAfk[i] = buf.readBoolean();
        }
        // Full rank list
        int rankCount = buf.readInt();
        this.allRanks = new ArrayList<>(rankCount);
        for (int i = 0; i < rankCount; i++) {
            allRanks.add(new RankEntry(
                    buf.readUtf(), buf.readUtf(), buf.readUtf(),
                    buf.readLong(), buf.readUtf(),
                    buf.readInt(), buf.readInt(), buf.readInt(),
                    buf.readBoolean(), buf.readBoolean()));
        }
        // Player list
        int plCount = buf.readInt();
        this.playerList = new ArrayList<>(plCount);
        for (int i = 0; i < plCount; i++) {
            playerList.add(new PlayerListEntry(
                    buf.readUtf(), buf.readUUID(), buf.readLong(),
                    buf.readUtf(), buf.readUtf(), buf.readByte(),
                    buf.readLong(), buf.readLong(), buf.readUtf()));
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
        buf.writeBoolean(isOperator);
        buf.writeUtf(displayRank);
        // Top 3
        buf.writeInt(top3Count);
        for (int i = 0; i < top3Count; i++) {
            buf.writeUtf(top3Names[i]);
            buf.writeUUID(top3Uuids[i]);
            buf.writeLong(top3Ticks[i]);
            buf.writeUtf(top3RankNames[i]);
            buf.writeUtf(top3RankColors[i]);
            buf.writeBoolean(top3IsAfk[i]);
        }
        // Full rank list
        buf.writeInt(allRanks.size());
        for (RankEntry r : allRanks) {
            buf.writeUtf(r.id);
            buf.writeUtf(r.displayName);
            buf.writeUtf(r.color);
            buf.writeLong(r.thresholdTicks);
            buf.writeUtf(r.defaultItem);
            buf.writeInt(r.claims);
            buf.writeInt(r.forceloads);
            buf.writeInt(r.inactivityDays);
            buf.writeBoolean(r.earned);
            buf.writeBoolean(r.claimed);
        }
        // Player list
        buf.writeInt(playerList.size());
        for (PlayerListEntry p : playerList) {
            buf.writeUtf(p.name);
            buf.writeUUID(p.uuid);
            buf.writeLong(p.totalTicks);
            buf.writeUtf(p.rankName);
            buf.writeUtf(p.rankColor);
            buf.writeByte(p.status);
            buf.writeLong(p.firstJoinMs);
            buf.writeLong(p.lastSeenMs);
            buf.writeUtf(p.displayRank);
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
    public boolean isOperator() { return isOperator; }
    public String getDisplayRank() { return displayRank; }

    // ── Top 3 getters ───────────────────────────────────────────────────────────
    public int getTop3Count() { return top3Count; }
    public String[] getTop3Names() { return top3Names; }
    public UUID[] getTop3Uuids() { return top3Uuids; }
    public long[] getTop3Ticks() { return top3Ticks; }
    public String[] getTop3RankNames() { return top3RankNames; }
    public String[] getTop3RankColors() { return top3RankColors; }
    public boolean[] getTop3IsAfk() { return top3IsAfk; }

    // ── Rank list getter ────────────────────────────────────────────────────────
    public List<RankEntry> getAllRanks() { return allRanks; }
    public List<PlayerListEntry> getPlayerList() { return playerList; }
}
