package com.enviouse.playtime.data;

import javax.annotation.Nullable;

/**
 * Immutable rank definition loaded from the ranks config file.
 * Ranks are ordered by {@code sortOrder} (ascending).
 */
public class RankDefinition implements Comparable<RankDefinition> {

    private String id;
    private String displayName;
    private boolean visible;
    private long thresholdTicks;
    private int claims;
    private int forceloads;
    private int inactivityDays;       // -1 = never expires
    private String luckpermsGroup;    // LP group name to sync, may equal id
    private String fallbackColor;     // §-code fallback when LP prefix unavailable
    private int sortOrder;

    public RankDefinition() {
    }

    public RankDefinition(String id, String displayName, boolean visible, long thresholdTicks,
                          int claims, int forceloads, int inactivityDays,
                          String luckpermsGroup, String fallbackColor, int sortOrder) {
        this.id = id;
        this.displayName = displayName;
        this.visible = visible;
        this.thresholdTicks = thresholdTicks;
        this.claims = claims;
        this.forceloads = forceloads;
        this.inactivityDays = inactivityDays;
        this.luckpermsGroup = luckpermsGroup;
        this.fallbackColor = fallbackColor;
        this.sortOrder = sortOrder;
    }

    // ── Getters ────────────────────────────────────────────────────────────────

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public boolean isVisible() { return visible; }
    public long getThresholdTicks() { return thresholdTicks; }
    public int getClaims() { return claims; }
    public int getForceloads() { return forceloads; }
    public int getInactivityDays() { return inactivityDays; }
    public String getLuckpermsGroup() { return luckpermsGroup; }
    public String getFallbackColor() { return fallbackColor; }
    public int getSortOrder() { return sortOrder; }

    /** Returns threshold in whole hours (for display). */
    public long getThresholdHours() {
        return thresholdTicks / 72_000L;
    }

    @Override
    public int compareTo(RankDefinition other) {
        return Integer.compare(this.sortOrder, other.sortOrder);
    }

    @Override
    public String toString() {
        return "RankDefinition{id='" + id + "', order=" + sortOrder + ", ticks=" + thresholdTicks + "}";
    }
}

