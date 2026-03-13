package com.enviouse.playtime.command;

import com.enviouse.playtime.Config;
import com.enviouse.playtime.Playtime;
import com.enviouse.playtime.config.RankConfig;
import com.enviouse.playtime.data.PlayerDataRepository;
import com.enviouse.playtime.data.PlayerRecord;
import com.enviouse.playtime.data.RankDefinition;
import com.enviouse.playtime.integration.LuckPermsService;
import com.enviouse.playtime.service.BackupService;
import com.enviouse.playtime.service.CleanupService;
import com.enviouse.playtime.service.RankEngine;
import com.enviouse.playtime.service.SessionTracker;
import com.enviouse.playtime.util.TimeParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * /playtimeadmin — admin command tree.
 * Sub-commands: list, add, remove, set, reset, rank set, rank sync, cleanup, backup now, reload, import.
 */
public class PlaytimeAdminCommand {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

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

                        // /playtimeadmin rank set <player> <rankId>
                        .then(Commands.literal("rank")
                                .then(Commands.literal("set")
                                        .then(Commands.argument("player", StringArgumentType.word())
                                                .then(Commands.argument("rankId", StringArgumentType.word())
                                                        .executes(PlaytimeAdminCommand::executeRankSet)
                                                )
                                        )
                                )
                                // /playtimeadmin rank sync
                                .then(Commands.literal("sync")
                                        .executes(PlaytimeAdminCommand::executeRankSync)
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

                        // /playtimeadmin import <filepath>
                        .then(Commands.literal("import")
                                .then(Commands.argument("filepath", StringArgumentType.greedyString())
                                        .executes(PlaytimeAdminCommand::executeImport)
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
        String color = lp.getDisplayColor(rank);

        src.sendSystemMessage(Component.literal("§6━━━━ Playtime for " + record.getLastUsername() + " ━━━━"));
        src.sendSystemMessage(Component.literal("§7Total Playtime: §f" + TimeParser.formatTicks(record.getTotalPlaytimeTicks())));
        src.sendSystemMessage(Component.literal("§7Current Rank: " + color + rank.getDisplayName()));
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
        RankConfig rankConfig = Playtime.getRankConfig();

        if (repo == null || !repo.isLoaded()) return notReady(src);

        String targetInput = StringArgumentType.getString(ctx, "player");
        PlayerRecord record = repo.getPlayerByName(targetInput);
        if (record == null) {
            src.sendFailure(Component.literal("Player '" + targetInput + "' not found."));
            return 0;
        }

        String name = record.getLastUsername();

        // Remove all rank groups via LP, then force-sync to first rank
        engine.forceResync(src.getServer(), record.getUuid());

        // Delete the record entirely
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

        // Set playtime to match rank threshold, then resync
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
        for (PlayerRecord record : repo.getAllPlayers()) {
            engine.forceResync(src.getServer(), record.getUuid());
            count++;
        }

        final int total = count;
        src.sendSuccess(() -> Component.literal("§aResynced ranks for " + total + " players."), true);
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

    // ── import ──────────────────────────────────────────────────────────────────

    private static int executeImport(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        String filepath = StringArgumentType.getString(ctx, "filepath");

        try {
            int imported = com.enviouse.playtime.migration.KubeJsImporter.importFromFile(
                    filepath, Playtime.getRepository(), Playtime.getRankEngine(), src.getServer()
            );
            src.sendSuccess(() -> Component.literal("§aImported " + imported + " player records from KubeJS data."), true);
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
}

