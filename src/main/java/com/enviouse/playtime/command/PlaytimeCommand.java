package com.enviouse.playtime.command;

import com.enviouse.playtime.Playtime;
import com.enviouse.playtime.data.PlayerDataRepository;
import com.enviouse.playtime.data.PlayerRecord;
import com.enviouse.playtime.data.RankDefinition;
import com.enviouse.playtime.integration.LuckPermsService;
import com.enviouse.playtime.service.RankEngine;
import com.enviouse.playtime.service.SessionTracker;
import com.enviouse.playtime.util.TimeParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * /playtime — show your own stats.
 * /playtime top [page] — leaderboard.
 */
public class PlaytimeCommand {

    private static final int PAGE_SIZE = 10;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("playtime")
                        .executes(PlaytimeCommand::executeSelf)
                        .then(Commands.literal("top")
                                .executes(ctx -> executeTop(ctx, 1))
                                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                        .executes(ctx -> executeTop(ctx, IntegerArgumentType.getInteger(ctx, "page")))
                                )
                        )
        );
    }

    private static int executeSelf(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayer();
        if (player == null) {
            src.sendFailure(Component.literal("This command can only be run by a player."));
            return 0;
        }

        PlayerDataRepository repo = Playtime.getRepository();
        SessionTracker tracker = Playtime.getSessionTracker();
        RankEngine engine = Playtime.getRankEngine();
        LuckPermsService lp = Playtime.getLuckPerms();

        if (repo == null || !repo.isLoaded()) {
            src.sendFailure(Component.literal("Playtime system not ready (data failed to load)."));
            return 0;
        }

        PlayerRecord record = repo.getPlayer(player.getUUID());
        if (record == null) {
            player.sendSystemMessage(Component.literal("§cNo playtime data found!"));
            return 1;
        }

        long sessionTicks = tracker != null ? tracker.getSessionTicks(player.getUUID()) : 0;
        long totalTicks = record.getTotalPlaytimeTicks() + sessionTicks;

        RankDefinition currentRank = engine.getCurrentRank(totalTicks);
        RankDefinition nextRank = engine.getNextRank(currentRank);
        String color = lp.getDisplayColor(currentRank);

        boolean isAfk = tracker != null && tracker.isAfk(player.getUUID());
        String statusText = isAfk ? " §c[AFK - Not Tracking]" : " §a[Active]";

        player.sendSystemMessage(Component.literal("§6━━━━━━━━━━ Playtime Stats ━━━━━━━━━━"));
        player.sendSystemMessage(Component.literal("§7Total Playtime: §f" + TimeParser.formatTicks(totalTicks) + statusText));
        player.sendSystemMessage(Component.literal("§7Current Rank: " + color + currentRank.getDisplayName()));

        String inactivityText = currentRank.getInactivityDays() == -1 ? "Never" : currentRank.getInactivityDays() + "d";
        player.sendSystemMessage(Component.literal("§7  ➤ §f" + currentRank.getClaims() + "§7 claims, §f" +
                currentRank.getForceloads() + "§7 forceloads, §f" +
                inactivityText + "§7 max inactivity"));

        if (nextRank != null) {
            long ticksNeeded = nextRank.getThresholdTicks() - totalTicks;
            String nextColor = lp.getDisplayColor(nextRank);
            player.sendSystemMessage(Component.literal("§7Next Rank: " + nextColor + nextRank.getDisplayName() +
                    " §7(" + TimeParser.formatTicks(ticksNeeded) + " remaining)"));
        } else {
            player.sendSystemMessage(Component.literal("§a§l✓ Max rank achieved!"));
        }

        player.sendSystemMessage(Component.literal("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        return 1;
    }

    private static int executeTop(CommandContext<CommandSourceStack> ctx, int page) {
        CommandSourceStack src = ctx.getSource();
        PlayerDataRepository repo = Playtime.getRepository();
        RankEngine engine = Playtime.getRankEngine();
        LuckPermsService lp = Playtime.getLuckPerms();

        if (repo == null || !repo.isLoaded()) {
            src.sendFailure(Component.literal("Playtime system not ready."));
            return 0;
        }

        List<PlayerRecord> sorted = new ArrayList<>(repo.getAllPlayers());
        sorted.sort(Comparator.comparingLong(PlayerRecord::getTotalPlaytimeTicks).reversed());

        int totalPages = Math.max(1, (sorted.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page > totalPages) page = totalPages;

        int startIndex = (page - 1) * PAGE_SIZE;
        int endIndex = Math.min(startIndex + PAGE_SIZE, sorted.size());

        src.sendSystemMessage(Component.literal("§6━━━━━━━━━ Top Playtime (Page " + page + "/" + totalPages + ") ━━━━━━━━━"));

        for (int i = startIndex; i < endIndex; i++) {
            PlayerRecord record = sorted.get(i);
            RankDefinition rank = engine.getCurrentRank(record.getTotalPlaytimeTicks());
            String color = lp.getDisplayColor(rank);
            String name = record.getLastUsername() != null ? record.getLastUsername() : record.getUuid().toString().substring(0, 8);
            src.sendSystemMessage(Component.literal("§7" + (i + 1) + ". §f" + name + " " +
                    color + "[" + rank.getDisplayName() + "§r" + color + "] §7- §f" +
                    TimeParser.formatTicks(record.getTotalPlaytimeTicks())));
        }

        src.sendSystemMessage(Component.literal("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        return 1;
    }
}

