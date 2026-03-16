package com.enviouse.playtime.integration;

import com.enviouse.playtime.Config;
import com.enviouse.playtime.Playtime;
import com.enviouse.playtime.config.RankConfig;
import com.enviouse.playtime.data.PlayerRecord;
import com.enviouse.playtime.data.RankDefinition;
import com.enviouse.playtime.service.RankEngine;
import com.enviouse.playtime.util.ColorUtil;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;

/**
 * Integrated chat formatter that replaces the vanilla {@code <player> message}
 * format with a rank-aware format when LuckPerms integration is disabled.
 * <p>
 * When LuckPerms is enabled, this handler does nothing — LP and BFCRR handle
 * chat formatting instead.
 * <p>
 * Fully configurable via {@code [integrated-ranks]} in {@code playtime.toml}:
 * <ul>
 *   <li>{@code chatMessageFormat} — overall structure, e.g. {@code <{rank-display}> {msg}}</li>
 *   <li>{@code chatMessageFormatNoRank} — format for players below displayRankMinimum</li>
 *   <li>{@code rankDisplayFormat} — rank prefix, e.g. {@code {rank} {username}}</li>
 *   <li>{@code hexFormattingEnabled} — whether hex/gradient colours are parsed</li>
 *   <li>{@code displayRankMinimum} — minimum rank ID to show rank prefix in chat</li>
 *   <li>{@code style.boldMinimumRank} — minimum rank for bold styling</li>
 *   <li>{@code style.underlineMinimumRank} — minimum rank for underline styling</li>
 *   <li>{@code style.italicMinimumRank} — minimum rank for italic styling</li>
 *   <li>{@code style.strikethroughMinimumRank} — minimum rank for strikethrough styling</li>
 *   <li>{@code style.obfuscatedMinimumRank} — minimum rank for obfuscated styling</li>
 *   <li>{@code style.applyToUsername} — whether styles also apply to the username</li>
 * </ul>
 */
