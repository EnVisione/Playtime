package com.enviouse.playtime.command;

import com.enviouse.playtime.Config;
import com.enviouse.playtime.Playtime;
import com.enviouse.playtime.config.RankConfig;
import com.enviouse.playtime.data.InactivityAction;
import com.enviouse.playtime.data.PlayerDataRepository;
import com.enviouse.playtime.data.PlayerRecord;
import com.enviouse.playtime.data.RankDefinition;
import com.enviouse.playtime.integration.LuckPermsService;
import com.enviouse.playtime.service.BackupService;
import com.enviouse.playtime.service.CleanupService;
import com.enviouse.playtime.service.RankEngine;
import com.enviouse.playtime.util.ColorUtil;
import com.enviouse.playtime.util.TimeParser;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.text.SimpleDateFormat;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * /playtimeadmin — admin command tree.
 * Sub-commands: list, add, remove, set, reset,
 * rank (add/remove/edit/list/info/set/sync/sethover/edithover/inactivity),
 * cleanup, backup now, reload, import.
 */
@SuppressWarnings({"deprecation", "removal"}) // ResourceLocation constructor is the correct API for 1.20.1
public class PlaytimeAdminCommand {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    /** Suggests rank IDs from the loaded rank config. */
    private static final SuggestionProvider<CommandSourceStack> RANK_ID_SUGGESTIONS = (ctx, builder) -> {
        RankConfig config = Playtime.getRankConfig();
        if (config != null) {
            return SharedSuggestionProvider.suggest(config.getRankIds(), builder);
        }
        return builder.buildFuture();
    };

    /** Suggests editable rank field names. */
    private static final List<String> RANK_FIELDS = List.of(
            "displayName", "visible", "hours", "claims", "forceloads",
            "inactivityDays", "luckpermsGroup", "fallbackColor", "sortOrder",
            "syncWithLuckPerms", "description", "hoverText", "defaultItem", "phaseText"
    );

    private static final SuggestionProvider<CommandSourceStack> RANK_FIELD_SUGGESTIONS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(RANK_FIELDS, builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("playtimeadmin")
                        .requires(src -> src.hasPermission(Config.adminPermissionLevel))

                        // /playtimeadmin list <player>
                        .then(Commands.literal("list")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .executes(PlaytimeAdminCommand::executeList)
                                )
                        )

