package com.enviouse.sef.announcements;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses interval specs like "1S", "30M", "1H", "1H30M15S", "45".
 * Pure numbers are treated as seconds. Units: S, M, H (case-insensitive).
 * Minimum result is 1 second. Returns -1 for invalid input.
 */
public final class DurationParser {
    private static final Pattern SPEC = Pattern.compile("(\\d+)([SsMmHh])");

    private DurationParser() {}

    public static long parseSeconds(String input) {
        if (input == null || input.isBlank()) return -1;
        String trimmed = input.trim();
        try {
            long asInt = Long.parseLong(trimmed);
            return asInt < 1 ? -1 : asInt;
        } catch (NumberFormatException ignored) {}

        Matcher m = SPEC.matcher(trimmed);
        long total = 0;
        int consumed = 0;
        while (m.find()) {
            if (m.start() != consumed) return -1;
            long value;
            try { value = Long.parseLong(m.group(1)); } catch (NumberFormatException e) { return -1; }
            char unit = Character.toLowerCase(m.group(2).charAt(0));
            switch (unit) {
                case 's' -> total += value;
                case 'm' -> total += value * 60L;
                case 'h' -> total += value * 3600L;
                default -> { return -1; }
            }
            consumed = m.end();
        }
        if (consumed != trimmed.length() || total < 1) return -1;
        return total;
    }

    public static String humanReadable(long seconds) {
        if (seconds <= 0) return "0s";
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        StringBuilder sb = new StringBuilder();
        if (h > 0) sb.append(h).append("h");
        if (m > 0) sb.append(m).append("m");
        if (s > 0) sb.append(s).append("s");
        return sb.toString();
    }
}
