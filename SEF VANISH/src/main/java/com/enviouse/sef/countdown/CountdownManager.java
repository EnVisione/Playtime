package com.enviouse.sef.countdown;

import com.enviouse.sef.TextFormatter;
import com.enviouse.sef.config.ConfigHandler;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Tick-driven countdown broadcaster.
 *
 * <p>One {@link Countdown} schedules a fixed list of broadcasts (start, every
 * minute mark, 10s, 5..0) and drains them as the server tick counter catches
 * up. Each broadcast lands on every online player as a title (the message)
 * + subtitle (colored remaining-time string), and optionally as a chat line.
 *
 * <p>Multiple countdowns can run concurrently — they each have their own
 * schedule and are removed from {@link #ACTIVE} when their queue drains.
 */
public final class CountdownManager {
    private static final List<Countdown> ACTIVE = new CopyOnWriteArrayList<>();

    private CountdownManager() {}

    /** A scheduled broadcast tick. */
    private static final class Beat {
        final long fireAtTick;
        final long remainingSeconds;
        Beat(long fireAtTick, long remainingSeconds) {
            this.fireAtTick = fireAtTick;
            this.remainingSeconds = remainingSeconds;
        }
    }

    /** One running countdown. */
    private static final class Countdown {
        final String message;
        /** Raw color code as the user typed it: e.g. {@code &c}, {@code &#FF5500}, or {@code #FF5500}. */
        final String colorCode;
        final boolean alsoChat;
        final Deque<Beat> beats;

        Countdown(String message, String colorCode, boolean alsoChat, Deque<Beat> beats) {
            this.message = message;
            this.colorCode = colorCode;
            this.alsoChat = alsoChat;
            this.beats = beats;
        }
    }

    /** Build the descending-tick beat schedule for a countdown of {@code totalSeconds}. */
    private static Deque<Beat> buildSchedule(long startTick, long totalSeconds) {
        // Collect a deduped, descending set of remaining-second markers.
        TreeSet<Long> marks = new TreeSet<>((a, b) -> Long.compare(b, a));
        marks.add(totalSeconds); // start
        for (long m = 60L; m < totalSeconds; m += 60L) marks.add(m); // every minute on the way down
        if (totalSeconds > 10L) marks.add(10L);
        for (long s = 5L; s >= 0L; s--) marks.add(s);

        Deque<Beat> q = new ArrayDeque<>();
        for (long remaining : marks) {
            long elapsed = totalSeconds - remaining;
            long fire = startTick + elapsed * 20L;
            q.addLast(new Beat(fire, remaining));
        }
        return q;
    }

    /** Start a new countdown. {@code totalSeconds} must be {@code >= 1}. */
    public static void start(MinecraftServer server, long totalSeconds,
                             String message, String colorCode, boolean alsoChat) {
        if (server == null || totalSeconds < 1) return;
        Deque<Beat> beats = buildSchedule(server.getTickCount(), totalSeconds);
        ACTIVE.add(new Countdown(message, colorCode, alsoChat, beats));
    }

    /** Drop every active countdown. Used on server-stop. */
    public static void clear() { ACTIVE.clear(); }

    public static int activeCount() { return ACTIVE.size(); }

    /** Server-tick hook — must be called once per END-phase server tick. */
    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty() || server == null) return;
        long now = server.getTickCount();
        Iterator<Countdown> it = ACTIVE.iterator();
        // CopyOnWriteArrayList iterator doesn't support remove; collect finished items
        java.util.List<Countdown> finished = null;
        while (it.hasNext()) {
            Countdown cd = it.next();
            while (!cd.beats.isEmpty() && cd.beats.peekFirst().fireAtTick <= now) {
                fire(server, cd, cd.beats.pollFirst());
            }
            if (cd.beats.isEmpty()) {
                if (finished == null) finished = new java.util.ArrayList<>(2);
                finished.add(cd);
            }
        }
        if (finished != null) ACTIVE.removeAll(finished);
    }

    /** Format and dispatch a single broadcast beat. */
    private static void fire(MinecraftServer server, Countdown cd, Beat beat) {
        String remainingStr = humanRemaining(beat.remainingSeconds);
        String coloredRemaining = (cd.colorCode == null ? "" : cd.colorCode) + remainingStr;

        String titleFmt = ConfigHandler.config.countdownTitleFormat.get();
        if (titleFmt == null || titleFmt.isEmpty()) titleFmt = "$message";
        String subtitleFmt = ConfigHandler.config.countdownSubtitleFormat.get();
        if (subtitleFmt == null || subtitleFmt.isEmpty()) subtitleFmt = "$colored_time";

        String titleStr = titleFmt
                .replace("$message", cd.message == null ? "" : cd.message)
                .replace("$time", remainingStr)
                .replace("$colored_time", coloredRemaining)
                .replace("$color", cd.colorCode == null ? "" : cd.colorCode);
        String subtitleStr = subtitleFmt
                .replace("$message", cd.message == null ? "" : cd.message)
                .replace("$time", remainingStr)
                .replace("$colored_time", coloredRemaining)
                .replace("$color", cd.colorCode == null ? "" : cd.colorCode);

        Component title = TextFormatter.stringToFormattedText(titleStr);
        Component subtitle = TextFormatter.stringToFormattedText(subtitleStr);

        ClientboundSetTitleTextPacket titlePkt = new ClientboundSetTitleTextPacket(title);
        ClientboundSetSubtitleTextPacket subtitlePkt = new ClientboundSetSubtitleTextPacket(subtitle);

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.connection.send(subtitlePkt);
            p.connection.send(titlePkt);
        }

        if (cd.alsoChat) {
            String chatFmt = ConfigHandler.config.countdownChatFormat.get();
            if (chatFmt == null || chatFmt.isEmpty()) {
                chatFmt = "&c[&lCountdown&c] &r$message &7- $colored_time";
            }
            String chatStr = chatFmt
                    .replace("$message", cd.message == null ? "" : cd.message)
                    .replace("$time", remainingStr)
                    .replace("$colored_time", coloredRemaining)
                    .replace("$color", cd.colorCode == null ? "" : cd.colorCode);
            MutableComponent chatLine = TextFormatter.stringToFormattedText(chatStr);
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                p.sendSystemMessage(chatLine);
            }
        }
    }

    /** Compact "1d2h", "30m", "5s" form for the remaining-time display. */
    public static String humanRemaining(long seconds) {
        if (seconds <= 0L) return "0s";
        long d = seconds / 86400L;
        long h = (seconds % 86400L) / 3600L;
        long m = (seconds % 3600L) / 60L;
        long s = seconds % 60L;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append('d');
        if (h > 0) sb.append(h).append('h');
        if (m > 0) sb.append(m).append('m');
        // seconds shown only when smaller than a minute, otherwise we round to the nearest unit shown
        if (sb.length() == 0 || s > 0 && d == 0 && h == 0 && m == 0) {
            sb.append(s).append('s');
        }
        return sb.toString();
    }

    /**
     * Parses durations like {@code 90}, {@code 1m30s}, {@code 1h}, {@code 1d2h30m}.
     * Plain integers are seconds. Returns {@code -1} on invalid input.
     */
    public static long parseDurationSeconds(String input) {
        if (input == null || input.isBlank()) return -1L;
        String s = input.trim().toLowerCase();
        // bare integer = seconds
        try {
            long n = Long.parseLong(s);
            return n < 1 ? -1L : n;
        } catch (NumberFormatException ignored) {}

        long total = 0L;
        long current = 0L;
        boolean sawDigit = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                current = current * 10 + (c - '0');
                sawDigit = true;
            } else {
                if (!sawDigit) return -1L;
                long mult;
                switch (c) {
                    case 's' -> mult = 1L;
                    case 'm' -> mult = 60L;
                    case 'h' -> mult = 3600L;
                    case 'd' -> mult = 86400L;
                    default -> { return -1L; }
                }
                total += current * mult;
                current = 0L;
                sawDigit = false;
            }
        }
        if (sawDigit && current > 0) total += current; // trailing bare number = seconds
        return total < 1 ? -1L : total;
    }
}
