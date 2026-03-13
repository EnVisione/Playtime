package com.enviouse.playtime.command;

import com.enviouse.playtime.Config;
import com.enviouse.playtime.Playtime;
import com.enviouse.playtime.config.RankConfig;
import com.enviouse.playtime.data.RankDefinition;
import com.enviouse.playtime.integration.LuckPermsService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

/**
 * /ranks — list all public ranks with hours, descriptions, claims, forceloads, inactivity.
 * If a rank has a description, it replaces the claims/forceloads/inactivity line.
 * If claims or forceloads features are disabled, those columns are hidden.
 * If a rank has hover text, it appears on mouse-over.
 */
public class RanksCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("ranks")
                        .executes(RanksCommand::execute)
        );
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        RankConfig rankConfig = Playtime.getRankConfig();
        LuckPermsService lp = Playtime.getLuckPerms();

        src.sendSystemMessage(Component.literal("§6━━━━━━━━━━━ Ranks ━━━━━━━━━━━"));

        // Build subtitle based on enabled features
        StringBuilder subtitle = new StringBuilder("§7(Playtime");
        if (Config.claimsEnabled) subtitle.append(" — Claims");
        if (Config.forceloadsEnabled) subtitle.append(", Forceloads");
        subtitle.append(")");
        src.sendSystemMessage(Component.literal(subtitle.toString()));

        for (RankDefinition rank : rankConfig.getRanks()) {
            if (!rank.isVisible()) continue;

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

        src.sendSystemMessage(Component.literal("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        return 1;
    }
}
