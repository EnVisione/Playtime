package com.enviouse.playtime.command;

import com.enviouse.playtime.Config;
import com.enviouse.playtime.Playtime;
import com.enviouse.playtime.config.RankConfig;
import com.enviouse.playtime.data.RankDefinition;
import com.enviouse.playtime.integration.LuckPermsService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * /ranks [page] — list all public ranks with hours, descriptions, claims, forceloads, inactivity.
 * Paginated — configurable page size (default 16).
 * If a rank has a description, it replaces the claims/forceloads/inactivity line.
 * If claims or forceloads features are disabled, those columns are hidden.
 * If a rank has hover text, it appears on mouse-over.
 */
public class RanksCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("ranks")
                        .executes(ctx -> execute(ctx, 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> execute(ctx, IntegerArgumentType.getInteger(ctx, "page")))
                        )
        );
    }

    private static int execute(CommandContext<CommandSourceStack> ctx, int page) {
        CommandSourceStack src = ctx.getSource();
        RankConfig rankConfig = Playtime.getRankConfig();
        LuckPermsService lp = Playtime.getLuckPerms();

        // Collect only visible ranks
        List<RankDefinition> visibleRanks = new ArrayList<>();
        for (RankDefinition rank : rankConfig.getRanks()) {
            if (rank.isVisible()) visibleRanks.add(rank);
        }

        int pageSize = Config.ranksPageSize;
        int totalPages = Math.max(1, (visibleRanks.size() + pageSize - 1) / pageSize);
        if (page > totalPages) page = totalPages;

        int startIndex = (page - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, visibleRanks.size());

        src.sendSystemMessage(Component.literal("§6━━━━━━━━━━━ Ranks (Page " + page + "/" + totalPages + ") ━━━━━━━━━━━"));

        // Build subtitle based on enabled features
        StringBuilder subtitle = new StringBuilder("§7(Playtime");
        if (Config.claimsEnabled) subtitle.append(" — Claims");
        if (Config.forceloadsEnabled) subtitle.append(", Forceloads");
        subtitle.append(")");
        src.sendSystemMessage(Component.literal(subtitle.toString()));

        for (int i = startIndex; i < endIndex; i++) {
            RankDefinition rank = visibleRanks.get(i);

            MutableComponent line = Component.literal("§7- ")
                    .append(lp.getStyledRankName(rank));

            // If the rank has a custom description, show that instead of claims/forceloads
            if (rank.getDescription() != null && !rank.getDescription().isEmpty()) {
                line.append(Component.literal("§r §7- §f" + rank.getThresholdHours() + "h §7- §f" + rank.getDescription()));
            } else {
                // Build detail string based on enabled features
                StringBuilder detail = new StringBuilder();
                detail.append("§r §7- §f").append(rank.getThresholdHours()).append("h §7- ");

                if (Config.claimsEnabled) {
                    detail.append("§f").append(rank.getClaims()).append("§7 claims");
                }
                if (Config.forceloadsEnabled) {
                    if (Config.claimsEnabled) detail.append(", ");
                    detail.append("§f").append(rank.getForceloads()).append("§7 forceloads");
                }

                String inactivityText = rank.getInactivityDays() == -1 ? "Never" : rank.getInactivityDays() + "d";
                if (Config.claimsEnabled || Config.forceloadsEnabled) detail.append(", ");
                detail.append("§f").append(inactivityText).append("§7 inactivity");

                line.append(Component.literal(detail.toString()));
            }

            // Add hover text if present
            if (rank.getHoverText() != null && !rank.getHoverText().isEmpty()) {
                line.withStyle(style -> style.withHoverEvent(
                        new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal(rank.getHoverText().replace("\\n", "\n")))));
            }

            src.sendSystemMessage(line);
        }

        // Navigation footer
        if (totalPages > 1) {
            MutableComponent nav = Component.literal("§7");

            if (page > 1) {
                MutableComponent prev = Component.literal("§e[← Prev]");
                final int prevPage = page - 1;
                prev.withStyle(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ranks " + prevPage))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("§7Go to page " + prevPage))));
                nav.append(prev);
            }

            nav.append(Component.literal(" §6Page " + page + "/" + totalPages + " "));

            if (page < totalPages) {
                MutableComponent next = Component.literal("§e[Next →]");
                final int nextPage = page + 1;
                next.withStyle(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ranks " + nextPage))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("§7Go to page " + nextPage))));
                nav.append(next);
            }

            src.sendSystemMessage(nav);
        }

        src.sendSystemMessage(Component.literal("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        return 1;
    }
}
