package com.enviouse.playtime.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses human-friendly time strings like "1h30m", "2d4h", "45m", "90s"
 * into game ticks (20 ticks per second).
 */
public final class TimeParser {

    private static final Pattern TIME_PATTERN = Pattern.compile(
            "(?:(\\d+)d)?\\s*(?:(\\d+)h)?\\s*(?:(\\d+)m)?\\s*(?:(\\d+)s)?",
            Pattern.CASE_INSENSITIVE
    );

    private TimeParser() {}

    /**
     * Parse a time string into ticks.
     * Supports formats: "1d2h30m10s", "2h", "30m", "1h30m", etc.
     * Also accepts a plain number as hours (backward compat with old admin commands).
     *
     * @param input the time string
     * @return ticks (always ≥ 0)
     * @throws IllegalArgumentException if format is invalid
     */
    public static long parseTicks(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Time string cannot be empty");
        }

        String trimmed = input.trim();

        // If it's a plain number, treat as hours (backward compat)
        try {
            double hours = Double.parseDouble(trimmed);
            return Math.max(0, (long) (hours * 3600 * 20));
        } catch (NumberFormatException ignored) {}

        Matcher matcher = TIME_PATTERN.matcher(trimmed);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid time format: '" + input + "'. Use e.g. 1d2h30m10s");
        }

        long days    = matcher.group(1) != null ? Long.parseLong(matcher.group(1)) : 0;
        long hours   = matcher.group(2) != null ? Long.parseLong(matcher.group(2)) : 0;
        long minutes = matcher.group(3) != null ? Long.parseLong(matcher.group(3)) : 0;
        long seconds = matcher.group(4) != null ? Long.parseLong(matcher.group(4)) : 0;

        if (days == 0 && hours == 0 && minutes == 0 && seconds == 0) {
            throw new IllegalArgumentException("Invalid time format: '" + input + "'. Use e.g. 1d2h30m10s");
        }

        long totalSeconds = (days * 86400) + (hours * 3600) + (minutes * 60) + seconds;
        return totalSeconds * 20L;
    }

    /**
     * Parse a time string into whole days.
     * Supports the same formats as {@link #parseTicks(String)}.
     * Partial days are rounded down (floor).
     *
     * @param input the time string
     * @return days (always ≥ 0)
     * @throws IllegalArgumentException if format is invalid
     */
    public static int parseDays(String input) {
        long ticks = parseTicks(input);
        // 1 day = 24h = 24 * 3600 * 20 = 1,728,000 ticks
        return (int) (ticks / 1_728_000L);
    }

    /**
     * Format ticks into a human-readable string like "12h 34m 56s".
     */
    public static String formatTicks(long ticks) {
        long totalSeconds = ticks / 20;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return hours + "h " + minutes + "m " + seconds + "s";
        } else if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        } else {
            return seconds + "s";
        }
    }
}

