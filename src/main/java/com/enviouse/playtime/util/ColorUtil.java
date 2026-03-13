package com.enviouse.playtime.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Colour utility supporting both legacy §-codes and hex &#RRGGBB colours.
 * <p>
 * Hex format matches the Better-Forge-Chat-Reforged-Reworked convention: {@code &#RRGGBB}.
 * Legacy codes like {@code §a}, {@code &a}, {@code §l}, {@code &l} are also supported.
 */
public final class ColorUtil {

    private ColorUtil() {}

    // Matches &#RRGGBB (hex) or &/§ followed by a formatting char (0-9a-fk-or)
    private static final Pattern COLOR_PATTERN = Pattern.compile(
            "([&§]#[0-9A-Fa-f]{6})|([&§][0-9a-fk-orA-FK-OR])"
    );

    /**
     * Parse a colour/style string and apply it to text, returning a styled Component.
     * Supports:
     * <ul>
     *   <li>{@code &#FF5500} — hex colour</li>
     *   <li>{@code §a} or {@code &a} — legacy Minecraft colour codes</li>
     *   <li>Combinations like {@code &#FF5500§l} — hex colour + bold</li>
     *   <li>{@code §6§l§n} — gold, bold, underline</li>
     * </ul>
     *
     * @param colorStr the colour/style prefix string
     * @param text     the text to style
     * @return a styled MutableComponent
     */
    public static MutableComponent styled(String colorStr, String text) {
        if (colorStr == null || colorStr.isEmpty()) {
            return Component.literal(text);
        }

        Style style = Style.EMPTY;
        Matcher matcher = COLOR_PATTERN.matcher(colorStr);

        while (matcher.find()) {
            if (matcher.group(1) != null) {
                // Hex colour: &#RRGGBB or §#RRGGBB
                String hex = matcher.group(1).substring(2); // strip &# or §#
                try {
                    int rgb = Integer.parseInt(hex, 16);
                    style = style.withColor(TextColor.fromRgb(rgb));
                } catch (NumberFormatException ignored) {}
            } else if (matcher.group(2) != null) {
                // Legacy code: &a, §l, etc.
                char code = Character.toLowerCase(matcher.group(2).charAt(1));
                ChatFormatting fmt = fromCode(code);
                if (fmt != null) {
                    if (fmt.isColor()) {
                        style = style.withColor(fmt);
                    } else {
                        style = applyFormatting(style, fmt);
                    }
                }
            }
        }

        return Component.literal(text).withStyle(style);
    }

    /**
     * Build a display component for a rank: colour prefix + display name.
     * Equivalent to {@code styled(colorStr, displayName)}.
     */
    public static MutableComponent rankDisplay(String colorStr, String displayName) {
        return styled(colorStr, displayName);
    }

    /**
     * Convert a colour string to a preview string for chat.
     * e.g. "&#FF5500" → shows as the hex in that colour.
     */
    public static MutableComponent colorPreview(String colorStr) {
        return styled(colorStr, colorStr);
    }

    /**
     * Check whether a string looks like a valid colour specification.
     * Accepts: §-codes, &-codes, &#RRGGBB, §#RRGGBB, or combinations.
     */
    public static boolean isValidColor(String input) {
        if (input == null || input.isEmpty()) return false;
        // Must contain at least one colour/style code
        return COLOR_PATTERN.matcher(input).find();
    }

    // ── Internal helpers ───────────────────────────────────────────────────────

    private static ChatFormatting fromCode(char code) {
        for (ChatFormatting fmt : ChatFormatting.values()) {
            if (fmt.getChar() == code) return fmt;
        }
        return null;
    }

    private static Style applyFormatting(Style style, ChatFormatting fmt) {
        return switch (fmt) {
            case BOLD -> style.withBold(true);
            case ITALIC -> style.withItalic(true);
            case UNDERLINE -> style.withUnderlined(true);
            case STRIKETHROUGH -> style.withStrikethrough(true);
            case OBFUSCATED -> style.withObfuscated(true);
            case RESET -> Style.EMPTY;
            default -> style;
        };
    }
}