                        // /playtimeadmin add <player> <time>
                        .then(Commands.literal("add")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .then(Commands.argument("time", StringArgumentType.greedyString())
                                                .executes(PlaytimeAdminCommand::executeAdd)
                                        )
                                )
                        )

                        // /playtimeadmin remove <player> <time>
                        .then(Commands.literal("remove")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .then(Commands.argument("time", StringArgumentType.greedyString())
                                                .executes(PlaytimeAdminCommand::executeRemove)
                                        )
                                )
                        )

                        // /playtimeadmin set <player> <time>
                        .then(Commands.literal("set")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .then(Commands.argument("time", StringArgumentType.greedyString())
                                                .executes(PlaytimeAdminCommand::executeSet)
                                        )
                                )
                        )

                        // /playtimeadmin reset <player>
                        .then(Commands.literal("reset")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .executes(PlaytimeAdminCommand::executeReset)
                                )
                        )

                        // /playtimeadmin rank ...
                        .then(Commands.literal("rank")
                                // /playtimeadmin rank set <player> <rankId>
                                .then(Commands.literal("set")
                                        .then(Commands.argument("player", StringArgumentType.word())
                                                .then(Commands.argument("rankId", StringArgumentType.word())
                                                        .suggests(RANK_ID_SUGGESTIONS)
                                                        .executes(PlaytimeAdminCommand::executeRankSet)
                                                )
                                        )
                                )
                                // /playtimeadmin rank sync
                                .then(Commands.literal("sync")
                                        .executes(PlaytimeAdminCommand::executeRankSync)
                                )
                                // /playtimeadmin rank list
                                .then(Commands.literal("list")
                                        .executes(PlaytimeAdminCommand::executeRankList)
                                )
                                // /playtimeadmin rank info <rankId>
                                .then(Commands.literal("info")
                                        .then(Commands.argument("rankId", StringArgumentType.word())
                                                .suggests(RANK_ID_SUGGESTIONS)
                                                .executes(PlaytimeAdminCommand::executeRankInfo)
                                        )
                                )
                                // /playtimeadmin rank add <id> <displayName> <hours> [claims] [forceloads] [inactivityDays] [color]
                                .then(Commands.literal("add")
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .then(Commands.argument("displayName", StringArgumentType.word())
                                                        .then(Commands.argument("hours", IntegerArgumentType.integer(0))
                                                                .executes(PlaytimeAdminCommand::executeRankAdd)
                                                                .then(Commands.argument("claims", IntegerArgumentType.integer(0))
                                                                        .executes(PlaytimeAdminCommand::executeRankAdd)
                                                                        .then(Commands.argument("forceloads", IntegerArgumentType.integer(0))
                                                                                .executes(PlaytimeAdminCommand::executeRankAdd)
                                                                                .then(Commands.argument("inactivityDays", IntegerArgumentType.integer(-1))
                                                                                        .executes(PlaytimeAdminCommand::executeRankAdd)
                                                                                        .then(Commands.argument("color", StringArgumentType.greedyString())
                                                                                                .executes(PlaytimeAdminCommand::executeRankAdd)
                                                                                        )
                                                                                )
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                                // /playtimeadmin rank remove <rankId>
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("rankId", StringArgumentType.word())
                                                .suggests(RANK_ID_SUGGESTIONS)
                                                .executes(PlaytimeAdminCommand::executeRankRemove)
                                        )
                                )
                                // /playtimeadmin rank edit <rankId> <field> <value>
                                .then(Commands.literal("edit")
                                        .then(Commands.argument("rankId", StringArgumentType.word())
                                                .suggests(RANK_ID_SUGGESTIONS)
                                                .then(Commands.argument("field", StringArgumentType.word())
                                                        .suggests(RANK_FIELD_SUGGESTIONS)
                                                        .then(Commands.argument("value", StringArgumentType.greedyString())
                                                                .executes(PlaytimeAdminCommand::executeRankEdit)
                                                        )
                                                )
                                        )
                                )
                                // /playtimeadmin rank sethover <rankId> <text>
                                .then(Commands.literal("sethover")
                                        .then(Commands.argument("rankId", StringArgumentType.word())
                                                .suggests(RANK_ID_SUGGESTIONS)
                                                .then(Commands.argument("text", StringArgumentType.greedyString())
                                                        .executes(PlaytimeAdminCommand::executeRankSetHover)
                                                )
                                        )
                                )
                                // /playtimeadmin rank edithover <rankId> <text>
                                .then(Commands.literal("edithover")
                                        .then(Commands.argument("rankId", StringArgumentType.word())
                                                .suggests(RANK_ID_SUGGESTIONS)
                                                .then(Commands.argument("text", StringArgumentType.greedyString())
                                                        .executes(PlaytimeAdminCommand::executeRankEditHover)
                                                )
                                        )
                                )
                                // /playtimeadmin rank setdesc <rankId> <text>
                                .then(Commands.literal("setdesc")
                                        .then(Commands.argument("rankId", StringArgumentType.word())
                                                .suggests(RANK_ID_SUGGESTIONS)
                                                .then(Commands.argument("text", StringArgumentType.greedyString())
                                                        .executes(PlaytimeAdminCommand::executeRankSetDesc)
                                                )
                                        )
                                )
                                // /playtimeadmin rank inactivity ...
                                .then(Commands.literal("inactivity")
                                        .then(Commands.argument("rankId", StringArgumentType.word())
                                                .suggests(RANK_ID_SUGGESTIONS)
                                                // /playtimeadmin rank inactivity <rankId> add <command> <time>
                                                .then(Commands.literal("add")
                                                        .then(Commands.argument("command", StringArgumentType.string())
                                                                .then(Commands.argument("time", StringArgumentType.greedyString())
                                                                        .executes(PlaytimeAdminCommand::executeInactivityAdd)
                                                                )
                                                        )
                                                )
                                                // /playtimeadmin rank inactivity <rankId> remove <index>
                                                .then(Commands.literal("remove")
                                                        .then(Commands.argument("index", IntegerArgumentType.integer(0))
                                                                .executes(PlaytimeAdminCommand::executeInactivityRemove)
                                                        )
                                                )
                                                // /playtimeadmin rank inactivity <rankId> list
                                                .then(Commands.literal("list")
                                                        .executes(PlaytimeAdminCommand::executeInactivityList)
                                                )
                                        )
                                )
                                // /playtimeadmin rank gradient <rankId> <color1> <color2> [color3...]
                                .then(Commands.literal("gradient")
                                        .then(Commands.argument("rankId", StringArgumentType.word())
                                                .suggests(RANK_ID_SUGGESTIONS)
                                                .then(Commands.argument("colors", StringArgumentType.greedyString())
                                                        .executes(PlaytimeAdminCommand::executeRankGradient)
                                                )
                                        )
                                )
                                // /playtimeadmin rank prebake <rankId> <color1> <color2> [color3...]
                                .then(Commands.literal("prebake")
                                        .then(Commands.argument("rankId", StringArgumentType.word())
                                                .suggests(RANK_ID_SUGGESTIONS)
                                                .then(Commands.argument("colors", StringArgumentType.greedyString())
                                                        .executes(PlaytimeAdminCommand::executeRankPrebake)
                                                )
                                        )
                                )
                                // /playtimeadmin rank setitem <rankId> <item>
                                .then(Commands.literal("setitem")
                                        .then(Commands.argument("rankId", StringArgumentType.word())
                                                .suggests(RANK_ID_SUGGESTIONS)
                                                .then(Commands.argument("item", StringArgumentType.greedyString())
                                                        .executes(PlaytimeAdminCommand::executeRankSetItem)
                                                )
                                        )
                                )
                                // /playtimeadmin rank setphasetext <rankId> <text>
                                .then(Commands.literal("setphasetext")
                                        .then(Commands.argument("rankId", StringArgumentType.word())
                                                .suggests(RANK_ID_SUGGESTIONS)
                                                .then(Commands.argument("text", StringArgumentType.greedyString())
                                                        .executes(PlaytimeAdminCommand::executeRankSetPhaseText)
                                                )
                                        )
                                )
                        )

                        // /playtimeadmin cleanup [dryrun]
                        .then(Commands.literal("cleanup")
                                .executes(ctx -> executeCleanup(ctx, false))
                                .then(Commands.literal("dryrun")
                                        .executes(ctx -> executeCleanup(ctx, true))
                                )
                        )

                        // /playtimeadmin backup now
                        .then(Commands.literal("backup")
                                .then(Commands.literal("now")
                                        .executes(PlaytimeAdminCommand::executeBackup)
                                )
                        )

                        // /playtimeadmin reload
                        .then(Commands.literal("reload")
                                .executes(PlaytimeAdminCommand::executeReload)
                        )

                        // /playtimeadmin import
                        .then(Commands.literal("import")
                                .executes(PlaytimeAdminCommand::executeImport)
                        )

                        // /playtimeadmin setdisplayrank <player> <rank>
                        .then(Commands.literal("setdisplayrank")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .then(Commands.argument("rank", StringArgumentType.greedyString())
                                                .executes(PlaytimeAdminCommand::executeSetDisplayRank)
                                        )
                                )
                        )
        );
    }

    // ── list ────────────────────────────────────────────────────────────────────

    private static int executeList(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        PlayerDataRepository repo = Playtime.getRepository();
        RankEngine engine = Playtime.getRankEngine();
        LuckPermsService lp = Playtime.getLuckPerms();

        if (repo == null || !repo.isLoaded()) return notReady(src);

        String targetInput = StringArgumentType.getString(ctx, "player");
        PlayerRecord record = repo.getPlayerByName(targetInput);
        if (record == null) {
            src.sendFailure(Component.literal("Player '" + targetInput + "' not found in playtime database."));
            return 0;
        }

        RankDefinition rank = engine.getCurrentRank(record.getTotalPlaytimeTicks());

        src.sendSystemMessage(Component.literal("§6━━━━ Playtime for " + record.getLastUsername() + " ━━━━"));
        src.sendSystemMessage(Component.literal("§7Total Playtime: §f" + TimeParser.formatTicks(record.getTotalPlaytimeTicks())));
        src.sendSystemMessage(Component.literal("§7Current Rank: ").append(lp.getStyledRankName(rank)));
        src.sendSystemMessage(Component.literal("§7First Join: §f" + DATE_FORMAT.format(new Date(record.getFirstJoinEpochMs()))));
        src.sendSystemMessage(Component.literal("§7Last Seen: §f" + DATE_FORMAT.format(new Date(record.getLastSeenEpochMs()))));
        src.sendSystemMessage(Component.literal("§7UUID: §f" + record.getUuid()));
        src.sendSystemMessage(Component.literal("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        return 1;
    }

    // ── add ─────────────────────────────────────────────────────────────────────

    private static int executeAdd(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        PlayerDataRepository repo = Playtime.getRepository();
        RankEngine engine = Playtime.getRankEngine();

        if (repo == null || !repo.isLoaded()) return notReady(src);

        String targetInput = StringArgumentType.getString(ctx, "player");
        String timeInput = StringArgumentType.getString(ctx, "time");

        PlayerRecord record = repo.getPlayerByName(targetInput);
        if (record == null) {
            src.sendFailure(Component.literal("Player '" + targetInput + "' not found."));
            return 0;
        }

        long ticks;
        try {
            ticks = TimeParser.parseTicks(timeInput);
        } catch (IllegalArgumentException e) {
            src.sendFailure(Component.literal("Invalid time format: " + e.getMessage()));
            return 0;
        }

        long newTotal = record.addPlaytimeTicks(ticks);
        engine.checkAndApplyProgression(src.getServer(), record.getUuid(), newTotal);
        repo.markDirty();
        repo.save(false);

        src.sendSuccess(() -> Component.literal("§aAdded " + TimeParser.formatTicks(ticks) +
                " to " + record.getLastUsername() + "'s playtime (total: " + TimeParser.formatTicks(newTotal) + ")"), true);
        return 1;
    }

    // ── remove ──────────────────────────────────────────────────────────────────

    private static int executeRemove(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        PlayerDataRepository repo = Playtime.getRepository();
        RankEngine engine = Playtime.getRankEngine();

        if (repo == null || !repo.isLoaded()) return notReady(src);

        String targetInput = StringArgumentType.getString(ctx, "player");
        String timeInput = StringArgumentType.getString(ctx, "time");

        PlayerRecord record = repo.getPlayerByName(targetInput);
        if (record == null) {
            src.sendFailure(Component.literal("Player '" + targetInput + "' not found."));
            return 0;
        }

        long ticks;
        try {
            ticks = TimeParser.parseTicks(timeInput);
        } catch (IllegalArgumentException e) {
            src.sendFailure(Component.literal("Invalid time format: " + e.getMessage()));
            return 0;
        }

        long newTotal = Math.max(0, record.getTotalPlaytimeTicks() - ticks);
        record.setTotalPlaytimeTicks(newTotal);
        engine.checkAndApplyProgression(src.getServer(), record.getUuid(), newTotal);
        repo.markDirty();
        repo.save(false);

        src.sendSuccess(() -> Component.literal("§aRemoved " + TimeParser.formatTicks(ticks) +
                " from " + record.getLastUsername() + "'s playtime (total: " + TimeParser.formatTicks(newTotal) + ")"), true);
        return 1;
    }

    // ── set ─────────────────────────────────────────────────────────────────────

    private static int executeSet(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        PlayerDataRepository repo = Playtime.getRepository();
        RankEngine engine = Playtime.getRankEngine();

        if (repo == null || !repo.isLoaded()) return notReady(src);

        String targetInput = StringArgumentType.getString(ctx, "player");
        String timeInput = StringArgumentType.getString(ctx, "time");

        PlayerRecord record = repo.getPlayerByName(targetInput);
        if (record == null) {
            src.sendFailure(Component.literal("Player '" + targetInput + "' not found."));
            return 0;
        }

        long ticks;
        try {
            ticks = TimeParser.parseTicks(timeInput);
        } catch (IllegalArgumentException e) {
            src.sendFailure(Component.literal("Invalid time format: " + e.getMessage()));
            return 0;
        }

        record.setTotalPlaytimeTicks(ticks);
        engine.checkAndApplyProgression(src.getServer(), record.getUuid(), ticks);
        repo.markDirty();
        repo.save(false);

        src.sendSuccess(() -> Component.literal("§aSet " + record.getLastUsername() + "'s playtime to " +
                TimeParser.formatTicks(ticks)), true);
        return 1;
    }

    // ── reset ───────────────────────────────────────────────────────────────────

    private static int executeReset(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        PlayerDataRepository repo = Playtime.getRepository();
        RankEngine engine = Playtime.getRankEngine();

        if (repo == null || !repo.isLoaded()) return notReady(src);

        String targetInput = StringArgumentType.getString(ctx, "player");
        PlayerRecord record = repo.getPlayerByName(targetInput);
        if (record == null) {
            src.sendFailure(Component.literal("Player '" + targetInput + "' not found."));
            return 0;
        }

        String name = record.getLastUsername();
        engine.forceResync(src.getServer(), record.getUuid());
        repo.removePlayer(record.getUuid());
        repo.save(false);

        src.sendSuccess(() -> Component.literal("§aReset " + name + "'s playtime and rank data."), true);
        return 1;
    }

    // ── rank set ────────────────────────────────────────────────────────────────

    private static int executeRankSet(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        PlayerDataRepository repo = Playtime.getRepository();
        RankConfig rankConfig = Playtime.getRankConfig();
        RankEngine engine = Playtime.getRankEngine();

        if (repo == null || !repo.isLoaded()) return notReady(src);

        String targetInput = StringArgumentType.getString(ctx, "player");
        String rankId = StringArgumentType.getString(ctx, "rankId");

        PlayerRecord record = repo.getPlayerByName(targetInput);
        if (record == null) {
            src.sendFailure(Component.literal("Player '" + targetInput + "' not found."));
            return 0;
        }

        RankDefinition targetRank = rankConfig.getRankById(rankId);
        if (targetRank == null) {
            src.sendFailure(Component.literal("Rank '" + rankId + "' not found in config."));
            return 0;
        }

        record.setTotalPlaytimeTicks(targetRank.getThresholdTicks());
        engine.forceResync(src.getServer(), record.getUuid());
        repo.markDirty();
        repo.save(false);

        src.sendSuccess(() -> Component.literal("§aSet " + record.getLastUsername() +
                "'s rank to " + targetRank.getDisplayName() +
                " (playtime set to " + TimeParser.formatTicks(targetRank.getThresholdTicks()) + ")"), true);
        return 1;
    }

    // ── rank sync ───────────────────────────────────────────────────────────────

    private static int executeRankSync(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        PlayerDataRepository repo = Playtime.getRepository();
        RankEngine engine = Playtime.getRankEngine();

        if (repo == null || !repo.isLoaded()) return notReady(src);

        int count = 0;
        int skipped = 0;
        for (PlayerRecord record : repo.getAllPlayers()) {
            engine.forceResync(src.getServer(), record.getUuid());
            count++;
        }

        final int total = count;
        src.sendSuccess(() -> Component.literal("§aResynced ranks for " + total + " players. " +
                "(Ranks with syncWithLuckPerms=false were skipped for LP sync)"), true);
        return 1;
    }

    // ── rank list (interactive) ─────────────────────────────────────────────────

    private static int executeRankList(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        RankConfig rankConfig = Playtime.getRankConfig();
        LuckPermsService lp = Playtime.getLuckPerms();

        if (rankConfig == null) {
            src.sendFailure(Component.literal("Rank config not available."));
            return 0;
        }

        List<RankDefinition> ranks = rankConfig.getRanks();
        src.sendSystemMessage(Component.literal("§6━━━━━━━━━ All Ranks (" + ranks.size() + ") ━━━━━━━━━"));
        src.sendSystemMessage(Component.literal("§7([D] edit description, [H] edit hover text. Hover for details.)"));

        for (RankDefinition rank : ranks) {
            String visibility = rank.isVisible() ? "§a✓" : "§c✗";
            String inactivity = rank.getInactivityDays() == -1 ? "∞" : rank.getInactivityDays() + "d";
            String syncIcon = rank.isSyncWithLuckPerms() ? "§a⟳" : "§c⟳";

            // Build the main line with [visibility] [sync] [D] [H] buttons
            MutableComponent line = Component.literal("§7#" + rank.getSortOrder() + " ")
                    .append(Component.literal("[" + visibility + "§7] "))
                    .append(Component.literal("[" + syncIcon + "§7] "));

            // [D] button — click to edit description, auto-fills current value
            String currentDesc = rank.getDescription() != null && !rank.getDescription().isEmpty()
                    ? rank.getDescription() : "";
            MutableComponent descButton = Component.literal("§e[D]");
            descButton.withStyle(style -> style
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Component.literal("§eEdit description\n§7Current: §f" +
                                    (currentDesc.isEmpty() ? "(none)" : currentDesc) +
                                    "\n\n§7Click to edit")))
                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                            "/playtimeadmin rank setdesc " + rank.getId() + " " + currentDesc)));
            line.append(descButton).append(Component.literal(" "));

            // [H] button — click to edit hover text, auto-fills current value
            String currentHover = rank.getHoverText() != null && !rank.getHoverText().isEmpty()
                    ? rank.getHoverText() : "";
            MutableComponent hoverButton = Component.literal("§d[H]");
            hoverButton.withStyle(style -> style
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Component.literal("§dEdit hover text\n§7Current: §f" +
                                    (currentHover.isEmpty() ? "(none)" : currentHover.replace("\\n", "\n")) +
                                    "\n\n§7Click to edit")))
                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                            "/playtimeadmin rank sethover " + rank.getId() + " " + currentHover)));
            line.append(hoverButton).append(Component.literal(" "));

            // Rank name and details
            line.append(lp.getStyledRankName(rank))
                    .append(Component.literal("§r §7(id: §f" + rank.getId() + "§7) §f" +
                            rank.getThresholdHours() + "h §7| §f" +
                            rank.getClaims() + "§7c §f" +
                            rank.getForceloads() + "§7fl §7| §f" +
                            inactivity));

            // Build hover text with full rank details
            StringBuilder hoverBuilder = new StringBuilder();
            hoverBuilder.append("§6§l").append(rank.getDisplayName()).append("\n");
            hoverBuilder.append("§7ID: §f").append(rank.getId()).append("\n");
            hoverBuilder.append("§7Threshold: §f").append(rank.getThresholdHours()).append("h\n");
            if (Config.claimsEnabled) {
                hoverBuilder.append("§7Claims: §f").append(rank.getClaims()).append("\n");
            }
            if (Config.forceloadsEnabled) {
                hoverBuilder.append("§7Forceloads: §f").append(rank.getForceloads()).append("\n");
            }
            hoverBuilder.append("§7Inactivity: §f").append(inactivity).append("\n");
            hoverBuilder.append("§7LP Sync: ").append(rank.isSyncWithLuckPerms() ? "§aEnabled" : "§cDisabled").append("\n");
            hoverBuilder.append("§7LP Group: §f").append(rank.getLuckpermsGroup()).append("\n");
            if (rank.getDescription() != null && !rank.getDescription().isEmpty()) {
                hoverBuilder.append("§7Description: §f").append(rank.getDescription()).append("\n");
            }
            if (rank.getHoverText() != null && !rank.getHoverText().isEmpty()) {
                hoverBuilder.append("§7Hover: §f").append(rank.getHoverText()).append("\n");
            }
            // Show inactivity actions
            List<InactivityAction> actions = rank.getInactivityActions();
            if (!actions.isEmpty()) {
                hoverBuilder.append("§7Inactivity Actions:\n");
                for (int i = 0; i < actions.size(); i++) {
                    InactivityAction a = actions.get(i);
                    hoverBuilder.append("  §f#").append(i).append(" §7").append(a.getDelayDays()).append("d → §f").append(a.getCommand()).append("\n");
                }
            }
            hoverBuilder.append("\n§eClick rank info: §f/playtimeadmin rank info " + rank.getId());

            line.withStyle(style -> style
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hoverBuilder.toString())))
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                            "/playtimeadmin rank info " + rank.getId())));

            src.sendSystemMessage(line);
        }

        src.sendSystemMessage(Component.literal("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        return 1;
    }

    // ── rank info ───────────────────────────────────────────────────────────────

    private static int executeRankInfo(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        RankConfig rankConfig = Playtime.getRankConfig();
        LuckPermsService lp = Playtime.getLuckPerms();

        String rankId = StringArgumentType.getString(ctx, "rankId");
        RankDefinition rank = rankConfig.getRankById(rankId);
        if (rank == null) {
            src.sendFailure(Component.literal("Rank '" + rankId + "' not found."));
            return 0;
        }

        src.sendSystemMessage(Component.literal("§6━━━━━━━━━ Rank: ").append(lp.getStyledRankName(rank)).append(Component.literal(" §6━━━━━━━━━")));
        src.sendSystemMessage(Component.literal("§7ID: §f" + rank.getId()));
        src.sendSystemMessage(Component.literal("§7Display Name: §f" + rank.getDisplayName()));
        src.sendSystemMessage(Component.literal("§7Visible: " + (rank.isVisible() ? "§ayes" : "§cno")));
        src.sendSystemMessage(Component.literal("§7Threshold: §f" + rank.getThresholdHours() + "h §7(" + rank.getThresholdTicks() + " ticks)"));
        if (Config.claimsEnabled) {
            src.sendSystemMessage(Component.literal("§7Claims: §f" + rank.getClaims()));
        }
        if (Config.forceloadsEnabled) {
            src.sendSystemMessage(Component.literal("§7Forceloads: §f" + rank.getForceloads()));
        }
        String inactivity = rank.getInactivityDays() == -1 ? "Never (immune)" : rank.getInactivityDays() + " days";
        src.sendSystemMessage(Component.literal("§7Inactivity Limit: §f" + inactivity));
        src.sendSystemMessage(Component.literal("§7LuckPerms Group: §f" + rank.getLuckpermsGroup()));
        src.sendSystemMessage(Component.literal("§7LP Sync: " + (rank.isSyncWithLuckPerms() ? "§aEnabled" : "§cDisabled")));

        // Fallback color with type detection
        String fc = rank.getFallbackColor();
        String colorType;
        if (ColorUtil.isPrebaked(fc)) {
            colorType = "pre-baked gradient";
        } else if (fc != null && fc.startsWith("gradient:")) {
            colorType = "gradient spec";
        } else {
            colorType = "solid";
        }
        src.sendSystemMessage(Component.literal("§7Fallback Color (§f" + colorType + "§7): ").append(ColorUtil.colorPreview(fc)));

        src.sendSystemMessage(Component.literal("§7Sort Order: §f" + rank.getSortOrder()));

        // Description
        String desc = rank.getDescription();
        src.sendSystemMessage(Component.literal("§7Description: §f" + (desc != null && !desc.isEmpty() ? desc : "(none)")));

        // Hover text
        String hover = rank.getHoverText();
        src.sendSystemMessage(Component.literal("§7Hover Text: §f" + (hover != null && !hover.isEmpty() ? hover : "(none)")));

        // Phase text
        String phase = rank.getPhaseText();
        src.sendSystemMessage(Component.literal("§7Phase Text: §f" + (phase != null && !phase.isEmpty() ? phase : "(none)")));

        // Inactivity actions
        List<InactivityAction> actions = rank.getInactivityActions();
        if (actions.isEmpty()) {
            src.sendSystemMessage(Component.literal("§7Inactivity Actions: §f(none — uses legacy inactivityDays)"));
        } else {
            src.sendSystemMessage(Component.literal("§7Inactivity Actions:"));
            for (int i = 0; i < actions.size(); i++) {
                InactivityAction a = actions.get(i);
                MutableComponent actionLine = Component.literal("  §f#" + i + " §7" + a.getDelayDays() + "d → §f" + a.getCommand());
                // Clickable to remove
                final int idx = i;
                actionLine.withStyle(style -> style
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("§cClick to remove this action")))
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                                "/playtimeadmin rank inactivity " + rank.getId() + " remove " + idx)));
                src.sendSystemMessage(actionLine);
            }
        }

        src.sendSystemMessage(Component.literal("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        return 1;
    }

    // ── rank add ────────────────────────────────────────────────────────────────

    private static int executeRankAdd(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        RankConfig rankConfig = Playtime.getRankConfig();

        if (rankConfig == null) {
            src.sendFailure(Component.literal("Rank config not available."));
            return 0;
        }

        String id = StringArgumentType.getString(ctx, "id").toLowerCase();
        String displayName = StringArgumentType.getString(ctx, "displayName");
        int hours = IntegerArgumentType.getInteger(ctx, "hours");

        // Optional args with defaults
        int claims = getIntOrDefault(ctx, "claims", 0);
        int forceloads = getIntOrDefault(ctx, "forceloads", 0);
        int inactivityDays = getIntOrDefault(ctx, "inactivityDays", 7);
        String color = getStringOrDefault(ctx, "color", "§f");

        if (rankConfig.getRankById(id) != null) {
            src.sendFailure(Component.literal("Rank with id '" + id + "' already exists. Use 'rank edit' to modify it."));
            return 0;
        }

        int sortOrder = rankConfig.getNextSortOrder();
        RankDefinition newRank = new RankDefinition(
                id, displayName, true, hours * 72_000L,
                claims, forceloads, inactivityDays,
                displayName, color, sortOrder
        );

        rankConfig.addRank(newRank);

        src.sendSuccess(() -> Component.literal("§aCreated rank '")
                .append(ColorUtil.rankDisplay(color, displayName))
                .append(Component.literal("§a' (id: " + id + ", " + hours + "h, order: " + sortOrder + ")")), true);
        return 1;
    }

    // ── rank remove ─────────────────────────────────────────────────────────────

    private static int executeRankRemove(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        RankConfig rankConfig = Playtime.getRankConfig();

        if (rankConfig == null) {
            src.sendFailure(Component.literal("Rank config not available."));
            return 0;
        }

        String rankId = StringArgumentType.getString(ctx, "rankId");
        RankDefinition removed = rankConfig.removeRank(rankId);

        if (removed == null) {
            src.sendFailure(Component.literal("Rank '" + rankId + "' not found."));
            return 0;
        }

        src.sendSuccess(() -> Component.literal("§aRemoved rank '" + removed.getDisplayName() + "' (id: " + removed.getId() + "). " +
                "Players at this rank will be recalculated on next activity or rank sync."), true);
        return 1;
    }

    // ── rank edit ───────────────────────────────────────────────────────────────

    private static int executeRankEdit(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        RankConfig rankConfig = Playtime.getRankConfig();

        if (rankConfig == null) {
            src.sendFailure(Component.literal("Rank config not available."));
            return 0;
        }

        String rankId = StringArgumentType.getString(ctx, "rankId");
        String field = StringArgumentType.getString(ctx, "field");
        String value = StringArgumentType.getString(ctx, "value");

        RankDefinition rank = rankConfig.getRankById(rankId);
        if (rank == null) {
            src.sendFailure(Component.literal("Rank '" + rankId + "' not found."));
            return 0;
        }

        try {
            switch (field.toLowerCase()) {
                case "displayname" -> rank.setDisplayName(value);
                case "visible" -> rank.setVisible(Boolean.parseBoolean(value));
                case "hours" -> rank.setThresholdTicks(Long.parseLong(value) * 72_000L);
                case "claims" -> rank.setClaims(Integer.parseInt(value));
                case "forceloads" -> rank.setForceloads(Integer.parseInt(value));
                case "inactivitydays" -> rank.setInactivityDays(Integer.parseInt(value));
                case "luckpermsgroup" -> rank.setLuckpermsGroup(value);
                case "fallbackcolor" -> rank.setFallbackColor(value);
                case "sortorder" -> rank.setSortOrder(Integer.parseInt(value));
                case "syncwithluckperms" -> rank.setSyncWithLuckPerms(Boolean.parseBoolean(value));
                case "description" -> rank.setDescription(value.equals("none") || value.equals("clear") ? null : value);
                case "hovertext" -> rank.setHoverText(value.equals("none") || value.equals("clear") ? null : value);
                case "defaultitem" -> rank.setDefaultItem(value.equals("none") || value.equals("clear") ? null : value);
                case "phasetext" -> rank.setPhaseText(value.equals("none") || value.equals("clear") ? null : value);
                default -> {
                    src.sendFailure(Component.literal("Unknown field '" + field + "'. Valid fields: " + String.join(", ", RANK_FIELDS)));
                    return 0;
                }
            }
        } catch (NumberFormatException e) {
            src.sendFailure(Component.literal("Invalid value '" + value + "' for field '" + field + "'. Expected a number."));
            return 0;
        }

        rankConfig.resortAndSave();

        src.sendSuccess(() -> Component.literal("§aUpdated rank '")
                .append(ColorUtil.rankDisplay(rank.getFallbackColor(), rank.getDisplayName()))
                .append(Component.literal("§a': " + field + " = " + value)), true);
        return 1;
    }

    // ── rank sethover ───────────────────────────────────────────────────────────

    private static int executeRankSetHover(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        RankConfig rankConfig = Playtime.getRankConfig();

        if (rankConfig == null) {
            src.sendFailure(Component.literal("Rank config not available."));
            return 0;
        }

        String rankId = StringArgumentType.getString(ctx, "rankId");
        String text = StringArgumentType.getString(ctx, "text");

        RankDefinition rank = rankConfig.getRankById(rankId);
        if (rank == null) {
            src.sendFailure(Component.literal("Rank '" + rankId + "' not found."));
            return 0;
        }

        rank.setHoverText(text.equals("none") || text.equals("clear") ? null : text);
        rankConfig.resortAndSave();

        src.sendSuccess(() -> Component.literal("§aSet hover text for '")
                .append(ColorUtil.rankDisplay(rank.getFallbackColor(), rank.getDisplayName()))
                .append(Component.literal("§a': " + text)), true);
        return 1;
    }

    // ── rank edithover ──────────────────────────────────────────────────────────

    private static int executeRankEditHover(CommandContext<CommandSourceStack> ctx) {
        // Functionally identical to sethover — both replace the hover text.
        // "edithover" is provided as a convenience alias that suggests the current value.
        return executeRankSetHover(ctx);
    }

    // ── rank setdesc ────────────────────────────────────────────────────────────

    private static int executeRankSetDesc(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        RankConfig rankConfig = Playtime.getRankConfig();

        if (rankConfig == null) {
            src.sendFailure(Component.literal("Rank config not available."));
            return 0;
        }

        String rankId = StringArgumentType.getString(ctx, "rankId");
        String text = StringArgumentType.getString(ctx, "text");

        RankDefinition rank = rankConfig.getRankById(rankId);
        if (rank == null) {
            src.sendFailure(Component.literal("Rank '" + rankId + "' not found."));
            return 0;
        }

        rank.setDescription(text.equals("none") || text.equals("clear") ? null : text);
        rankConfig.resortAndSave();

        src.sendSuccess(() -> Component.literal("§aSet description for '")
                .append(ColorUtil.rankDisplay(rank.getFallbackColor(), rank.getDisplayName()))
                .append(Component.literal("§a': " + text)), true);
        return 1;
    }

    // ── rank inactivity add ─────────────────────────────────────────────────────

    private static int executeInactivityAdd(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        RankConfig rankConfig = Playtime.getRankConfig();

        if (rankConfig == null) {
            src.sendFailure(Component.literal("Rank config not available."));
            return 0;
        }

        String rankId = StringArgumentType.getString(ctx, "rankId");
        String command = StringArgumentType.getString(ctx, "command");
        String timeInput = StringArgumentType.getString(ctx, "time");

        RankDefinition rank = rankConfig.getRankById(rankId);
        if (rank == null) {
            src.sendFailure(Component.literal("Rank '" + rankId + "' not found."));
            return 0;
        }

        int days;
        try {
            days = TimeParser.parseDays(timeInput);
            if (days <= 0) {
                // If time was specified but rounds to 0 days, try parsing as plain number of days
                try {
                    days = Integer.parseInt(timeInput.replaceAll("[dD]$", ""));
                } catch (NumberFormatException e2) {
                    src.sendFailure(Component.literal("Time must be at least 1 day. Got: " + timeInput));
                    return 0;
                }
            }
        } catch (IllegalArgumentException e) {
            // Try parsing as plain integer days (e.g. "13" or "13d")
            try {
                days = Integer.parseInt(timeInput.replaceAll("[dD]$", ""));
            } catch (NumberFormatException e2) {
                src.sendFailure(Component.literal("Invalid time format: " + e.getMessage()));
                return 0;
            }
        }

        InactivityAction action = new InactivityAction(command, days);
        rank.getInactivityActions().add(action);
        rankConfig.resortAndSave();

        final int finalDays = days;
        src.sendSuccess(() -> Component.literal("§aAdded inactivity action to '")
                .append(ColorUtil.rankDisplay(rank.getFallbackColor(), rank.getDisplayName()))
                .append(Component.literal("§a': " + command + " after " + finalDays + " days")), true);
        return 1;
    }

    // ── rank inactivity remove ──────────────────────────────────────────────────

    private static int executeInactivityRemove(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        RankConfig rankConfig = Playtime.getRankConfig();

        if (rankConfig == null) {
            src.sendFailure(Component.literal("Rank config not available."));
            return 0;
        }

        String rankId = StringArgumentType.getString(ctx, "rankId");
        int index = IntegerArgumentType.getInteger(ctx, "index");

        RankDefinition rank = rankConfig.getRankById(rankId);
        if (rank == null) {
            src.sendFailure(Component.literal("Rank '" + rankId + "' not found."));
            return 0;
        }

        List<InactivityAction> actions = rank.getInactivityActions();
        if (index < 0 || index >= actions.size()) {
            src.sendFailure(Component.literal("Invalid index " + index + ". Rank '" + rank.getDisplayName() +
                    "' has " + actions.size() + " inactivity action(s) (0-indexed)."));
            return 0;
        }

        InactivityAction removed = actions.remove(index);
        rankConfig.resortAndSave();

        src.sendSuccess(() -> Component.literal("§aRemoved inactivity action #" + index + " from '")
                .append(ColorUtil.rankDisplay(rank.getFallbackColor(), rank.getDisplayName()))
                .append(Component.literal("§a': " + removed.getCommand() + " (" + removed.getDelayDays() + "d)")), true);
        return 1;
    }

    // ── rank inactivity list ────────────────────────────────────────────────────

    private static int executeInactivityList(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        RankConfig rankConfig = Playtime.getRankConfig();
        LuckPermsService lp = Playtime.getLuckPerms();

        if (rankConfig == null) {
            src.sendFailure(Component.literal("Rank config not available."));
            return 0;
        }

        String rankId = StringArgumentType.getString(ctx, "rankId");

        RankDefinition rank = rankConfig.getRankById(rankId);
        if (rank == null) {
            src.sendFailure(Component.literal("Rank '" + rankId + "' not found."));
            return 0;
        }

        List<InactivityAction> actions = rank.getInactivityActions();

        src.sendSystemMessage(Component.literal("§6━━━━━ Inactivity Actions: ")
                .append(lp.getStyledRankName(rank))
                .append(Component.literal(" §6━━━━━")));

        if (actions.isEmpty()) {
            src.sendSystemMessage(Component.literal("§7No inactivity actions configured."));
            String inactivity = rank.getInactivityDays() == -1 ? "Never (immune)" : rank.getInactivityDays() + " days";
            src.sendSystemMessage(Component.literal("§7Legacy inactivityDays: §f" + inactivity));
        } else {
            for (int i = 0; i < actions.size(); i++) {
                InactivityAction a = actions.get(i);
                MutableComponent line = Component.literal("  §f#" + i + " §7" + a.getDelayDays() + "d → §f" + a.getCommand());
                final int idx = i;
                line.withStyle(style -> style
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("§cClick to remove")))
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                                "/playtimeadmin rank inactivity " + rank.getId() + " remove " + idx)));
                src.sendSystemMessage(line);
            }
        }

        // Add button
        MutableComponent addButton = Component.literal("§a[+ Add Action]");
        addButton.withStyle(style -> style
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal("§aClick to add a new inactivity action")))
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                        "/playtimeadmin rank inactivity " + rank.getId() + " add \"command {uuid}\" ")));
        src.sendSystemMessage(addButton);

        src.sendSystemMessage(Component.literal("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        return 1;
    }

    // ── rank gradient ───────────────────────────────────────────────────────────

    private static int executeRankGradient(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        RankConfig rankConfig = Playtime.getRankConfig();

        if (rankConfig == null) {
            src.sendFailure(Component.literal("Rank config not available."));
            return 0;
        }

        String rankId = StringArgumentType.getString(ctx, "rankId");
        String colorsInput = StringArgumentType.getString(ctx, "colors");

        RankDefinition rank = rankConfig.getRankById(rankId);
        if (rank == null) {
            src.sendFailure(Component.literal("Rank '" + rankId + "' not found."));
            return 0;
        }

        // Parse color stops from space or dash-separated hex values
        List<String> hexColors = parseColorInput(colorsInput);
        if (hexColors.size() < 2) {
            src.sendFailure(Component.literal("§cNeed at least 2 hex colors. Usage: /playtimeadmin rank gradient <rank> #RRGGBB #RRGGBB [#RRGGBB...]"));
            return 0;
        }

        try {
            String gradientSpec = ColorUtil.makeGradientSpec(hexColors);
            rank.setFallbackColor(gradientSpec);
            rankConfig.resortAndSave();

            src.sendSuccess(() -> Component.literal("§aSet gradient for '")
                    .append(ColorUtil.rankDisplay(gradientSpec, rank.getDisplayName()))
                    .append(Component.literal("§a' → " + gradientSpec)), true);

            // Show preview
            src.sendSystemMessage(Component.literal("§7Preview: ").append(ColorUtil.rankDisplay(gradientSpec, rank.getDisplayName())));

            return 1;
        } catch (IllegalArgumentException e) {
            src.sendFailure(Component.literal("§cInvalid color: " + e.getMessage()));
            return 0;
        }
    }

    // ── rank prebake ────────────────────────────────────────────────────────────

    private static int executeRankPrebake(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        RankConfig rankConfig = Playtime.getRankConfig();

        if (rankConfig == null) {
            src.sendFailure(Component.literal("Rank config not available."));
            return 0;
        }

        String rankId = StringArgumentType.getString(ctx, "rankId");
        String colorsInput = StringArgumentType.getString(ctx, "colors");

        RankDefinition rank = rankConfig.getRankById(rankId);
        if (rank == null) {
            src.sendFailure(Component.literal("Rank '" + rankId + "' not found."));
            return 0;
        }

        // Parse color stops
        List<String> hexColors = parseColorInput(colorsInput);
        if (hexColors.size() < 2) {
            src.sendFailure(Component.literal("§cNeed at least 2 hex colors. Usage: /playtimeadmin rank prebake <rank> #RRGGBB #RRGGBB [#RRGGBB...]"));
            return 0;
        }

        try {
            String prebaked = ColorUtil.generatePrebaked(hexColors, rank.getDisplayName());
            rank.setFallbackColor(prebaked);
            rankConfig.resortAndSave();

            src.sendSuccess(() -> Component.literal("§aSet pre-baked gradient for '")
                    .append(ColorUtil.parsePrebaked(prebaked))
                    .append(Component.literal("§a'")), true);

            // Show the raw string for reference
            src.sendSystemMessage(Component.literal("§7Raw: §f" + prebaked));
            src.sendSystemMessage(Component.literal("§7Preview: ").append(ColorUtil.parsePrebaked(prebaked)));

            return 1;
        } catch (IllegalArgumentException e) {
            src.sendFailure(Component.literal("§cInvalid color: " + e.getMessage()));
            return 0;
        }
    }

    /**
     * Parse color input from a string. Accepts space-separated or dash-separated hex values.
     * Handles "#RRGGBB", "RRGGBB", "&#RRGGBB" formats.
     */
    private static List<String> parseColorInput(String input) {
        List<String> colors = new ArrayList<>();
        // Split on spaces, dashes (but not dashes inside hex), or commas
        String[] parts = input.split("[\\s,]+");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            // Normalize: strip & or § prefix, ensure # prefix
            String clean = trimmed.replaceAll("^[&§]?#?", "");
            if (clean.length() == 6 && clean.matches("[0-9A-Fa-f]{6}")) {
                colors.add("#" + clean.toUpperCase());
            }
        }
        return colors;
    }

    // ── rank setitem ──────────────────────────────────────────────────────────

    private static int executeRankSetItem(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        RankConfig rankConfig = Playtime.getRankConfig();

        if (rankConfig == null) {
            src.sendFailure(Component.literal("Rank config not available."));
            return 0;
        }

        String rankId = StringArgumentType.getString(ctx, "rankId");
        String itemInput = StringArgumentType.getString(ctx, "item").trim();

        RankDefinition rank = rankConfig.getRankById(rankId);
        if (rank == null) {
            src.sendFailure(Component.literal("Rank '" + rankId + "' not found."));
            return 0;
        }

        if (itemInput.equalsIgnoreCase("none") || itemInput.equalsIgnoreCase("clear")) {
            rank.setDefaultItem(null);
            rankConfig.resortAndSave();
            src.sendSuccess(() -> Component.literal("§aCleared display item for rank '")
                    .append(ColorUtil.rankDisplay(rank.getFallbackColor(), rank.getDisplayName()))
                    .append(Component.literal("§a'.")), true);
            return 1;
        }

        // Validate the item exists
        try {
            ResourceLocation itemRL = new ResourceLocation(itemInput);
            Item item = ForgeRegistries.ITEMS.getValue(itemRL);
            if (item == null || item == Items.AIR) {
                src.sendFailure(Component.literal("§cUnknown item: " + itemInput));
                return 0;
            }
        } catch (Exception e) {
            src.sendFailure(Component.literal("§cInvalid item ID: " + itemInput));
            return 0;
        }

        rank.setDefaultItem(itemInput);
        rankConfig.resortAndSave();

        src.sendSuccess(() -> Component.literal("§aSet display item for rank '")
                .append(ColorUtil.rankDisplay(rank.getFallbackColor(), rank.getDisplayName()))
                .append(Component.literal("§a' to §f" + itemInput)), true);
        return 1;
    }

    // ── rank setphasetext ──────────────────────────────────────────────────────

    private static int executeRankSetPhaseText(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        RankConfig rankConfig = Playtime.getRankConfig();

        if (rankConfig == null) {
            src.sendFailure(Component.literal("Rank config not available."));
            return 0;
        }

        String rankId = StringArgumentType.getString(ctx, "rankId");
        String text = StringArgumentType.getString(ctx, "text");

        RankDefinition rank = rankConfig.getRankById(rankId);
        if (rank == null) {
            src.sendFailure(Component.literal("Rank '" + rankId + "' not found."));
            return 0;
        }

        if (text.equalsIgnoreCase("none") || text.equalsIgnoreCase("clear")) {
            rank.setPhaseText(null);
            rankConfig.resortAndSave();
            src.sendSuccess(() -> Component.literal("§aCleared phase text for rank '")
                    .append(ColorUtil.rankDisplay(rank.getFallbackColor(), rank.getDisplayName()))
                    .append(Component.literal("§a'.")), true);
            return 1;
        }

        rank.setPhaseText(text);
        rankConfig.resortAndSave();

        src.sendSuccess(() -> Component.literal("§aSet phase text for rank '")
                .append(ColorUtil.rankDisplay(rank.getFallbackColor(), rank.getDisplayName()))
                .append(Component.literal("§a': §7§o" + text)), true);
        return 1;
    }

    // ── cleanup ─────────────────────────────────────────────────────────────────

    private static int executeCleanup(CommandContext<CommandSourceStack> ctx, boolean dryRun) {
        CleanupService cleanup = Playtime.getCleanupService();
        if (cleanup == null) {
            ctx.getSource().sendFailure(Component.literal("Cleanup service not available."));
            return 0;
        }
        cleanup.runCleanup(ctx.getSource().getServer(), ctx.getSource(), dryRun);
        return 1;
    }

    // ── backup now ──────────────────────────────────────────────────────────────

    private static int executeBackup(CommandContext<CommandSourceStack> ctx) {
        BackupService backup = Playtime.getBackupService();
        if (backup == null) {
            ctx.getSource().sendFailure(Component.literal("Backup service not available."));
            return 0;
        }
        boolean ok = backup.backupNow();
        if (ok) {
            ctx.getSource().sendSuccess(() -> Component.literal("§aManual backup created successfully."), true);
        } else {
            ctx.getSource().sendFailure(Component.literal("Backup failed — see server log."));
        }
        return ok ? 1 : 0;
    }

    // ── reload ──────────────────────────────────────────────────────────────────

    private static int executeReload(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        RankConfig rankConfig = Playtime.getRankConfig();
        if (rankConfig == null) {
            src.sendFailure(Component.literal("Rank config not available."));
            return 0;
        }
        rankConfig.load();
        src.sendSuccess(() -> Component.literal("§aReloaded rank definitions (" + rankConfig.getRanks().size() + " ranks)."), true);
        return 1;
    }

    // ── setdisplayrank ─────────────────────────────────────────────────────────

    private static int executeSetDisplayRank(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        PlayerDataRepository repo = Playtime.getRepository();
        LuckPermsService lp = Playtime.getLuckPerms();

        if (repo == null || !repo.isLoaded()) return notReady(src);

        String targetInput = StringArgumentType.getString(ctx, "player");
        String rankInput = StringArgumentType.getString(ctx, "rank").trim();

        PlayerRecord record = repo.getPlayerByName(targetInput);
        if (record == null) {
            src.sendFailure(Component.literal("Player '" + targetInput + "' not found in playtime database."));
            return 0;
        }

        // Check if "clear" or "none" was specified
        if (rankInput.equalsIgnoreCase("clear") || rankInput.equalsIgnoreCase("none") || rankInput.isEmpty()) {
            record.setDisplayRank("");
            repo.markDirty();
            repo.save(false);
            if (lp != null && lp.isAvailable()) {
                lp.removeSuffix(record.getUuid(), 50);
            }
            src.sendSuccess(() -> Component.literal("§aCleared display rank for " + record.getLastUsername() + "."), true);
            return 1;
        }

        // Try to match a rank by display name or ID
        RankDefinition matchedRank = Playtime.getRankConfig().getRankById(rankInput);
        if (matchedRank == null) {
            // Try matching by display name (case-insensitive)
            for (RankDefinition rd : Playtime.getRankConfig().getRanks()) {
                if (rd.getDisplayName().equalsIgnoreCase(rankInput)) {
                    matchedRank = rd;
                    break;
                }
            }
        }

        String displayName;
        if (matchedRank != null) {
            displayName = matchedRank.getDisplayName();
        } else {
            // Use the raw input as a custom display rank name
            if (rankInput.length() > 32) {
                src.sendFailure(Component.literal("§cDisplay rank must be 1-32 characters."));
                return 0;
            }
            displayName = rankInput;
        }

        record.setDisplayRank(displayName);
        repo.markDirty();
        repo.save(false);

        // Sync LP suffix with the rank's actual colour
        if (lp != null && lp.isAvailable()) {
            String colorStr = matchedRank != null ? matchedRank.getFallbackColor() : null;
            lp.setSuffix(record.getUuid(), 50,
                    com.enviouse.playtime.util.ColorUtil.buildLPSuffix(colorStr, displayName));
        }

        final String finalDisplayName = displayName;
        src.sendSuccess(() -> Component.literal("§aSet display rank for " + record.getLastUsername() + " to: §n" + finalDisplayName), true);
        return 1;
    }

    // ── import ──────────────────────────────────────────────────────────────────

    private static int executeImport(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();

        if (Playtime.getRepository() == null || !Playtime.getRepository().isLoaded()) return notReady(src);

        try {
            Path worldDir = src.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
            int imported = com.enviouse.playtime.migration.KubeJsImporter.importFromFile(
                    worldDir,
                    (com.enviouse.playtime.data.JsonPlayerDataRepository) Playtime.getRepository(),
                    Playtime.getRankEngine()
            );
            src.sendSuccess(() -> Component.literal("§aImported " + imported + " player records from imports.json."), true);
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("§cImport failed: " + e.getMessage()));
            return 0;
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private static int notReady(CommandSourceStack src) {
        src.sendFailure(Component.literal("Playtime system not ready (data failed to load)."));
        return 0;
    }

    private static int getIntOrDefault(CommandContext<CommandSourceStack> ctx, String name, int defaultValue) {
        try {
            return IntegerArgumentType.getInteger(ctx, name);
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }

    private static String getStringOrDefault(CommandContext<CommandSourceStack> ctx, String name, String defaultValue) {
        try {
            return StringArgumentType.getString(ctx, name);
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }
}
