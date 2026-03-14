package com.enviouse.playtime.data;

import java.util.UUID;

/**
 * Persistent per-player record keyed by UUID.
 * Serialised to / from JSON by Gson.
 */
public class PlayerRecord {

    private UUID uuid;
    private String lastUsername;
    private long totalPlaytimeTicks;
    private String currentRankId;
    private long firstJoinEpochMs;
    private long lastSeenEpochMs;
    private long claimsWipedAtMs;
    private long claimsWipeLastSeenMs;
    private int dataVersion;
    private String displayRank;         // cosmetic rank title shown instead of actual rank (empty = use actual)

    public PlayerRecord() {
        this.dataVersion = 1;
        this.displayRank = "";
    }

    public PlayerRecord(UUID uuid, String lastUsername) {
        this.uuid = uuid;
        this.lastUsername = lastUsername;
        this.totalPlaytimeTicks = 0;
        this.currentRankId = null;  // will be resolved by RankEngine
        this.firstJoinEpochMs = System.currentTimeMillis();
        this.lastSeenEpochMs = System.currentTimeMillis();
        this.claimsWipedAtMs = 0;
        this.claimsWipeLastSeenMs = 0;
        this.dataVersion = 1;
        this.displayRank = "";
    }

    // ── Getters & Setters ──────────────────────────────────────────────────────

    public UUID getUuid() { return uuid; }
    public void setUuid(UUID uuid) { this.uuid = uuid; }

    public String getLastUsername() { return lastUsername; }
    public void setLastUsername(String lastUsername) { this.lastUsername = lastUsername; }

    public long getTotalPlaytimeTicks() { return totalPlaytimeTicks; }
    public void setTotalPlaytimeTicks(long totalPlaytimeTicks) { this.totalPlaytimeTicks = totalPlaytimeTicks; }

    public String getCurrentRankId() { return currentRankId; }
    public void setCurrentRankId(String currentRankId) { this.currentRankId = currentRankId; }

    public long getFirstJoinEpochMs() { return firstJoinEpochMs; }
    public void setFirstJoinEpochMs(long firstJoinEpochMs) { this.firstJoinEpochMs = firstJoinEpochMs; }

    public long getLastSeenEpochMs() { return lastSeenEpochMs; }
    public void setLastSeenEpochMs(long lastSeenEpochMs) { this.lastSeenEpochMs = lastSeenEpochMs; }

    public long getClaimsWipedAtMs() { return claimsWipedAtMs; }
    public void setClaimsWipedAtMs(long claimsWipedAtMs) { this.claimsWipedAtMs = claimsWipedAtMs; }

    public long getClaimsWipeLastSeenMs() { return claimsWipeLastSeenMs; }
    public void setClaimsWipeLastSeenMs(long claimsWipeLastSeenMs) { this.claimsWipeLastSeenMs = claimsWipeLastSeenMs; }

    public int getDataVersion() { return dataVersion; }
    public void setDataVersion(int dataVersion) { this.dataVersion = dataVersion; }

    /** Cosmetic display rank title. Empty string means use the actual rank name. */
    public String getDisplayRank() { return displayRank != null ? displayRank : ""; }
    public void setDisplayRank(String displayRank) { this.displayRank = displayRank != null ? displayRank : ""; }

    /** Add ticks to total and return new total. */
    public long addPlaytimeTicks(long ticks) {
        this.totalPlaytimeTicks += ticks;
        return this.totalPlaytimeTicks;
    }

    @Override
    public String toString() {
        return "PlayerRecord{uuid=" + uuid + ", name='" + lastUsername + "', ticks=" + totalPlaytimeTicks + ", rank='" + currentRankId + "'}";
    }
}

