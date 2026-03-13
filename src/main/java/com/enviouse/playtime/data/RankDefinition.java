package com.enviouse.playtime.data;

/**
 * Mutable rank definition loaded from the ranks config file.
 * Ranks are ordered by {@code sortOrder} (ascending).
 * Supports unlimited ranks and hex colors (&#RRGGBB format).
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
    private String fallbackColor;     // §-code or &#RRGGBB hex fallback when LP prefix unavailable
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

    // ── Setters ────────────────────────────────────────────────────────────────

    public void setId(String id) { this.id = id; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setVisible(boolean visible) { this.visible = visible; }
    public void setThresholdTicks(long thresholdTicks) { this.thresholdTicks = thresholdTicks; }
    public void setClaims(int claims) { this.claims = claims; }
    public void setForceloads(int forceloads) { this.forceloads = forceloads; }
    public void setInactivityDays(int inactivityDays) { this.inactivityDays = inactivityDays; }
    public void setLuckpermsGroup(String luckpermsGroup) { this.luckpermsGroup = luckpermsGroup; }
    public void setFallbackColor(String fallbackColor) { this.fallbackColor = fallbackColor; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

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
