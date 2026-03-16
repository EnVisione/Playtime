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
 * Format is configurable via {@code [integrated-ranks]} in {@code playtime.toml}:
 * <ul>
 *   <li>{@code chatMessageFormat} — overall structure, e.g. {@code <{rank-display}> {msg}}</li>
 *   <li>{@code rankDisplayFormat} — rank prefix, e.g. {@code {rank} {username}}</li>
 *   <li>{@code hexFormattingEnabled} — whether hex/gradient colours are parsed</li>
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

        // Build the rank display component (coloured rank name)
        MutableComponent rankComponent = buildRankComponent(rank);

        // Build the rank-display string: e.g. "{rank} {username}"
        // We build this as a component so colours are preserved
        MutableComponent rankDisplay = buildRankDisplay(rankComponent, username);

        // Build the full chat message using the format template
        MutableComponent fullMessage = buildChatMessage(rankDisplay, message);

        // Replace the chat message
        event.setMessage(fullMessage);
    }

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

    /**
     * Build a styled rank name component using the rank's colour/gradient.
     */
    private MutableComponent buildRankComponent(RankDefinition rank) {
        String colorStr = rank.getFallbackColor();
        if (Config.hexFormattingEnabled && colorStr != null && !colorStr.isEmpty()) {
            return ColorUtil.rankDisplay(colorStr, rank.getDisplayName());
        }
        // Plain rank name if hex formatting is disabled
        return Component.literal(rank.getDisplayName());
    }

    /**
     * Build the rank-display portion of the chat message.
     * Template: {@code {rank} {username}} by default.
     */
    private MutableComponent buildRankDisplay(MutableComponent rankComponent, String username) {
        String format = Config.rankDisplayFormat;
        if (format == null || format.isEmpty()) {
            format = "{rank} {username}";
        }

        // Split the format around {rank} and {username} placeholders
        // Build component piece by piece to preserve rank colouring
        MutableComponent result = Component.empty();

        String remaining = format;
        while (!remaining.isEmpty()) {
            int rankIdx = remaining.indexOf("{rank}");
            int userIdx = remaining.indexOf("{username}");

            // Find the earliest placeholder
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
                // No more placeholders — append the rest as literal
                result.append(Component.literal(remaining));
                break;
            }

            // Append text before the placeholder
            if (earliest > 0) {
                result.append(Component.literal(remaining.substring(0, earliest)));
            }

            // Append the placeholder value
            if ("{rank}".equals(placeholder)) {
                result.append(rankComponent.copy());
            } else {
                result.append(Component.literal(username));
            }

            remaining = remaining.substring(earliest + placeholder.length());
        }

        return result;
    }

    /**
     * Build the full chat message component.
     * Template: {@code <{rank-display}> {msg}} by default.
     */
    private MutableComponent buildChatMessage(MutableComponent rankDisplay, String message) {
        String format = Config.chatMessageFormat;
        if (format == null || format.isEmpty()) {
            format = "<{rank-display}> {msg}";
        }

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
}

