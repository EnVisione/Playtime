package com.enviouse.playtime.data;

import java.util.ArrayList;
import java.util.List;

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
    private int inactivityDays;       // -1 = never expires (legacy, used as fallback)
    private String luckpermsGroup;    // LP group name to sync, may equal id
    private String fallbackColor;     // §-code or &#RRGGBB hex fallback when LP prefix unavailable
    private int sortOrder;
    private Boolean syncWithLuckPerms;   // null treated as true for backward compat
    private String description;          // custom description text shown in /ranks
    private String hoverText;            // hover text shown when mousing over rank in chat
    private List<InactivityAction> inactivityActions; // modular inactivity commands
    private String defaultItem;          // Minecraft item/block ID shown in the GUI box (e.g. "minecraft:diamond")
    private String phaseText;            // flavor text shown to the player when they reach this rank (phase intro)
    private List<String> commands;       // commands to run on rank-up (e.g. ["/give @p diamond"])

    public RankDefinition() {
    }

    public RankDefinition(String id, String displayName, boolean visible, long thresholdTicks,
                          int claims, int forceloads, int inactivityDays,
                          String luckpermsGroup, String fallbackColor, int sortOrder) {
        this(id, displayName, visible, thresholdTicks, claims, forceloads,
                inactivityDays, luckpermsGroup, fallbackColor, sortOrder, null);
    }

    public RankDefinition(String id, String displayName, boolean visible, long thresholdTicks,
                          int claims, int forceloads, int inactivityDays,
                          String luckpermsGroup, String fallbackColor, int sortOrder,
                          String defaultItem) {
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
        this.syncWithLuckPerms = true;
        this.description = null;
        this.hoverText = null;
        this.inactivityActions = new ArrayList<>();
        this.defaultItem = defaultItem;
        this.phaseText = null;
        this.commands = new ArrayList<>();
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

    /** Whether this rank should sync with LuckPerms. Null is treated as true for backward compat. */
    public boolean isSyncWithLuckPerms() { return syncWithLuckPerms == null || syncWithLuckPerms; }
    public Boolean getSyncWithLuckPermsRaw() { return syncWithLuckPerms; }

    /** Custom description for display in /ranks. May be null. */
    public String getDescription() { return description; }

    /** Hover text shown when mousing over this rank in chat. May be null. */
    public String getHoverText() { return hoverText; }

    /** Modular inactivity commands. Never null (may be empty). */
    public List<InactivityAction> getInactivityActions() {
        if (inactivityActions == null) inactivityActions = new ArrayList<>();
        return inactivityActions;
    }

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
    public void setSyncWithLuckPerms(boolean syncWithLuckPerms) { this.syncWithLuckPerms = syncWithLuckPerms; }
    public void setDescription(String description) { this.description = description; }
    public void setHoverText(String hoverText) { this.hoverText = hoverText; }
    public void setInactivityActions(List<InactivityAction> inactivityActions) { this.inactivityActions = inactivityActions; }

    /** Minecraft item/block ID for the GUI box (e.g. "minecraft:diamond"). May be null. */
    public String getDefaultItem() { return defaultItem; }
    public void setDefaultItem(String defaultItem) { this.defaultItem = defaultItem; }

    /** Phase flavor text shown to the player when they first claim this rank. May be null. */
    public String getPhaseText() { return phaseText; }
    public void setPhaseText(String phaseText) { this.phaseText = phaseText; }

    /** Commands to execute on rank-up. Never null (may be empty). */
    public List<String> getCommands() {
        if (commands == null) commands = new ArrayList<>();
        return commands;
    }
    public void setCommands(List<String> commands) { this.commands = commands; }

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
