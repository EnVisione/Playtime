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
 * /claims — list claim and forceload caps per rank.
 */
public class ClaimsCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("claims")
                        .executes(ClaimsCommand::execute)
        );
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        RankConfig rankConfig = Playtime.getRankConfig();
        LuckPermsService lp = Playtime.getLuckPerms();

        src.sendSystemMessage(Component.literal("§6━━━━━━━━━ Claim Limits ━━━━━━━━━"));
        src.sendSystemMessage(Component.literal("§7(Max claims / Max forceloads by rank)"));

        for (RankDefinition rank : rankConfig.getRanks()) {
            if (!rank.isVisible()) continue;

            String color = lp.getDisplayColor(rank);
            src.sendSystemMessage(Component.literal(
                    color + rank.getDisplayName() + "§r §7- §f" + rank.getClaims() + "§7 claims, §f" +
                            rank.getForceloads() + "§7 forceloads"
            ));
        }

        src.sendSystemMessage(Component.literal("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        return 1;
    }
}

