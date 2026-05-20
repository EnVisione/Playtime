package com.enviouse.sef.mute;

import com.enviouse.sef.TextFormatter;
import com.enviouse.sef.config.ConfigHandler;
import com.enviouse.sef.config.PermissionsHandler;
import com.enviouse.sef.events.CommandRegistrationHandler;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

/**
 * Registers {@code /mute}, {@code /unmute}, and {@code /mutelist} commands.
 *
 * <h3>Usage</h3>
 * <pre>
 *   /mute &lt;player&gt; &lt;duration|permanent&gt; &lt;reason&gt;
 *   /unmute &lt;player&gt;
 *   /mutelist
 * </pre>
 *
 * Duration formats: {@code 30s, 5m, 1h, 1d, permanent, infinite}
 * <p>All messages are config-driven.</p>
 */
public class MuteCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        if (!ConfigHandler.config.enableMuteSystem.get()) return;

        // /mute <player> <duration> <reason>
        dispatcher.register(Commands.literal("mute")
            .requires(src -> {
                try {
                    return PermissionsHandler.playerHasPermission(
                        src.getPlayerOrException().getUUID(), PermissionsHandler.muteCommand);
                } catch (Exception e) { return src.hasPermission(2); }
            })
            .then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("duration", StringArgumentType.word())
                    .then(Commands.argument("reason", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            String duration = StringArgumentType.getString(ctx, "duration");
                            String reason = StringArgumentType.getString(ctx, "reason");
                            return executeMute(ctx.getSource(), target, duration, reason);
                        })))));

        // /unmute <player>
        dispatcher.register(Commands.literal("unmute")
            .requires(src -> {
                try {
                    return PermissionsHandler.playerHasPermission(
                        src.getPlayerOrException().getUUID(), PermissionsHandler.unmuteCommand);
                } catch (Exception e) { return src.hasPermission(2); }
            })
            .then(Commands.argument("player", EntityArgument.player())
                .executes(ctx -> {
                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                    return executeUnmute(ctx.getSource(), target);
                })));

        // /mutelist
        dispatcher.register(Commands.literal("mutelist")
            .requires(src -> {
                try {
                    return PermissionsHandler.playerHasPermission(
                        src.getPlayerOrException().getUUID(), PermissionsHandler.muteCommand);
                } catch (Exception e) { return src.hasPermission(2); }
            })
            .executes(ctx -> executeMuteList(ctx.getSource())));
    }

    private static int executeMute(CommandSourceStack source, ServerPlayer target,
                                   String durationStr, String reason) {
        String adminName;
        try {
            adminName = source.getPlayerOrException().getGameProfile().getName();
        } catch (Exception e) {
            adminName = "Console";
        }

        MuteManager muteManager = CommandRegistrationHandler.getMuteManager();
        if (muteManager == null) {
            source.sendFailure(TextFormatter.stringToFormattedText("&cMute system not initialized."));
            return 0;
        }

        // Check if already muted
        if (muteManager.isMuted(target.getUUID())) {
            source.sendFailure(TextFormatter.stringToFormattedText(
                ConfigHandler.config.muteAlreadyMutedMsg.get()
                    .replace("$player", target.getGameProfile().getName())));
            return 0;
        }

        long durationTicks = MuteManager.parseDuration(durationStr);
        muteManager.mutePlayer(target, adminName, reason, durationTicks, source.getServer());

        // Confirmation to the source
        MuteManager.MuteEntry entry = muteManager.getMuteEntry(target.getUUID());
        String confirmMsg = ConfigHandler.config.muteConfirmFormat.get()
                .replace("$player", target.getGameProfile().getName())
                .replace("$admin", adminName)
                .replace("$reason", reason)
                .replace("$duration", entry != null ? entry.getOriginalDurationString() : durationStr);
        source.sendSuccess(() -> TextFormatter.stringToFormattedText(confirmMsg), true);
        return 1;
    }

    private static int executeUnmute(CommandSourceStack source, ServerPlayer target) {
        String adminName;
        try {
            adminName = source.getPlayerOrException().getGameProfile().getName();
        } catch (Exception e) {
            adminName = "Console";
        }

        MuteManager muteManager = CommandRegistrationHandler.getMuteManager();
        if (muteManager == null) {
            source.sendFailure(TextFormatter.stringToFormattedText("&cMute system not initialized."));
            return 0;
        }

        if (!muteManager.isMuted(target.getUUID())) {
            source.sendFailure(TextFormatter.stringToFormattedText(
                ConfigHandler.config.muteNotMutedMsg.get()
                    .replace("$player", target.getGameProfile().getName())));
            return 0;
        }

        muteManager.unmutePlayer(target.getUUID(), adminName, source.getServer());

        String confirmMsg = ConfigHandler.config.unmuteConfirmFormat.get()
                .replace("$player", target.getGameProfile().getName())
                .replace("$admin", adminName);
        source.sendSuccess(() -> TextFormatter.stringToFormattedText(confirmMsg), true);
        return 1;
    }

    private static int executeMuteList(CommandSourceStack source) {
        MuteManager muteManager = CommandRegistrationHandler.getMuteManager();
        if (muteManager == null) {
            source.sendFailure(TextFormatter.stringToFormattedText("&cMute system not initialized."));
            return 0;
        }

        var allMutes = muteManager.getAllMutes();
        if (allMutes.isEmpty()) {
            source.sendSuccess(() -> TextFormatter.stringToFormattedText(
                ConfigHandler.config.muteListEmptyMsg.get()), false);
            return 1;
        }

        source.sendSuccess(() -> TextFormatter.stringToFormattedText(
            ConfigHandler.config.muteListHeaderFormat.get()), false);

        for (MuteManager.MuteEntry entry : allMutes) {
            String line = ConfigHandler.config.muteListEntryFormat.get()
                    .replace("$player", entry.playerName)
                    .replace("$admin", entry.adminName)
                    .replace("$reason", entry.reason)
                    .replace("$remaining", entry.getRemainingString())
                    .replace("$duration", entry.getOriginalDurationString());
            source.sendSuccess(() -> TextFormatter.stringToFormattedText(line), false);
        }

        return 1;
    }
}

