package com.enviouse.sef.banned;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One entry in the banned list.
 *
 * <p>The {@code pattern} field accepts three forms:
 * <ul>
 *   <li>Exact registry id: {@code minecraft:diamond}</li>
 *   <li>Mod-wide wildcard: {@code modid:*}</li>
 *   <li>Tag reference: {@code #namespace:path} (matched against item OR block tags)</li>
 * </ul>
 *
 * <p>Stored as a plain POJO so Gson can round-trip it without a custom adapter.
 */
public class BannedEntry {
    /** The ban pattern (registry id, mod wildcard, or tag). Case-sensitive registry-style. */
    public String pattern;
    /** Reason shown in the hover tooltip and notifications. May be empty. */
    public String reason = "";
    /** Name of the player/console that issued the ban. */
    public String bannedBy = "Console";
    /** Epoch-millis the ban was created. Purely informational. */
    public long bannedAtMillis = 0L;
    /** Duration in milliseconds. {@code -1} = infinite. {@code 0} = also infinite (legacy). */
    public long durationMs = -1L;
    /** Whether to broadcast a server-wide message when an item is confiscated. */
    public boolean announce = false;

    public BannedEntry() {}

    public BannedEntry(String pattern, String reason, String bannedBy,
                       long durationMs, boolean announce) {
        this.pattern = pattern;
        this.reason = reason == null ? "" : reason;
        this.bannedBy = bannedBy == null ? "Console" : bannedBy;
        this.durationMs = durationMs;
        this.announce = announce;
        this.bannedAtMillis = System.currentTimeMillis();
    }

    public boolean isPermanent() { return durationMs < 0L; }

    public boolean isExpired() {
        if (isPermanent()) return false;
        return System.currentTimeMillis() >= (bannedAtMillis + durationMs);
    }

    public long getRemainingMillis() {
        if (isPermanent()) return -1L;
        return Math.max(0L, (bannedAtMillis + durationMs) - System.currentTimeMillis());
    }

    public String getRemainingString() {
        if (isPermanent()) return "Permanent";
        return formatDurationMs(getRemainingMillis());
    }

    public String getDurationString() {
        if (isPermanent()) return "Permanent";
        return formatDurationMs(durationMs);
    }

    public static String formatDurationMs(long ms) {
        if (ms <= 0) return "0s";
        long totalSeconds = ms / 1000L;
        long d = totalSeconds / 86400;
        long h = (totalSeconds % 86400) / 3600;
        long m = (totalSeconds % 3600) / 60;
        long s = totalSeconds % 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("d ");
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (s > 0 || sb.length() == 0) sb.append(s).append("s");
        return sb.toString().trim();
    }

    /** True if the pattern matches the given item stack (supports id, modid:*, #tag). */
    public boolean matchesItem(ResourceLocation rl, ItemStack stack) {
        if (pattern == null || rl == null) return false;
        String p = pattern.trim();
        if (p.isEmpty()) return false;
        if (p.startsWith("#")) {
            ResourceLocation tagRl = ResourceLocation.tryParse(p.substring(1));
            if (tagRl == null || stack == null) return false;
            return stack.is(TagKey.create(Registries.ITEM, tagRl));
        }
        if (isModWildcard(p)) {
            return rl.getNamespace().equalsIgnoreCase(p.substring(0, p.indexOf(':')));
        }
        return p.equalsIgnoreCase(rl.toString());
    }

    /** True if the pattern matches the given block state (supports id, modid:*, #tag). */
    public boolean matchesBlock(ResourceLocation rl, BlockState state) {
        if (pattern == null || rl == null) return false;
        String p = pattern.trim();
        if (p.isEmpty()) return false;
        if (p.startsWith("#")) {
            ResourceLocation tagRl = ResourceLocation.tryParse(p.substring(1));
            if (tagRl == null || state == null) return false;
            return state.is(TagKey.create(Registries.BLOCK, tagRl));
        }
        if (isModWildcard(p)) {
            return rl.getNamespace().equalsIgnoreCase(p.substring(0, p.indexOf(':')));
        }
        return p.equalsIgnoreCase(rl.toString());
    }

    private static boolean isModWildcard(String p) {
        int colon = p.indexOf(':');
        return colon > 0 && p.endsWith("*") && p.length() == colon + 2;
    }
}
