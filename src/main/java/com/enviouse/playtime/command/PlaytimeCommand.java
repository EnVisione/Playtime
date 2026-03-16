package com.enviouse.playtime.command;

import com.enviouse.playtime.Config;
import com.enviouse.playtime.Playtime;
import com.enviouse.playtime.data.PlayerDataRepository;
import com.enviouse.playtime.data.PlayerRecord;
import com.enviouse.playtime.data.RankDefinition;
import com.enviouse.playtime.integration.LuckPermsService;
import com.enviouse.playtime.network.PlaytimeDataS2CPacket;
import com.enviouse.playtime.network.PlaytimeNetwork;
import com.enviouse.playtime.service.RankEngine;
import com.enviouse.playtime.service.SessionTracker;
import com.enviouse.playtime.util.TimeParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
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
 * /playtime displayrank set <name> — set a cosmetic display rank (requires Technician+).
 * /playtime displayrank clear — remove your display rank.
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
                        .then(Commands.literal("displayrank")
                                .then(Commands.literal("set")
                                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                                .executes(PlaytimeCommand::executeDisplayRankSet)
                                        )
                                )
                                .then(Commands.literal("clear")
                                        .executes(PlaytimeCommand::executeDisplayRankClear)
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

        if (Playtime.getRepository() == null || !Playtime.getRepository().isLoaded()) {
            src.sendFailure(Component.literal("Playtime system not ready (data failed to load)."));
            return 0;
        }

        // If the client has the mod, send the packet to open the GUI
        if (PlaytimeNetwork.hasModChannel(player)) {
            sendPlaytimePacket(player);
        } else {
            // Client doesn't have the mod — send text fallback in chat
            sendPlaytimeText(player);
        }
        return 1;
    }

    /** Build and send the full playtime data packet to a player. Used by /playtime and claim handler. */
    public static void sendPlaytimePacket(ServerPlayer player) {
        PlayerDataRepository repo = Playtime.getRepository();
        SessionTracker tracker = Playtime.getSessionTracker();
        RankEngine engine = Playtime.getRankEngine();
        LuckPermsService lp = Playtime.getLuckPerms();

        if (repo == null || !repo.isLoaded()) return;

        PlayerRecord record = repo.getPlayer(player.getUUID());
        if (record == null) {
            player.sendSystemMessage(Component.literal("§cNo playtime data found!"));
            return;
        }

        long sessionTicks = tracker != null ? tracker.getSessionTicks(player.getUUID()) : 0;
        long totalTicks = record.getTotalPlaytimeTicks() + sessionTicks;

        // Current rank = the stored/claimed rank (not the earned one)
        String storedRankId = record.getCurrentRankId();
        RankDefinition currentRank;
        if (storedRankId != null) {
            RankDefinition stored = Playtime.getRankConfig().getRankById(storedRankId);
            currentRank = stored != null ? stored : engine.getCurrentRank(totalTicks);
        } else {
            currentRank = engine.getCurrentRank(0); // first rank
        }
        // Next rank = the next rank after the claimed one (that hasn't been claimed yet)
        RankDefinition nextRank = engine.getNextRank(currentRank);

        boolean isAfk = tracker != null && tracker.isAfk(player.getUUID());
        boolean isMaxRank = (nextRank == null);
        long ticksToNext = isMaxRank ? 0 : Math.max(0, nextRank.getThresholdTicks() - totalTicks);

        // Gather top 3 leaderboard (include session ticks for accuracy)
        List<PlayerRecord> sorted = new ArrayList<>(repo.getAllPlayers());
        sorted.sort(Comparator.comparingLong(PlayerRecord::getTotalPlaytimeTicks).reversed());
        int top3Count = Math.min(3, sorted.size());
        String[] top3Names = new String[3];
        java.util.UUID[] top3Uuids = new java.util.UUID[3];
        long[] top3Ticks = new long[3];
        String[] top3RankNames = new String[3];
        String[] top3RankColors = new String[3];
        boolean[] top3IsAfk = new boolean[3];
        String[] top3SkinUrls = new String[3];
        for (int i = 0; i < top3Count; i++) {
            PlayerRecord r = sorted.get(i);
            long rSession = tracker != null ? tracker.getSessionTicks(r.getUuid()) : 0;
            long rTotal = r.getTotalPlaytimeTicks() + rSession;
            RankDefinition rank = engine.getCurrentRank(rTotal);
            top3Names[i] = r.getLastUsername() != null ? r.getLastUsername() : r.getUuid().toString().substring(0, 8);
            top3Uuids[i] = r.getUuid();
            top3Ticks[i] = rTotal;
            top3RankNames[i] = rank.getDisplayName();
            top3RankColors[i] = lp.getDisplayColor(rank);
            // Freeze counter if player is offline OR afk
            boolean playerOnline = player.getServer().getPlayerList().getPlayer(r.getUuid()) != null;
            top3IsAfk[i] = !playerOnline || (tracker != null && tracker.isAfk(r.getUuid()));
            top3SkinUrls[i] = r.getSkinUrl() != null ? r.getSkinUrl() : "";
        }

        // Build full rank list for the ranks panel
        List<RankDefinition> allRankDefs = Playtime.getRankConfig().getRanks();
        List<PlaytimeDataS2CPacket.RankEntry> rankEntries = new ArrayList<>();
        int claimedRankOrder = currentRank.getSortOrder();
        for (RankDefinition rd : allRankDefs) {
            if (!rd.isVisible()) continue;
            boolean earned = totalTicks >= rd.getThresholdTicks();
            boolean claimed = rd.getSortOrder() <= claimedRankOrder;
            rankEntries.add(new PlaytimeDataS2CPacket.RankEntry(
                    rd.getId(), rd.getDisplayName(), lp.getDisplayColor(rd),
                    rd.getThresholdTicks(),
                    rd.getDefaultItem() != null ? rd.getDefaultItem() : "",
                    rd.getClaims(), rd.getForceloads(), rd.getInactivityDays(),
                    earned, claimed, rd.getSortOrder()));
        }

        // Build full player list for the list view (sorted by playtime desc)
        List<PlaytimeDataS2CPacket.PlayerListEntry> playerListEntries = new ArrayList<>();
        for (PlayerRecord r : sorted) {
            long rSession = tracker != null ? tracker.getSessionTicks(r.getUuid()) : 0;
            long rTotal = r.getTotalPlaytimeTicks() + rSession;
            RankDefinition rank = engine.getCurrentRank(rTotal);
            String pName = r.getLastUsername() != null ? r.getLastUsername() : r.getUuid().toString().substring(0, 8);

            boolean pOnline = player.getServer().getPlayerList().getPlayer(r.getUuid()) != null;
            byte status; // 0=online, 1=afk, 2=offline
            if (!pOnline) {
                status = 2;
            } else if (tracker != null && tracker.isAfk(r.getUuid())) {
                status = 1;
            } else {
                status = 0;
            }

            playerListEntries.add(new PlaytimeDataS2CPacket.PlayerListEntry(
                    pName, r.getUuid(), rTotal, rank.getDisplayName(), lp.getDisplayColor(rank), status,
                    r.getFirstJoinEpochMs(), r.getLastSeenEpochMs(), r.getDisplayRank(),
                    r.getSkinUrl() != null ? r.getSkinUrl() : ""));
        }

        boolean viewerIsOp = player.hasPermissions(Config.adminPermissionLevel);
        boolean canSetDisplayRank = meetsDisplayRankMinimum(currentRank);

        // Build and send the S2C packet — client opens the GUI
        PlaytimeDataS2CPacket packet = new PlaytimeDataS2CPacket(
                player.getGameProfile().getName(),
                player.getUUID(),
                totalTicks,
                currentRank.getDisplayName(),
                lp.getDisplayColor(currentRank),
                isMaxRank ? "" : nextRank.getDisplayName(),
                isMaxRank ? "" : lp.getDisplayColor(nextRank),
                ticksToNext,
                isAfk,
                currentRank.getClaims(),
                currentRank.getForceloads(),
                currentRank.getInactivityDays(),
                Config.claimsEnabled,
                Config.forceloadsEnabled,
                isMaxRank,
                viewerIsOp,
                record.getDisplayRank(),
                canSetDisplayRank,
                top3Count, top3Names, top3Uuids, top3Ticks, top3RankNames, top3RankColors,
                top3IsAfk, top3SkinUrls,
                rankEntries,
                playerListEntries
        );

        PlaytimeNetwork.sendToPlayer(player, packet);
    }

    /**
     * Send playtime stats as plain chat text — used when the client doesn't have the mod GUI.
     * Mirrors the same info that the GUI shows: total playtime, rank, next rank, AFK status.
     */
    private static void sendPlaytimeText(ServerPlayer player) {
        PlayerDataRepository repo = Playtime.getRepository();
        SessionTracker tracker = Playtime.getSessionTracker();
        RankEngine engine = Playtime.getRankEngine();
        LuckPermsService lp = Playtime.getLuckPerms();

        if (repo == null || !repo.isLoaded()) return;

        PlayerRecord record = repo.getPlayer(player.getUUID());
        if (record == null) {
            player.sendSystemMessage(Component.literal("§cNo playtime data found!"));
            return;
        }

        long sessionTicks = tracker != null ? tracker.getSessionTicks(player.getUUID()) : 0;
        long totalTicks = record.getTotalPlaytimeTicks() + sessionTicks;
        boolean isAfk = tracker != null && tracker.isAfk(player.getUUID());

        // Current rank
        String storedRankId = record.getCurrentRankId();
        RankDefinition currentRank;
        if (storedRankId != null) {
            RankDefinition stored = Playtime.getRankConfig().getRankById(storedRankId);
            currentRank = stored != null ? stored : engine.getCurrentRank(totalTicks);
        } else {
            currentRank = engine.getCurrentRank(0);
        }
        RankDefinition nextRank = engine.getNextRank(currentRank);
        boolean isMaxRank = (nextRank == null);

        // Header
        player.sendSystemMessage(Component.literal("§6━━━━━━━━━━ Playtime Stats ━━━━━━━━━━"));

        // Total playtime + AFK status
        String afkLabel = isAfk ? " §c[AFK - Not Tracking]" : " §a[Active]";
        player.sendSystemMessage(Component.literal("§7Total Playtime: §f" + TimeParser.formatTicks(totalTicks) + afkLabel));

        // Current rank (coloured)
        player.sendSystemMessage(Component.literal("§7Current Rank: ").append(lp.getStyledRankName(currentRank)));

        // Claims & forceloads (if enabled)
        if (Config.claimsEnabled || Config.forceloadsEnabled) {
            StringBuilder benefits = new StringBuilder("§7  ➤ ");
            if (Config.claimsEnabled) {
                benefits.append(currentRank.getClaims()).append(" claims");
            }
            if (Config.forceloadsEnabled) {
                if (Config.claimsEnabled) benefits.append(", ");
                benefits.append(currentRank.getForceloads()).append(" forceloads");
            }
            int inact = currentRank.getInactivityDays();
            benefits.append(", ").append(inact < 0 ? "never expires" : inact + "d max inactivity");
            player.sendSystemMessage(Component.literal(benefits.toString()));
        }

        // Next rank or max
        if (isMaxRank) {
            player.sendSystemMessage(Component.literal("§aMax rank achieved!"));
        } else {
            long ticksToNext = Math.max(0, nextRank.getThresholdTicks() - totalTicks);
            player.sendSystemMessage(Component.literal("§7Next Rank: ")
                    .append(lp.getStyledRankName(nextRank))
                    .append(Component.literal(" §7(" + TimeParser.formatTicks(ticksToNext) + " remaining)")));
        }

        // Footer
        player.sendSystemMessage(Component.literal("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
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

    // ── Display Rank ────────────────────────────────────────────────────────────

    private static int executeDisplayRankSet(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayer();
        if (player == null) {
            src.sendFailure(Component.literal("This command can only be run by a player."));
            return 0;
        }

        PlayerDataRepository repo = Playtime.getRepository();
        if (repo == null || !repo.isLoaded()) {
            src.sendFailure(Component.literal("Playtime system not ready."));
            return 0;
        }

        PlayerRecord record = repo.getPlayer(player.getUUID());
        if (record == null) {
            src.sendFailure(Component.literal("§cNo playtime data found."));
            return 0;
        }

        // Check if player has reached the minimum rank for display rank
        String rankId = record.getCurrentRankId();
        if (rankId != null) {
            RankDefinition rank = Playtime.getRankConfig().getRankById(rankId);
            if (rank == null || !meetsDisplayRankMinimum(rank)) {
                String minId = Config.displayRankMinimumId;
                String minName = minId != null && !minId.isEmpty() ? minId : "the required";
                src.sendFailure(Component.literal("§cYou must reach §e" + minName + " §crank or higher to set a display rank."));
                return 0;
            }
        } else {
            String minId = Config.displayRankMinimumId;
            String minName = minId != null && !minId.isEmpty() ? minId : "the required";
            src.sendFailure(Component.literal("§cYou must reach §e" + minName + " §crank or higher to set a display rank."));
            return 0;
        }

        String name = StringArgumentType.getString(ctx, "name").trim();
        if (name.isEmpty() || name.length() > 32) {
            src.sendFailure(Component.literal("§cDisplay rank must be 1-32 characters."));
            return 0;
        }

        record.setDisplayRank(name);
        repo.markDirty();

        // Sync LP suffix: priority 50, bold+underline formatting
        LuckPermsService lp = Playtime.getLuckPerms();
        if (lp != null && lp.isAvailable()) {
            lp.setSuffix(player.getUUID(), 50, " &n" + name);
        }

        src.sendSuccess(() -> Component.literal("§aDisplay rank set to: §n" + name), false);
        return 1;
    }

    private static int executeDisplayRankClear(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayer();
        if (player == null) {
            src.sendFailure(Component.literal("This command can only be run by a player."));
            return 0;
        }

        PlayerDataRepository repo = Playtime.getRepository();
        if (repo == null || !repo.isLoaded()) {
            src.sendFailure(Component.literal("Playtime system not ready."));
            return 0;
        }

        PlayerRecord record = repo.getPlayer(player.getUUID());
        if (record == null) {
            src.sendFailure(Component.literal("§cNo playtime data found."));
            return 0;
        }

        record.setDisplayRank("");
        repo.markDirty();

        // Remove LP suffix at priority 50
        LuckPermsService lp = Playtime.getLuckPerms();
        if (lp != null && lp.isAvailable()) {
            lp.removeSuffix(player.getUUID(), 50);
        }

        src.sendSuccess(() -> Component.literal("§aDisplay rank cleared."), false);
        return 1;
    }

    /**
     * Check whether a rank meets the configurable minimum for the display rank feature.
     * Uses Config.displayRankMinimumId to look up the threshold rank's sort order.
     * Returns true if the config value is empty (all ranks allowed).
     */
    private static boolean meetsDisplayRankMinimum(RankDefinition playerRank) {
        String minId = Config.displayRankMinimumId;
        if (minId == null || minId.isEmpty()) return true;
        if (Playtime.getRankConfig() == null) return false;
        RankDefinition threshold = Playtime.getRankConfig().getRankById(minId);
        if (threshold == null) return true; // unknown rank ID → don't block
        return playerRank.getSortOrder() >= threshold.getSortOrder();
    }
}
