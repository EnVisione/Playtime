package com.enviouse.sef.freeze;

import com.enviouse.sef.TextFormatter;
import com.enviouse.sef.config.ConfigHandler;
import com.enviouse.sef.config.PermissionsHandler;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

/**
 * Registers /freeze and /unfreeze commands.
 *
 * Usage:
 *   /freeze <player> <duration|infinite> <reason>
 *   /unfreeze <player>
 *
 * Duration formats: 30s, 5m, 1h, infinite
 * All messages are config-driven.
 */
public class FreezeCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        if (!ConfigHandler.config.enableFreezeSystem.get()) return;

        // /freeze <player> <duration> <reason>
        dispatcher.register(Commands.literal("freeze")
            .requires(src -> {
                try {
                    return PermissionsHandler.playerHasPermission(
                        src.getPlayerOrException().getUUID(), PermissionsHandler.freezeCommand);
                } catch (Exception e) { return src.hasPermission(2); }
            })
            .then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("duration", StringArgumentType.word())
                    .then(Commands.argument("reason", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            String duration = StringArgumentType.getString(ctx, "duration");
                            String reason = StringArgumentType.getString(ctx, "reason");
                            return executeFreeze(ctx.getSource(), target, duration, reason);
                        })))));

        // /unfreeze <player>
        dispatcher.register(Commands.literal("unfreeze")
            .requires(src -> {
                try {
                    return PermissionsHandler.playerHasPermission(
                        src.getPlayerOrException().getUUID(), PermissionsHandler.unfreezeCommand);
                } catch (Exception e) { return src.hasPermission(2); }
            })
            .then(Commands.argument("player", EntityArgument.player())
                .executes(ctx -> {
                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                    return executeUnfreeze(ctx.getSource(), target);
                })));
    }

    private static int executeFreeze(CommandSourceStack source, ServerPlayer target, String durationStr, String reason) {
        String adminName;
        try {
            adminName = source.getPlayerOrException().getGameProfile().getName();
        } catch (Exception e) {
            adminName = "Console";
        }

        // Check if already frozen
        if (FreezeManager.isFrozen(target.getUUID())) {
            source.sendFailure(TextFormatter.stringToFormattedText(
                "&c" + target.getGameProfile().getName() + " is already frozen."));
            return 0;
        }

        long durationTicks = FreezeManager.parseDuration(durationStr);
        FreezeManager.freezePlayer(target, adminName, reason, durationTicks, source.getServer());

        return 1;
    }

    private static int executeUnfreeze(CommandSourceStack source, ServerPlayer target) {
        String adminName;
        try {
            adminName = source.getPlayerOrException().getGameProfile().getName();
        } catch (Exception e) {
            adminName = "Console";
        }

        if (!FreezeManager.isFrozen(target.getUUID())) {
            source.sendFailure(TextFormatter.stringToFormattedText(
                "&c" + target.getGameProfile().getName() + " is not frozen."));
            return 0;
        }

        FreezeManager.unfreezePlayer(target.getUUID(), adminName, source.getServer());
        return 1;
    }
}