public class IntegratedChatHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Intercept chat messages and apply integrated rank formatting.
     * Uses HIGH priority so it runs before other chat mods, but can still be cancelled.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onServerChat(ServerChatEvent event) {
        // Only active when LuckPerms integration is disabled
        if (Config.luckpermsEnabled) return;

        ServerPlayer player = event.getPlayer();
        if (player == null) return;

        // Look up the player's current rank
        RankDefinition rank = getPlayerRank(player);
        if (rank == null) return;

        String username = player.getGameProfile().getName();
        String message = event.getRawText();

        // Check if the player's rank meets the display minimum
        if (!meetsDisplayMinimum(rank)) {
            // Player is below the display rank minimum — use the no-rank format
            MutableComponent fullMessage = buildNoRankMessage(username, message);
            event.setMessage(fullMessage);
            return;
        }

        // Compute which styles apply based on configurable rank thresholds
        Style thresholdStyle = computeThresholdStyle(rank);

        // Build the rank display component (coloured rank name + threshold styles)
        MutableComponent rankComponent = buildRankComponent(rank, thresholdStyle);

        // Build the rank-display string: e.g. "{rank} {username}"
        MutableComponent rankDisplay = buildRankDisplay(rankComponent, username, thresholdStyle);

        // Build the full chat message using the format template
        MutableComponent fullMessage = buildChatMessage(rankDisplay, message);

        // Replace the chat message
        event.setMessage(fullMessage);
    }

    // ── Rank Lookup ────────────────────────────────────────────────────────────

    /**
     * Get the current rank for a player, or the first rank as fallback.
     */
    private RankDefinition getPlayerRank(ServerPlayer player) {
        var repository = Playtime.getRepository();
        var rankConfig = Playtime.getRankConfig();
        if (repository == null || rankConfig == null) return null;

        PlayerRecord record = repository.getPlayer(player.getUUID());
        if (record == null) return rankConfig.getFirstRank();

        String rankId = record.getCurrentRankId();
        if (rankId != null) {
            RankDefinition rank = rankConfig.getRankById(rankId);
            if (rank != null) return rank;
        }

        // Fallback: calculate from playtime
        RankEngine engine = Playtime.getRankEngine();
        if (engine != null) {
            return engine.getCurrentRank(record.getTotalPlaytimeTicks());
        }
        return rankConfig.getFirstRank();
    }

    // ── Display Minimum ────────────────────────────────────────────────────────

    /**
     * Check whether a player's rank meets or exceeds the configured display minimum.
     * If displayRankMinimum is empty, all ranks qualify.
     */
    private boolean meetsDisplayMinimum(RankDefinition playerRank) {
        String minRankId = Config.displayRankMinimum;
        if (minRankId == null || minRankId.isEmpty()) return true;
        return isRankAtOrAbove(playerRank, minRankId);
    }

    /**
     * Check if {@code playerRank} has a sortOrder >= the sortOrder of the rank
     * identified by {@code thresholdRankId}. Returns false if the threshold rank
     * doesn't exist (effectively disabling the feature).
     */
    private boolean isRankAtOrAbove(RankDefinition playerRank, String thresholdRankId) {
        if (thresholdRankId == null || thresholdRankId.isEmpty()) return false;
        RankConfig rankConfig = Playtime.getRankConfig();
        if (rankConfig == null) return false;

        RankDefinition thresholdRank = rankConfig.getRankById(thresholdRankId);
        if (thresholdRank == null) {
            LOGGER.warn("[Playtime] Integrated-ranks config references unknown rank ID '{}' — ignoring threshold.", thresholdRankId);
            return false;
        }
        return playerRank.getSortOrder() >= thresholdRank.getSortOrder();
    }

    // ── Style Thresholds ───────────────────────────────────────────────────────

    /**
     * Compute the combined {@link Style} for a player's rank by checking each
     * configurable style threshold (bold, underline, italic, strikethrough,
     * obfuscated). Only styles whose minimum rank is met are applied.
     */
    private Style computeThresholdStyle(RankDefinition playerRank) {
        Style style = Style.EMPTY;

        if (isRankAtOrAbove(playerRank, Config.styleBoldMinimumRank)) {
            style = style.withBold(true);
        }
        if (isRankAtOrAbove(playerRank, Config.styleUnderlineMinimumRank)) {
            style = style.withUnderlined(true);
        }
        if (isRankAtOrAbove(playerRank, Config.styleItalicMinimumRank)) {
            style = style.withItalic(true);
        }
        if (isRankAtOrAbove(playerRank, Config.styleStrikethroughMinimumRank)) {
            style = style.withStrikethrough(true);
        }
        if (isRankAtOrAbove(playerRank, Config.styleObfuscatedMinimumRank)) {
            style = style.withObfuscated(true);
        }

        return style;
    }

    // ── Component Builders ─────────────────────────────────────────────────────

    /**
     * Build a styled rank name component using the rank's colour/gradient,
     * with additional threshold-based formatting (bold/underline/etc.) overlaid.
     */
    private MutableComponent buildRankComponent(RankDefinition rank, Style thresholdStyle) {
        String colorStr = rank.getFallbackColor();
        MutableComponent component;
        if (Config.hexFormattingEnabled && colorStr != null && !colorStr.isEmpty()) {
            component = ColorUtil.rankDisplay(colorStr, rank.getDisplayName());
        } else {
            component = Component.literal(rank.getDisplayName());
        }

        // Apply threshold-based styles on top of the existing colour styling
        if (!thresholdStyle.equals(Style.EMPTY)) {
            component = applyStyleRecursive(component, thresholdStyle);
        }
        return component;
    }

    /**
     * Build the rank-display portion of the chat message.
     * Template: {@code {rank} {username}} by default.
     */
    private MutableComponent buildRankDisplay(MutableComponent rankComponent, String username, Style thresholdStyle) {
        String format = Config.rankDisplayFormat;
        if (format == null || format.isEmpty()) {
            format = "{rank} {username}";
        }

        MutableComponent result = Component.empty();

        String remaining = format;
        while (!remaining.isEmpty()) {
            int rankIdx = remaining.indexOf("{rank}");
            int userIdx = remaining.indexOf("{username}");

            int earliest = -1;
            String placeholder = null;
            if (rankIdx >= 0 && (userIdx < 0 || rankIdx < userIdx)) {
                earliest = rankIdx;
                placeholder = "{rank}";
            } else if (userIdx >= 0) {
                earliest = userIdx;
                placeholder = "{username}";
            }

            if (earliest < 0) {
                result.append(Component.literal(remaining));
                break;
            }

            if (earliest > 0) {
                result.append(Component.literal(remaining.substring(0, earliest)));
            }

            if ("{rank}".equals(placeholder)) {
                result.append(rankComponent.copy());
            } else {
                // Username — optionally apply threshold styles
                MutableComponent usernameComp = Component.literal(username);
                if (Config.styleApplyToUsername && !thresholdStyle.equals(Style.EMPTY)) {
                    usernameComp = usernameComp.withStyle(thresholdStyle);
                }
                result.append(usernameComp);
            }

            remaining = remaining.substring(earliest + placeholder.length());
        }

        return result;
    }

    /**
     * Build the full chat message component using the standard format.
     * Template: {@code <{rank-display}> {msg}} by default.
     */
    private MutableComponent buildChatMessage(MutableComponent rankDisplay, String message) {
        String format = Config.chatMessageFormat;
        if (format == null || format.isEmpty()) {
            format = "<{rank-display}> {msg}";
        }
        return buildFromTemplate(format, rankDisplay, message);
    }

    /**
     * Build the chat message for players below the display rank minimum.
     * Template: {@code <{username}> {msg}} by default.
     */
    private MutableComponent buildNoRankMessage(String username, String message) {
        String format = Config.chatMessageFormatNoRank;
        if (format == null || format.isEmpty()) {
            format = "<{username}> {msg}";
        }

        MutableComponent result = Component.empty();
        String remaining = format;
        while (!remaining.isEmpty()) {
            int userIdx = remaining.indexOf("{username}");
            int msgIdx = remaining.indexOf("{msg}");

            int earliest = -1;
            String placeholder = null;
            if (userIdx >= 0 && (msgIdx < 0 || userIdx < msgIdx)) {
                earliest = userIdx;
                placeholder = "{username}";
            } else if (msgIdx >= 0) {
                earliest = msgIdx;
                placeholder = "{msg}";
            }

            if (earliest < 0) {
                result.append(Component.literal(remaining));
                break;
            }

            if (earliest > 0) {
                result.append(Component.literal(remaining.substring(0, earliest)));
            }

            if ("{username}".equals(placeholder)) {
                result.append(Component.literal(username));
            } else {
                result.append(Component.literal(message));
            }

            remaining = remaining.substring(earliest + placeholder.length());
        }
        return result;
    }

    // ── Template Utilities ─────────────────────────────────────────────────────

    /**
     * Build a component from a format string containing {rank-display} and {msg}.
     */
    private MutableComponent buildFromTemplate(String format, MutableComponent rankDisplay, String message) {
        MutableComponent result = Component.empty();

        String remaining = format;
        while (!remaining.isEmpty()) {
            int rdIdx = remaining.indexOf("{rank-display}");
            int msgIdx = remaining.indexOf("{msg}");

            int earliest = -1;
            String placeholder = null;
            if (rdIdx >= 0 && (msgIdx < 0 || rdIdx < msgIdx)) {
                earliest = rdIdx;
                placeholder = "{rank-display}";
            } else if (msgIdx >= 0) {
                earliest = msgIdx;
                placeholder = "{msg}";
            }

            if (earliest < 0) {
                result.append(Component.literal(remaining));
                break;
            }

            if (earliest > 0) {
                result.append(Component.literal(remaining.substring(0, earliest)));
            }

            if ("{rank-display}".equals(placeholder)) {
                result.append(rankDisplay.copy());
            } else {
                result.append(Component.literal(message));
            }

            remaining = remaining.substring(earliest + placeholder.length());
        }

        return result;
    }

    /**
     * Apply a {@link Style} to a component and all its siblings/children recursively.
     * This merges the new style on top of each existing char style, so colours
     * from gradients are preserved while bold/underline/etc. are added.
     */
    private MutableComponent applyStyleRecursive(MutableComponent component, Style overlay) {
        // Apply to root — merge overlay into existing style
        Style merged = component.getStyle().applyTo(overlay);
        component.setStyle(merged);

        // Apply to siblings
        for (var sibling : component.getSiblings()) {
            if (sibling instanceof MutableComponent mc) {
                applyStyleRecursive(mc, overlay);
            }
        }
        return component;
    }
}
