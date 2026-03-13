package com.enviouse.playtime.command;

import com.enviouse.playtime.Config;
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
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * /playtime — show your own stats.
 * /playtime top [page] — leaderboard.
 */
public class PlaytimeCommand {


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

        boolean isAfk = tracker != null && tracker.isAfk(player.getUUID());
        MutableComponent statusText = isAfk
                ? Component.literal(" ").append(Component.literal("[AFK - Not Tracking]").withStyle(net.minecraft.ChatFormatting.RED))
                : Component.literal(" ").append(Component.literal("[Active]").withStyle(net.minecraft.ChatFormatting.GREEN));

        player.sendSystemMessage(Component.literal("§6━━━━━━━━━━ Playtime Stats ━━━━━━━━━━"));

        player.sendSystemMessage(Component.literal("§7Total Playtime: §f" + TimeParser.formatTicks(totalTicks)).append(statusText));

        player.sendSystemMessage(Component.literal("§7Current Rank: ").append(lp.getStyledRankName(currentRank)));

        // Build benefit detail line based on enabled features
        StringBuilder benefits = new StringBuilder("§7  ➤ ");
        boolean hasPrev = false;
        if (Config.claimsEnabled) {
            benefits.append("§f").append(currentRank.getClaims()).append("§7 claims");
            hasPrev = true;
        }
        if (Config.forceloadsEnabled) {
            if (hasPrev) benefits.append(", ");
            benefits.append("§f").append(currentRank.getForceloads()).append("§7 forceloads");
            hasPrev = true;
        }
        String inactivityText = currentRank.getInactivityDays() == -1 ? "Never" : currentRank.getInactivityDays() + "d";
        if (hasPrev) benefits.append(", ");
        benefits.append("§f").append(inactivityText).append("§7 max inactivity");
        player.sendSystemMessage(Component.literal(benefits.toString()));

        if (nextRank != null) {
            long ticksNeeded = nextRank.getThresholdTicks() - totalTicks;
            player.sendSystemMessage(Component.literal("§7Next Rank: ")
                    .append(lp.getStyledRankName(nextRank))
                    .append(Component.literal(" §7(" + TimeParser.formatTicks(ticksNeeded) + " remaining)")));
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

        int pageSize = Config.topPageSize;
        int totalPages = Math.max(1, (sorted.size() + pageSize - 1) / pageSize);
        if (page > totalPages) page = totalPages;

        int startIndex = (page - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, sorted.size());

        src.sendSystemMessage(Component.literal("§6━━━━━━━━━ Top Playtime (Page " + page + "/" + totalPages + ") ━━━━━━━━━"));

        for (int i = startIndex; i < endIndex; i++) {
            PlayerRecord record = sorted.get(i);
            RankDefinition rank = engine.getCurrentRank(record.getTotalPlaytimeTicks());
            String name = record.getLastUsername() != null ? record.getLastUsername() : record.getUuid().toString().substring(0, 8);

            src.sendSystemMessage(Component.literal("§7" + (i + 1) + ". §f" + name + " ")
                    .append(Component.literal("[").withStyle(net.minecraft.ChatFormatting.GRAY))
                    .append(lp.getStyledRankName(rank))
                    .append(Component.literal("]").withStyle(net.minecraft.ChatFormatting.GRAY))
                    .append(Component.literal(" §7- §f" + TimeParser.formatTicks(record.getTotalPlaytimeTicks()))));
        }

        // Navigation footer
        if (totalPages > 1) {
            MutableComponent nav = Component.literal("§7");

            if (page > 1) {
                MutableComponent prev = Component.literal("§e[← Prev]");
                final int prevPage = page - 1;
                prev.withStyle(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/playtime top " + prevPage))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("§7Go to page " + prevPage))));
                nav.append(prev);
            }

            nav.append(Component.literal(" §6Page " + page + "/" + totalPages + " "));

            if (page < totalPages) {
                MutableComponent next = Component.literal("§e[Next →]");
                final int nextPage = page + 1;
                next.withStyle(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/playtime top " + nextPage))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("§7Go to page " + nextPage))));
                nav.append(next);
            }

            src.sendSystemMessage(nav);
        }

        src.sendSystemMessage(Component.literal("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        return 1;
    }
}
