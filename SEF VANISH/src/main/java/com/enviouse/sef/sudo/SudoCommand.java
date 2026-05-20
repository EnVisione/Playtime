package com.enviouse.sef.sudo;

import com.enviouse.sef.ServerEssentialsForge;
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
 * Registers /sudo command.
 *
 * Usage:
 *   /sudo <player> <command>
 *
 * Forces the target player to execute a command.
 * All messages are config-driven.
 */
public class SudoCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sudo")
            .requires(src -> {
                try {
                    return PermissionsHandler.playerHasPermission(
                        src.getPlayerOrException().getUUID(), PermissionsHandler.sudoCommand);
                } catch (Exception e) { return src.hasPermission(2); }
            })
            .then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("command", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                        String command = StringArgumentType.getString(ctx, "command");
                        return executeSudo(ctx.getSource(), target, command);
                    }))));
    }

    private static int executeSudo(CommandSourceStack source, ServerPlayer target, String command) {
        String adminName;
        try {
            adminName = source.getPlayerOrException().getGameProfile().getName();
        } catch (Exception e) {
            adminName = "Console";
        }

        // Strip leading slash if the admin included one
        String cleanCommand = command.startsWith("/") ? command.substring(1) : command;

        // Execute the command as the target player
        CommandSourceStack targetSource = target.createCommandSourceStack();
        source.getServer().getCommands().performPrefixedCommand(targetSource, cleanCommand);

        // Notify the admin
        String adminMsg = ConfigHandler.config.sudoExecutedMsg.get()
                .replace("$player", target.getGameProfile().getName())
                .replace("$command", cleanCommand)
                .replace("$admin", adminName);
        source.sendSuccess(() -> TextFormatter.stringToFormattedText(adminMsg), true);

        // Notify the target player
        String targetMsg = ConfigHandler.config.sudoNotifyMsg.get()
                .replace("$admin", adminName)
                .replace("$command", cleanCommand);
        target.sendSystemMessage(TextFormatter.stringToFormattedText(targetMsg));

        ServerEssentialsForge.LOGGER.info("[SUDO] {} forced {} to execute: /{}", adminName,
                target.getGameProfile().getName(), cleanCommand);
        return 1;
    }
}

