package com.enviouse.sef.invlock;

import com.enviouse.sef.TextFormatter;
import com.enviouse.sef.config.ConfigHandler;
import com.enviouse.sef.config.PermissionsHandler;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

/**
 * Registers /invlock command.
 *
 * Usage:
 *   /invlock <player>  — toggles inventory lock for the target player
 *
 * All messages are config-driven.
 */
public class InvLockCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("invlock")
            .requires(src -> {
                try {
                    return PermissionsHandler.playerHasPermission(
                        src.getPlayerOrException().getUUID(), PermissionsHandler.invLockCommand);
                } catch (Exception e) { return src.hasPermission(2); }
            })
            .then(Commands.argument("player", EntityArgument.player())
                .executes(ctx -> {
                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                    return executeInvLock(ctx.getSource(), target);
                })));
    }

    private static int executeInvLock(CommandSourceStack source, ServerPlayer target) {
        String adminName;
        try {
            adminName = source.getPlayerOrException().getGameProfile().getName();
        } catch (Exception e) {
            adminName = "Console";
        }

        boolean nowLocked = InvLockManager.toggle(target.getUUID());

        if (nowLocked) {
            // Close any open container
            target.closeContainer();

            String playerMsg = ConfigHandler.config.invLockLockedMsg.get()
                    .replace("$admin", adminName);
            target.sendSystemMessage(TextFormatter.stringToFormattedText(playerMsg));

            String adminMsg = ConfigHandler.config.invLockAdminLockMsg.get()
                    .replace("$player", target.getGameProfile().getName());
            source.sendSuccess(() -> TextFormatter.stringToFormattedText(adminMsg), true);
        } else {
            String playerMsg = ConfigHandler.config.invLockUnlockedMsg.get()
                    .replace("$admin", adminName);
            target.sendSystemMessage(TextFormatter.stringToFormattedText(playerMsg));

            String adminMsg = ConfigHandler.config.invLockAdminUnlockMsg.get()
                    .replace("$player", target.getGameProfile().getName());
            source.sendSuccess(() -> TextFormatter.stringToFormattedText(adminMsg), true);
        }

        return 1;
    }
}

