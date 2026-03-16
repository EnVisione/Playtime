package com.enviouse.playtime.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Colour utility supporting legacy §-codes, hex &#RRGGBB colours, per-character
 * gradient text, and auto-generated gradients via specification strings.
 * <p>
 * Supported formats:
 * <ul>
 *   <li>{@code §a} or {@code &a} — legacy Minecraft colour codes</li>
 *   <li>{@code &#RRGGBB} — single hex colour applied to entire text</li>
 *   <li>{@code &#RRGGBBX&#RRGBBBY...} — pre-baked per-character gradient (each char preceded by its hex)</li>
 *   <li>{@code gradient:#RRGGBB-#RRGGBB} — auto-generate 2-stop gradient for text</li>
 *   <li>{@code gradient:#RRGGBB-#RRGGBB-#RRGGBB} — multi-stop gradient (any number of stops)</li>
 *   <li>Formatting suffixes: append {@code §l}, {@code §n}, etc. after gradient spec for bold/underline/etc.</li>
 * </ul>
 * <p>
 * Hex format is compatible with Better-Forge-Chat-Reforged-Reworked: {@code &#RRGGBB}.
 */
public final class ColorUtil {

    private ColorUtil() {}

    // Matches &#RRGGBB (hex) or &/§ followed by a formatting char (0-9a-fk-or)
    private static final Pattern COLOR_PATTERN = Pattern.compile(
            "([&§]#[0-9A-Fa-f]{6})|([&§][0-9a-fk-orA-FK-OR])"
    );

    // Matches pre-baked per-character gradient: &#RRGGBB followed by exactly one non-& non-§ char
    // e.g. &#AD3080P&#9C80A9l&#8ACFD2a
    private static final Pattern PREBAKED_GRADIENT_PATTERN = Pattern.compile(
            "[&§]#([0-9A-Fa-f]{6})([^&§])"
    );

    // Matches "gradient:#RRGGBB-#RRGGBB[-#RRGGBB...]" optionally followed by formatting codes
    private static final Pattern GRADIENT_SPEC_PATTERN = Pattern.compile(
            "^gradient:(#[0-9A-Fa-f]{6}(?:-#[0-9A-Fa-f]{6})+)((?:[&§][lmnoLMNO])*)$",
            Pattern.CASE_INSENSITIVE
    );

    // ── Public API ──────────────────────────────────────────────────────────────

    /**
     * Parse a colour/style string and apply it to text, returning a styled Component.
     * Detects gradients automatically. See class javadoc for supported formats.
     *
     * @param colorStr the colour/style prefix string (may contain gradient spec or pre-baked gradient)
     * @param text     the text to style
     * @return a styled MutableComponent
     */
    public static MutableComponent styled(String colorStr, String text) {
        if (colorStr == null || colorStr.isEmpty()) {
            return Component.literal(text);
        }

        // 1. Check for gradient specification: "gradient:#RRGGBB-#RRGGBB[§l§n...]"
        Matcher gradientSpecMatcher = GRADIENT_SPEC_PATTERN.matcher(colorStr);
        if (gradientSpecMatcher.matches()) {
            String stopsStr = gradientSpecMatcher.group(1);      // "#RRGGBB-#RRGGBB-..."
            String fmtCodes = gradientSpecMatcher.group(2);      // "§l§n" etc.
            int[] stops = parseColorStops(stopsStr);
            Style baseStyle = parseFormattingCodes(fmtCodes);
            return buildGradient(stops, text, baseStyle);
        }

        // 2. Check for pre-baked per-character gradient: &#RRGGBBX&#RRGBBBY...
        if (isPrebaked(colorStr)) {
            return parsePrebaked(colorStr);
        }

        // 3. Standard single-colour / legacy code path
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
     * Supports all formats including gradients.
     */
    public static MutableComponent rankDisplay(String colorStr, String displayName) {
        return styled(colorStr, displayName);
    }

    /**
     * Build a BFCRR-compatible LuckPerms suffix string for a display rank.
     * Includes the rank's colour (gradient pre-baked per-character, single hex, or legacy code)
     * plus underline formatting. Ends with {@code &r} to reset for subsequent text.
     * <p>
     * Examples:
     * <ul>
     *   <li>Gradient: {@code " &n&#DDA0DDW&#93AFD9i&#4ABFD5z&#00CED1a&#80D369r&#FFD700d&r"}</li>
     *   <li>Simple hex: {@code " &n&#B8860BCogwright&r"}</li>
     *   <li>No colour: {@code " &nRankName&r"}</li>
     * </ul>
     *
     * @param colorStr    the rank's colour string (gradient spec, hex, legacy code, or null)
     * @param displayName the rank display name
     * @return a formatted suffix string ready for LuckPerms
     */
    public static String buildLPSuffix(String colorStr, String displayName) {
        if (colorStr == null || colorStr.isEmpty()) {
            return " &n" + displayName + "&r";
        }

        // 1. Gradient spec: "gradient:#RRGGBB-#RRGGBB[-...][ §l§n...]"
        Matcher gradientMatcher = GRADIENT_SPEC_PATTERN.matcher(colorStr);
        if (gradientMatcher.matches()) {
            String stopsStr = gradientMatcher.group(1);   // "#RRGGBB-#RRGGBB-..."
            String fmtCodes = gradientMatcher.group(2);   // "§l§n" etc.
            int[] stops = parseColorStops(stopsStr);
            String prebaked = generatePrebaked(stops, displayName);
            // Convert §-codes to &-codes for LP/BFCRR, always add &n (underline)
            String formatting = fmtCodes.replace("§", "&");
            if (!formatting.contains("&n") && !formatting.contains("&N")) {
                formatting = "&n" + formatting;
            }
            return " " + formatting + prebaked + "&r";
        }

        // 2. Pre-baked gradient: already has per-char &#RRGGBBX sequences
        if (isPrebaked(colorStr)) {
            return " &n" + colorStr + "&r";
        }

        // 3. Simple hex (&#RRGGBB) or legacy code (§a, &a, etc.)
        String formattedColor = colorStr.replace("§", "&");
        return " &n" + formattedColor + displayName + "&r";
    }

    /**
     * Convert a colour string to a preview string for chat.
     * For gradient specs, shows the gradient applied to the spec string itself.
     * For pre-baked gradients, renders the embedded text.
     */
    public static MutableComponent colorPreview(String colorStr) {
        if (colorStr == null || colorStr.isEmpty()) {
            return Component.literal("(none)");
        }

        // Pre-baked gradient → render the embedded text
        if (isPrebaked(colorStr)) {
            return parsePrebaked(colorStr);
        }

        // Gradient spec → apply to spec text for preview
        Matcher gradientSpecMatcher = GRADIENT_SPEC_PATTERN.matcher(colorStr);
        if (gradientSpecMatcher.matches()) {
            return styled(colorStr, colorStr);
        }

        // Standard
        return styled(colorStr, colorStr);
    }

    /**
     * Check whether a string looks like a valid colour specification.
     * Accepts: §-codes, &-codes, &#RRGGBB, §#RRGGBB, gradient specs,
     * pre-baked gradients, or combinations.
     */
    public static boolean isValidColor(String input) {
        if (input == null || input.isEmpty()) return false;
        if (GRADIENT_SPEC_PATTERN.matcher(input).matches()) return true;
        if (isPrebaked(input)) return true;
        return COLOR_PATTERN.matcher(input).find();
    }

    // ── Gradient Generation ─────────────────────────────────────────────────────

    /**
     * Generate a gradient MutableComponent from color stops applied to text.
     * Each character gets a linearly interpolated colour.
     *
     * @param stops     array of RGB ints (at least 2)
     * @param text      the text to apply the gradient to
     * @param baseStyle base style (formatting like bold/italic) to apply to every char
     * @return a compound MutableComponent with per-character colours
     */
    public static MutableComponent buildGradient(int[] stops, String text, Style baseStyle) {
        if (text == null || text.isEmpty()) {
            return Component.literal("");
        }
        if (stops == null || stops.length == 0) {
            return Component.literal(text);
        }
        if (stops.length == 1 || text.length() == 1) {
            return Component.literal(text).withStyle(baseStyle.withColor(TextColor.fromRgb(stops[0])));
        }

        MutableComponent result = Component.empty();
        int len = text.length();

        for (int i = 0; i < len; i++) {
            float ratio = (float) i / (float) (len - 1);   // 0.0 to 1.0
            int rgb = interpolateMultiStop(stops, ratio);
            Style charStyle = baseStyle.withColor(TextColor.fromRgb(rgb));
            result.append(Component.literal(String.valueOf(text.charAt(i))).withStyle(charStyle));
        }

        return result;
    }

    /**
     * Generate a gradient specification string from start and end hex colors.
     *
     * @param startHex start colour as "#RRGGBB"
     * @param endHex   end colour as "#RRGGBB"
     * @return a gradient spec string like "gradient:#AD3080-#C3D1BB"
     */
    public static String makeGradientSpec(String startHex, String endHex) {
        return "gradient:" + normalizeHex(startHex) + "-" + normalizeHex(endHex);
    }

    /**
     * Generate a multi-stop gradient specification string.
     *
     * @param hexColors list of hex colours (at least 2)
     * @return a gradient spec string like "gradient:#AD3080-#9C80A9-#C3D1BB"
     */
    public static String makeGradientSpec(List<String> hexColors) {
        if (hexColors == null || hexColors.size() < 2) {
            throw new IllegalArgumentException("Need at least 2 colors for a gradient");
        }
        StringBuilder sb = new StringBuilder("gradient:");
        for (int i = 0; i < hexColors.size(); i++) {
            if (i > 0) sb.append("-");
            sb.append(normalizeHex(hexColors.get(i)));
        }
        return sb.toString();
    }

    /**
     * Generate a pre-baked per-character gradient string.
     * Output format: {@code &#AD3080P&#9C80A9l&#8ACFD2a...}
     *
     * @param stops array of RGB colour stops (at least 2)
     * @param text  the text to embed
     * @return a pre-baked gradient string
     */
    public static String generatePrebaked(int[] stops, String text) {
        if (text == null || text.isEmpty()) return "";
        if (stops == null || stops.length == 0) return text;

        StringBuilder sb = new StringBuilder();
        int len = text.length();
        for (int i = 0; i < len; i++) {
            float ratio = len == 1 ? 0f : (float) i / (float) (len - 1);
            int rgb = interpolateMultiStop(stops, ratio);
            sb.append(String.format("&#%06X%c", rgb, text.charAt(i)));
        }
        return sb.toString();
    }

    /**
     * Generate a pre-baked gradient from hex color strings.
     *
     * @param hexColors list of "#RRGGBB" strings (at least 2)
     * @param text      the text to embed
     * @return a pre-baked gradient string
     */
    public static String generatePrebaked(List<String> hexColors, String text) {
        int[] stops = new int[hexColors.size()];
        for (int i = 0; i < hexColors.size(); i++) {
            stops[i] = parseHex(hexColors.get(i));
        }
        return generatePrebaked(stops, text);
    }

    // ── Pre-baked Gradient Parsing ──────────────────────────────────────────────

    /**
     * Check if a string is a pre-baked per-character gradient.
     * Detected by the pattern: alternating &#RRGGBB + single char, with no leftover text.
     */
    public static boolean isPrebaked(String input) {
        if (input == null || input.length() < 9) return false; // minimum: &#RRGGBBX = 9 chars
        Matcher matcher = PREBAKED_GRADIENT_PATTERN.matcher(input);
        int lastEnd = 0;
        int matchCount = 0;
        while (matcher.find()) {
            if (matcher.start() != lastEnd) return false; // gap between matches
            lastEnd = matcher.end();
            matchCount++;
        }
        return matchCount >= 2 && lastEnd == input.length(); // at least 2 chars, covers entire string
    }

    /**
     * Parse a pre-baked per-character gradient string into a styled compound component.
     *
     * @param input the pre-baked gradient string
     * @return a compound MutableComponent with per-character colours
     */
    public static MutableComponent parsePrebaked(String input) {
        MutableComponent result = Component.empty();
        Matcher matcher = PREBAKED_GRADIENT_PATTERN.matcher(input);

        while (matcher.find()) {
            String hex = matcher.group(1);
            String ch = matcher.group(2);
            try {
                int rgb = Integer.parseInt(hex, 16);
                result.append(Component.literal(ch).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb))));
            } catch (NumberFormatException e) {
                result.append(Component.literal(ch));
            }
        }

        return result;
    }

    // ── Internal helpers ───────────────────────────────────────────────────────

    /**
     * Interpolate between multiple color stops.
     *
     * @param stops array of RGB int colors (at least 2)
     * @param ratio position in the gradient, 0.0 to 1.0
     * @return interpolated RGB int
     */
    static int interpolateMultiStop(int[] stops, float ratio) {
        if (ratio <= 0f) return stops[0];
        if (ratio >= 1f) return stops[stops.length - 1];

        // Scale ratio to the number of segments
        int segments = stops.length - 1;
        float scaled = ratio * segments;
        int segment = Math.min((int) scaled, segments - 1);
        float localRatio = scaled - segment;

        return interpolateRgb(stops[segment], stops[segment + 1], localRatio);
    }

    /**
     * Linear interpolation between two RGB colours.
     */
    static int interpolateRgb(int color1, int color2, float ratio) {
        int r1 = (color1 >> 16) & 0xFF, g1 = (color1 >> 8) & 0xFF, b1 = color1 & 0xFF;
        int r2 = (color2 >> 16) & 0xFF, g2 = (color2 >> 8) & 0xFF, b2 = color2 & 0xFF;

        int r = Math.round(r1 + (r2 - r1) * ratio);
        int g = Math.round(g1 + (g2 - g1) * ratio);
        int b = Math.round(b1 + (b2 - b1) * ratio);

        return (r << 16) | (g << 8) | b;
    }

    /**
     * Parse colour stops from a string like "#RRGGBB-#RRGGBB-#RRGGBB".
     */
    private static int[] parseColorStops(String stopsStr) {
        String[] parts = stopsStr.split("-");
        int[] stops = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            stops[i] = parseHex(parts[i]);
        }
        return stops;
    }

    /**
     * Parse a hex colour string. Accepts "#RRGGBB", "RRGGBB", "&#RRGGBB", "§#RRGGBB".
     */
    static int parseHex(String hex) {
        String clean = hex.replaceAll("^[&§]?#?", "");
        if (clean.length() != 6) {
            throw new IllegalArgumentException("Invalid hex colour: " + hex);
        }
        return Integer.parseInt(clean, 16);
    }

    /**
     * Normalize a hex string to "#RRGGBB" format.
     */
    private static String normalizeHex(String hex) {
        String clean = hex.replaceAll("^[&§]?#?", "");
        if (clean.length() != 6) {
            throw new IllegalArgumentException("Invalid hex colour: " + hex);
        }
        return "#" + clean.toUpperCase();
    }

    /**
     * Parse formatting codes like "§l§n" into a Style with bold+underline etc.
     */
    private static Style parseFormattingCodes(String codes) {
        if (codes == null || codes.isEmpty()) return Style.EMPTY;
        Style style = Style.EMPTY;
        Matcher matcher = Pattern.compile("[&§]([lmnoLMNO])").matcher(codes);
        while (matcher.find()) {
            char code = Character.toLowerCase(matcher.group(1).charAt(0));
            ChatFormatting fmt = fromCode(code);
            if (fmt != null) {
                style = applyFormatting(style, fmt);
            }
        }
        return style;
    }

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
