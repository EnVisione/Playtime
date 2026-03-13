package com.enviouse.playtime.command;

import com.enviouse.playtime.Playtime;
import com.enviouse.playtime.config.RankConfig;
import com.enviouse.playtime.data.RankDefinition;
import com.enviouse.playtime.integration.LuckPermsService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * /ranks — list all public ranks with hours, claims, forceloads, inactivity.
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
        src.sendSystemMessage(Component.literal("§7(Playtime — Claims, Forceloads, Max inactivity)"));

        for (RankDefinition rank : rankConfig.getRanks()) {
            if (!rank.isVisible()) continue;

            String color = lp.getDisplayColor(rank);
            String inactivityText = rank.getInactivityDays() == -1 ? "Never" : rank.getInactivityDays() + "d";

            src.sendSystemMessage(Component.literal(
                    "§7- " + color + rank.getDisplayName() + "§r §7- §f" + rank.getThresholdHours() + "h §7- §f" +
                            rank.getClaims() + "§7 claims, §f" + rank.getForceloads() + "§7 forceloads, §f" +
                            inactivityText + "§7 inactivity"
            ));
        }

        src.sendSystemMessage(Component.literal("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        return 1;
    }
}

