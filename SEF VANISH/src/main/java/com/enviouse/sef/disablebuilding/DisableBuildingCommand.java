package com.enviouse.sef.disablebuilding;

import com.enviouse.sef.TextFormatter;
import com.enviouse.sef.config.ConfigHandler;
import com.enviouse.sef.config.PermissionsHandler;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

/**
 * Registers /disablebuilding and /db commands.
 *
 * Usage:
 *   /disablebuilding <player>  — toggles building for the target player
 *   /db <player>               — alias
 *
 * All messages are config-driven.
 */
public class DisableBuildingCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /disablebuilding <player>
        dispatcher.register(Commands.literal("disablebuilding")
            .requires(src -> {
                try {
                    return PermissionsHandler.playerHasPermission(
                        src.getPlayerOrException().getUUID(), PermissionsHandler.disableBuildingCommand);
                } catch (Exception e) { return src.hasPermission(2); }
            })
            .then(Commands.argument("player", EntityArgument.player())
                .executes(ctx -> {
                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                    return executeToggle(ctx.getSource(), target);
                })));

        // /db alias
        dispatcher.register(Commands.literal("db")
            .requires(src -> {
                try {
                    return PermissionsHandler.playerHasPermission(
                        src.getPlayerOrException().getUUID(), PermissionsHandler.disableBuildingCommand);
                } catch (Exception e) { return src.hasPermission(2); }
            })
            .then(Commands.argument("player", EntityArgument.player())
                .executes(ctx -> {
                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                    return executeToggle(ctx.getSource(), target);
                })));
    }

    private static int executeToggle(CommandSourceStack source, ServerPlayer target) {
        String adminName;
        try {
            adminName = source.getPlayerOrException().getGameProfile().getName();
        } catch (Exception e) {
            adminName = "Console";
        }

        boolean nowDisabled = DisableBuildingManager.toggle(target.getUUID());

        if (nowDisabled) {
            String adminMsg = ConfigHandler.config.dbEnabledMsg.get()
                    .replace("$player", target.getGameProfile().getName())
                    .replace("$admin", adminName);
            source.sendSuccess(() -> TextFormatter.stringToFormattedText(adminMsg), true);

            String playerMsg = ConfigHandler.config.dbPlayerNotifyMsg.get()
                    .replace("$status", "disabled")
                    .replace("$admin", adminName);
            target.sendSystemMessage(TextFormatter.stringToFormattedText(playerMsg));
        } else {
            String adminMsg = ConfigHandler.config.dbDisabledMsg.get()
                    .replace("$player", target.getGameProfile().getName())
                    .replace("$admin", adminName);
            source.sendSuccess(() -> TextFormatter.stringToFormattedText(adminMsg), true);

            String playerMsg = ConfigHandler.config.dbPlayerNotifyMsg.get()
                    .replace("$status", "enabled")
                    .replace("$admin", adminName);
            target.sendSystemMessage(TextFormatter.stringToFormattedText(playerMsg));
        }

        return 1;
    }
}

