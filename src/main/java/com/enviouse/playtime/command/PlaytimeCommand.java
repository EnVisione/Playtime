package com.enviouse.playtime.command;

import com.enviouse.playtime.Config;
import com.enviouse.playtime.Playtime;
import com.enviouse.playtime.data.PlayerDataRepository;
import com.enviouse.playtime.data.PlayerRecord;
import com.enviouse.playtime.data.RankDefinition;
import com.enviouse.playtime.integration.LuckPermsService;
import com.enviouse.playtime.network.PlaytimeDataS2CPacket;
import com.enviouse.playtime.network.PlaytimeNetwork;
import com.enviouse.playtime.network.PlayerSearchResultS2CPacket;
import com.enviouse.playtime.service.RankEngine;
import com.enviouse.playtime.service.SessionTracker;
import com.enviouse.playtime.util.TimeParser;
import com.enviouse.playtime.config.RankConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
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
 * /playtime claim — claim your highest earned rank (for players without the GUI).
 * /playtime displayrank set <name> — set a cosmetic display rank (requires minimum rank).
 * /playtime displayrank clear — remove your display rank.
 */
public class PlaytimeCommand {

    /** Suggests rank display names from the loaded rank config for tab-completion. */
    private static final SuggestionProvider<CommandSourceStack> RANK_NAME_SUGGESTIONS = (ctx, builder) -> {
        RankConfig config = Playtime.getRankConfig();
        if (config != null) {
            List<String> names = new ArrayList<>();
            for (RankDefinition rank : config.getRanks()) {
                if (rank.isVisible()) names.add(rank.getDisplayName());
            }
            return SharedSuggestionProvider.suggest(names, builder);
        }
        return builder.buildFuture();
    };

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
                        .then(Commands.literal("claim")
                                .executes(PlaytimeCommand::executeClaim)
                        )
                        .then(Commands.literal("displayrank")
                                .then(Commands.literal("set")
                                        .then(Commands.argument("name", StringArgumentType.word())
                                                .suggests(RANK_NAME_SUGGESTIONS)
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

    /**
     * Maximum number of player entries to include in the initial S2C packet.
     * The client can request more via server-side search (PlayerSearchC2SPacket).
     */
    private static final int MAX_PLAYER_LIST_ENTRIES = 100;

    /** Build and send the full playtime data packet to a player. Used by /playtime and claim handler. */
    public static void sendPlaytimePacket(ServerPlayer player) {
        try {
            sendPlaytimePacketUnsafe(player);
        } catch (Exception e) {
            // Catch ANY exception to prevent the player from being kicked.
            // Log the error and send a graceful chat message instead.
            com.mojang.logging.LogUtils.getLogger().error(
                    "[Playtime] Failed to build playtime packet for {}: {}",
                    player.getGameProfile().getName(), e.getMessage(), e);
            player.sendSystemMessage(Component.literal(
                    "§cPlaytime data could not be loaded right now. Please try again."));
        }
    }

    /** Internal packet builder — may throw; caller wraps in try/catch. */
    private static void sendPlaytimePacketUnsafe(ServerPlayer player) {
        PlayerDataRepository repo = Playtime.getRepository();
        SessionTracker tracker = Playtime.getSessionTracker();
        RankEngine engine = Playtime.getRankEngine();

        if (repo == null || !repo.isLoaded() || engine == null) return;

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
            top3RankNames[i] = safe(rank.getDisplayName());
            top3RankColors[i] = safe(Playtime.getDisplayColor(rank));
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
                    safe(rd.getId()), safe(rd.getDisplayName()), safe(Playtime.getDisplayColor(rd)),
                    rd.getThresholdTicks(),
                    rd.getDefaultItem() != null ? rd.getDefaultItem() : "",
                    rd.getClaims(), rd.getForceloads(), rd.getInactivityDays(),
                    earned, claimed, rd.getSortOrder()));
        }

        // Build player list (capped to prevent oversized packets that kick the player)
        int playerCap = Math.min(sorted.size(), MAX_PLAYER_LIST_ENTRIES);
        List<PlaytimeDataS2CPacket.PlayerListEntry> playerListEntries = new ArrayList<>(playerCap);
        for (int idx = 0; idx < playerCap; idx++) {
            PlayerRecord r = sorted.get(idx);
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
                    pName, r.getUuid(), rTotal, safe(rank.getDisplayName()), safe(Playtime.getDisplayColor(rank)), status,
                    r.getFirstJoinEpochMs(), r.getLastSeenEpochMs(), safe(r.getDisplayRank()),
                    r.getSkinUrl() != null ? r.getSkinUrl() : ""));
        }

        boolean viewerIsOp = player.hasPermissions(Config.adminPermissionLevel);
        boolean canSetDisplayRank = meetsDisplayRankMinimum(currentRank);

        // Resolve the display name of the minimum rank for client tooltip
        String displayRankMinName = "";
        String minId = Config.displayRankMinimumId;
        if (minId != null && !minId.isEmpty() && Playtime.getRankConfig() != null) {
            RankDefinition minRank = Playtime.getRankConfig().getRankById(minId);
            displayRankMinName = minRank != null ? minRank.getDisplayName() : minId;
        }

        // Build and send the S2C packet — client opens the GUI
        PlaytimeDataS2CPacket packet = new PlaytimeDataS2CPacket(
                player.getGameProfile().getName(),
                player.getUUID(),
                totalTicks,
                safe(currentRank.getDisplayName()),
                safe(Playtime.getDisplayColor(currentRank)),
                isMaxRank ? "" : safe(nextRank.getDisplayName()),
                isMaxRank ? "" : safe(Playtime.getDisplayColor(nextRank)),
                ticksToNext,
                isAfk,
                currentRank.getClaims(),
                currentRank.getForceloads(),
                currentRank.getInactivityDays(),
                Config.claimsEnabled,
                Config.forceloadsEnabled,
                isMaxRank,
                viewerIsOp,
                safe(record.getDisplayRank()),
                canSetDisplayRank,
                safe(displayRankMinName),
                top3Count, top3Names, top3Uuids, top3Ticks, top3RankNames, top3RankColors,
                top3IsAfk, top3SkinUrls,
                rankEntries,
                playerListEntries,
                sorted.size()  // totalPlayerCount — may be larger than playerListEntries.size()
        );

        PlaytimeNetwork.sendToPlayer(player, packet);
    }

    /**
     * Build and send filtered player search results to a player.
     * Called when the client sends a {@link com.enviouse.playtime.network.PlayerSearchC2SPacket}.
     *
     * @param player     the requesting player
     * @param query      search string (name, rank, or display rank — case-insensitive)
     * @param onlineOnly if true, only include currently online or AFK players
     */
    public static void sendSearchResults(ServerPlayer player, String query, boolean onlineOnly) {
        sendSearchResults(player, query, onlineOnly, 0);
    }

    /**
     * Build and send filtered player search results to a player with offset-based pagination.
     *
     * @param player     the requesting player
     * @param query      search string (name, rank, or display rank — case-insensitive)
     * @param onlineOnly if true, only include currently online or AFK players
     * @param offset     number of matching results to skip (for "Load More")
     */
    public static void sendSearchResults(ServerPlayer player, String query, boolean onlineOnly, int offset) {
        try {
            sendSearchResultsUnsafe(player, query, onlineOnly, offset);
        } catch (Exception e) {
            com.mojang.logging.LogUtils.getLogger().error(
                    "[Playtime] Failed to build search results for {}: {}",
                    player.getGameProfile().getName(), e.getMessage(), e);
        }
    }

    private static void sendSearchResultsUnsafe(ServerPlayer player, String query, boolean onlineOnly, int offset) {
        PlayerDataRepository repo = Playtime.getRepository();
        SessionTracker tracker = Playtime.getSessionTracker();
        RankEngine engine = Playtime.getRankEngine();

        if (repo == null || !repo.isLoaded() || engine == null) return;

        List<PlayerRecord> sorted = new ArrayList<>(repo.getAllPlayers());
        sorted.sort(Comparator.comparingLong(PlayerRecord::getTotalPlaytimeTicks).reversed());

        String q = query != null ? query.toLowerCase(java.util.Locale.ROOT).trim() : "";

        List<PlaytimeDataS2CPacket.PlayerListEntry> results = new ArrayList<>();
        int totalMatches = 0;

        for (PlayerRecord r : sorted) {
            long rSession = tracker != null ? tracker.getSessionTicks(r.getUuid()) : 0;
            long rTotal = r.getTotalPlaytimeTicks() + rSession;
            RankDefinition rank = engine.getCurrentRank(rTotal);
            String pName = r.getLastUsername() != null ? r.getLastUsername() : r.getUuid().toString().substring(0, 8);

            boolean pOnline = player.getServer().getPlayerList().getPlayer(r.getUuid()) != null;
            byte status;
            if (!pOnline) {
                status = 2;
            } else if (tracker != null && tracker.isAfk(r.getUuid())) {
                status = 1;
            } else {
                status = 0;
            }

            // Online-only filter
            if (onlineOnly && status == 2) continue;

            // Query filter (name, rank name, or display rank)
            if (!q.isEmpty()) {
                boolean matches = pName.toLowerCase(java.util.Locale.ROOT).contains(q)
                        || rank.getDisplayName().toLowerCase(java.util.Locale.ROOT).contains(q)
                        || (r.getDisplayRank() != null && !r.getDisplayRank().isEmpty()
                            && r.getDisplayRank().toLowerCase(java.util.Locale.ROOT).contains(q));
                if (!matches) continue;
            }

            totalMatches++;

            // Skip entries before the requested offset (for "Load More" pagination)
            if (totalMatches <= offset) continue;

            // Cap results per packet
            if (results.size() < PlayerSearchResultS2CPacket.MAX_RESULTS) {
                results.add(new PlaytimeDataS2CPacket.PlayerListEntry(
                        pName, r.getUuid(), rTotal, safe(rank.getDisplayName()), safe(Playtime.getDisplayColor(rank)), status,
                        r.getFirstJoinEpochMs(), r.getLastSeenEpochMs(), safe(r.getDisplayRank()),
                        r.getSkinUrl() != null ? r.getSkinUrl() : ""));
            }
        }

        PlayerSearchResultS2CPacket packet = new PlayerSearchResultS2CPacket(query, onlineOnly, offset, results, totalMatches);
        PlaytimeNetwork.sendToPlayer(player, packet);
    }

    /** Null-guard for strings written to the network buffer — prevents NPE kicks. */
    private static String safe(String s) {
        return s != null ? s : "";
    }

    /**
     * Send playtime stats as plain chat text — used when the client doesn't have the mod GUI.
     * Mirrors the same info that the GUI shows: total playtime, rank, next rank, AFK status.
     * Uses §r resets before styled rank components so §-code formatting never bleeds into them.
     */
    private static void sendPlaytimeText(ServerPlayer player) {
        PlayerDataRepository repo = Playtime.getRepository();
        SessionTracker tracker = Playtime.getSessionTracker();
        RankEngine engine = Playtime.getRankEngine();

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
        player.sendSystemMessage(Component.literal("§6========== Playtime Stats =========="));

        // Total playtime + AFK status
        String afkLabel = isAfk ? " §c[AFK - Not Tracking]" : " §a[Active]";
        player.sendSystemMessage(Component.literal("§7Total Playtime: §f" + TimeParser.formatTicks(totalTicks) + afkLabel));

        // Current rank (coloured) — §r reset before the styled component
        player.sendSystemMessage(Component.literal("§7Current Rank: §r")
                .append(Playtime.getStyledRankName(currentRank)));

        // Claims & forceloads (if enabled)
        if (Config.claimsEnabled || Config.forceloadsEnabled) {
            StringBuilder benefits = new StringBuilder("§7  - ");
            if (Config.claimsEnabled) {
                benefits.append("§f").append(currentRank.getClaims()).append("§7 claims");
            }
            if (Config.forceloadsEnabled) {
                if (Config.claimsEnabled) benefits.append("§7, ");
                benefits.append("§f").append(currentRank.getForceloads()).append("§7 forceloads");
            }
            int inact = currentRank.getInactivityDays();
            benefits.append("§7, §f").append(inact < 0 ? "never" : inact + "d").append("§7 max inactivity");
            player.sendSystemMessage(Component.literal(benefits.toString()));
        }

        // Next rank or max
        if (isMaxRank) {
            player.sendSystemMessage(Component.literal("§aMax rank achieved!"));
        } else {
            long ticksToNext = Math.max(0, nextRank.getThresholdTicks() - totalTicks);
            // §r reset before the styled rank component, then §r again after it
            player.sendSystemMessage(Component.literal("§7Next Rank: §r")
                    .append(Playtime.getStyledRankName(nextRank))
                    .append(Component.literal("§r §7(" + TimeParser.formatTicks(ticksToNext) + " remaining)")));
        }

        // Check if there are unclaimed ranks
        RankDefinition highestEarned = engine.getCurrentRank(totalTicks);
        boolean hasUnclaimed = (currentRank.getSortOrder() < highestEarned.getSortOrder());
        if (hasUnclaimed) {
            MutableComponent claimMsg = Component.literal("§b✦ New rank available: §r")
                    .append(Playtime.getStyledRankName(highestEarned))
                    .append(Component.literal("§b! "));
            MutableComponent claimLink = Component.literal("§e§n[Click to Claim]");
            claimLink.withStyle(style -> style
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/playtime claim"))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("§7Click to claim your rank"))));
            claimMsg.append(claimLink);
            player.sendSystemMessage(claimMsg);
        }

        // Footer
        player.sendSystemMessage(Component.literal("§6===================================="));
    }

    /**
     * /playtime claim — claims the highest earned rank for players who don't have the GUI.
     * Mirrors the logic in ClaimRankC2SPacket but runs entirely server-side.
     */
    private static int executeClaim(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayer();
        if (player == null) {
            src.sendFailure(Component.literal("This command can only be run by a player."));
            return 0;
        }

        PlayerDataRepository repo = Playtime.getRepository();
        SessionTracker tracker = Playtime.getSessionTracker();
        RankEngine engine = Playtime.getRankEngine();
        RankConfig rankConfig = Playtime.getRankConfig();

        if (repo == null || !repo.isLoaded() || engine == null || rankConfig == null) {
            src.sendFailure(Component.literal("§cPlaytime system not ready."));
            return 0;
        }

        PlayerRecord record = repo.getPlayer(player.getUUID());
        if (record == null) {
            src.sendFailure(Component.literal("§cNo playtime data found."));
            return 0;
        }

        // Flush session ticks so total is accurate
        if (tracker != null) {
            tracker.flushAll(player.getServer());
        }

        long totalTicks = record.getTotalPlaytimeTicks();

        // Find the highest rank the player has earned
        RankDefinition highestEarned = engine.getCurrentRank(totalTicks);

        // Determine the current stored rank
        String storedRankId = record.getCurrentRankId();
        RankDefinition oldRank = storedRankId != null ? rankConfig.getRankById(storedRankId) : null;

        // Check if there's anything new to claim
        if (oldRank != null && oldRank.getSortOrder() >= highestEarned.getSortOrder()) {
            src.sendSuccess(() -> Component.literal("§7You are already at your highest earned rank: §r")
                    .append(Playtime.getStyledRankName(oldRank))
                    .append(Component.literal("§7.")), false);
            return 0;
        }

        // Apply the rank claim (updates stored rank, syncs LP, broadcasts, plays effects)
        engine.applyRankClaim(player.getServer(), player.getUUID(), oldRank, highestEarned);
        repo.save(false);

        src.sendSuccess(() -> Component.literal("§a✦ Rank claimed: §r")
                .append(Playtime.getStyledRankName(highestEarned))
                .append(Component.literal("§a!")), false);

        // If the player has the GUI mod, refresh the GUI packet too
        if (PlaytimeNetwork.hasModChannel(player)) {
            sendPlaytimePacket(player);
        }

        return 1;
    }

    private static int executeTop(CommandContext<CommandSourceStack> ctx, int page) {
        CommandSourceStack src = ctx.getSource();
        PlayerDataRepository repo = Playtime.getRepository();
        RankEngine engine = Playtime.getRankEngine();

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
                    .append(Playtime.getStyledRankName(rank))
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

        // Look up the rank by display name — only real ranks are allowed
        RankDefinition displayRankDef = Playtime.getRankConfig().getRankByDisplayName(name);
        if (displayRankDef == null) {
            src.sendFailure(Component.literal("§cUnknown rank: §e" + name + "§c. Use tab-completion to pick a valid rank."));
            return 0;
        }

        record.setDisplayRank(displayRankDef.getDisplayName());
        repo.markDirty();

        // Sync LP suffix with the rank's actual colour
        LuckPermsService lp = Playtime.getLuckPerms();
        if (lp != null && lp.isAvailable()) {
            String colorStr = displayRankDef.getFallbackColor();
            lp.setSuffix(player.getUUID(), 50, com.enviouse.playtime.util.ColorUtil.buildLPSuffix(colorStr, displayRankDef.getDisplayName()));
        }

        src.sendSuccess(() -> Component.literal("§aDisplay rank set to: §n" + displayRankDef.getDisplayName()), false);
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
