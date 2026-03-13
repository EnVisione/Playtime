package com.enviouse.playtime.data;

import java.util.UUID;

/**
 * Represents a command to execute when a player exceeds the specified inactivity period.
 * Supports placeholders: {uuid}, {player}, {rank}.
 */
public class InactivityAction {

    private String command;
    private int delayDays;

    public InactivityAction() {
    }

    public InactivityAction(String command, int delayDays) {
        this.command = command;
        this.delayDays = delayDays;
    }

    // ── Getters ────────────────────────────────────────────────────────────────

    public String getCommand() { return command; }
    public int getDelayDays() { return delayDays; }

    // ── Setters ────────────────────────────────────────────────────────────────

    public void setCommand(String command) { this.command = command; }
    public void setDelayDays(int delayDays) { this.delayDays = delayDays; }

    /**
     * Resolve placeholders in the command string.
     *
     * @param uuid       the player's UUID
     * @param playerName the player's last known username
     * @param rankId     the player's current rank ID
     * @return the command string with placeholders replaced
     */
    public String resolveCommand(UUID uuid, String playerName, String rankId) {
        String resolved = command;
        if (resolved == null) return "";
        resolved = resolved.replace("{uuid}", uuid.toString());
        resolved = resolved.replace("{player}", playerName != null ? playerName : uuid.toString());
        resolved = resolved.replace("{rank}", rankId != null ? rankId : "unknown");
        return resolved;
    }

    @Override
    public String toString() {
        return "InactivityAction{command='" + command + "', delayDays=" + delayDays + "}";
    }
}

