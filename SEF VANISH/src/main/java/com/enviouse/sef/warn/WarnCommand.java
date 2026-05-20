package com.enviouse.sef.warn;

import com.enviouse.sef.ServerEssentialsForge;
import com.enviouse.sef.TextFormatter;
import com.enviouse.sef.config.ConfigHandler;
import com.enviouse.sef.config.PermissionsHandler;
import com.enviouse.sef.events.CommandRegistrationHandler;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Registers /warn and /warns commands.
 *
 * Usage:
 *   /warn <player> add <reason> [duration]  — add a warning (duration optional, default permanent)
 *   /warn <player> check                    — check all warnings for a player (admin)
 *   /warn <player> remove <id>              — remove a warning by ID
 *   /warns                                  — check your own warnings (player self-check)
 *
 * All messages are config-driven.
 */
public class WarnCommand {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /warn <player> add <reason>
        // /warn <player> add <duration> <reason>
        // /warn <player> check
        // /warn <player> remove <id>
        dispatcher.register(Commands.literal("warn")
            .requires(src -> {
                try {
                    return PermissionsHandler.playerHasPermission(
                        src.getPlayerOrException().getUUID(), PermissionsHandler.warnCommand);
                } catch (Exception e) { return src.hasPermission(2); }
            })
            .then(Commands.argument("player", EntityArgument.player())
                // /warn <player> add <duration> <reason>
                .then(Commands.literal("add")
                    .then(Commands.argument("duration", StringArgumentType.word())
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                String duration = StringArgumentType.getString(ctx, "duration");
                                String reason = StringArgumentType.getString(ctx, "reason");
                                return executeAdd(ctx.getSource(), target, duration, reason);
                            }))
                        // If only one arg after "add", treat it as the reason with permanent duration
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            String reasonOrDuration = StringArgumentType.getString(ctx, "duration");
                            // Try parsing as duration — if it fails, it's the reason
                            return executeAdd(ctx.getSource(), target, "permanent", reasonOrDuration);
                        })))
                // /warn <player> check
                .then(Commands.literal("check")
                    .executes(ctx -> {
                        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                        return executeCheck(ctx.getSource(), target);
                    }))
                // /warn <player> remove <id>
                .then(Commands.literal("remove")
                    .then(Commands.argument("id", IntegerArgumentType.integer(1))
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            int id = IntegerArgumentType.getInteger(ctx, "id");
                            return executeRemove(ctx.getSource(), target, id);
                        })))));

        // /warns — self-check
        dispatcher.register(Commands.literal("warns")
            .requires(src -> {
                try {
                    return PermissionsHandler.playerHasPermission(
                        src.getPlayerOrException().getUUID(), PermissionsHandler.warnsSelfCommand);
                } catch (Exception e) { return false; } // Console can't self-check
            })
            .executes(ctx -> {
                ServerPlayer self;
                try {
                    self = ctx.getSource().getPlayerOrException();
                } catch (Exception e) {
                    ctx.getSource().sendFailure(TextFormatter.stringToFormattedText("&cThis command can only be used by players."));
                    return 0;
                }
                return executeCheck(ctx.getSource(), self);
            }));
    }

    private static int executeAdd(CommandSourceStack source, ServerPlayer target, String durationStr, String reason) {
        WarnManager manager = CommandRegistrationHandler.getWarnManager();
        if (manager == null) {
            source.sendFailure(TextFormatter.stringToFormattedText("&cWarn system is not initialized."));
            return 0;
        }

        String adminName;
        String adminUuid;
        try {
            ServerPlayer admin = source.getPlayerOrException();
            adminName = admin.getGameProfile().getName();
            adminUuid = admin.getUUID().toString();
        } catch (Exception e) {
            adminName = "Console";
            adminUuid = "00000000-0000-0000-0000-000000000000";
        }

        long durationMs = WarnManager.parseDuration(durationStr);
        WarnManager.WarnEntry entry = manager.addWarn(target.getUUID(), reason, adminName, adminUuid, durationMs);

        // Notify admin
        String adminMsg = ConfigHandler.config.warnAddedMsg.get()
                .replace("$player", target.getGameProfile().getName())
                .replace("$reason", reason)
                .replace("$admin", adminName)
                .replace("$id", String.valueOf(entry.id))
                .replace("$duration", entry.getDurationString());
        source.sendSuccess(() -> TextFormatter.stringToFormattedText(adminMsg), true);

        // Notify the player
        String playerMsg = ConfigHandler.config.warnNotifyPlayerMsg.get()
                .replace("$admin", adminName)
                .replace("$reason", reason);
        target.sendSystemMessage(TextFormatter.stringToFormattedText(playerMsg));

        // Play sound if configured
        if (ConfigHandler.config.warnPlaySound.get()) {
            target.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.get(), SoundSource.MASTER, 1.0f, 1.0f);
        }

        ServerEssentialsForge.LOGGER.info("[WARN] {} warned {} (#{}) - Reason: {} - Duration: {}",
                adminName, target.getGameProfile().getName(), entry.id, reason, entry.getDurationString());
        return 1;
    }

    private static int executeCheck(CommandSourceStack source, ServerPlayer target) {
        WarnManager manager = CommandRegistrationHandler.getWarnManager();
        if (manager == null) {
            source.sendFailure(TextFormatter.stringToFormattedText("&cWarn system is not initialized."));
            return 0;
        }

        List<WarnManager.WarnEntry> warns = manager.getWarns(target.getUUID());
        if (warns.isEmpty()) {
            String msg = ConfigHandler.config.warnNoWarnsMsg.get()
                    .replace("$player", target.getGameProfile().getName());
            source.sendSuccess(() -> TextFormatter.stringToFormattedText(msg), false);
            return 1;
        }

        // Show header
        String header = ConfigHandler.config.warnListHeaderFormat.get()
                .replace("$player", target.getGameProfile().getName());
        source.sendSuccess(() -> TextFormatter.stringToFormattedText(header), false);

        // Show each warning
        for (WarnManager.WarnEntry entry : warns) {
            String dateStr;
            try {
                dateStr = DATE_FMT.format(Instant.parse(entry.timestamp));
            } catch (Exception e) {
                dateStr = entry.timestamp;
            }
            String expiredTag = entry.isExpired() ? ConfigHandler.config.warnExpiredTag.get() : "";
            String line = ConfigHandler.config.warnEntryFormat.get()
                    .replace("$id", String.valueOf(entry.id))
                    .replace("$reason", entry.reason)
                    .replace("$admin", entry.adminName)
                    .replace("$date", dateStr)
                    .replace("$expired", expiredTag);
            source.sendSuccess(() -> TextFormatter.stringToFormattedText(line), false);
        }

        return 1;
    }

    private static int executeRemove(CommandSourceStack source, ServerPlayer target, int warnId) {
        WarnManager manager = CommandRegistrationHandler.getWarnManager();
        if (manager == null) {
            source.sendFailure(TextFormatter.stringToFormattedText("&cWarn system is not initialized."));
            return 0;
        }

        boolean removed = manager.removeWarn(target.getUUID(), warnId);
        if (!removed) {
            source.sendFailure(TextFormatter.stringToFormattedText(
                "&cWarning #" + warnId + " not found for " + target.getGameProfile().getName() + "."));
            return 0;
        }

        String msg = ConfigHandler.config.warnRemovedMsg.get()
                .replace("$player", target.getGameProfile().getName())
                .replace("$id", String.valueOf(warnId));
        source.sendSuccess(() -> TextFormatter.stringToFormattedText(msg), true);

        ServerEssentialsForge.LOGGER.info("[WARN] Warning #{} removed for {}", warnId, target.getGameProfile().getName());
        return 1;
    }
}

