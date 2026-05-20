package com.enviouse.sef.clearchat;

import com.enviouse.sef.TextFormatter;
import com.enviouse.sef.config.ConfigHandler;
import com.enviouse.sef.config.PermissionsHandler;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Registers /cc and /clearchat commands.
 *
 * Usage:
 *   /cc              — clears chat for all non-OP players
 *   /cc <player>     — clears chat for a specific player
 *   /clearchat       — alias for /cc
 *   /clearchat <player>
 *
 * All messages are config-driven.
 */
public class ClearChatCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /cc [player]
        dispatcher.register(Commands.literal("cc")
            .requires(src -> {
                try {
                    return PermissionsHandler.playerHasPermission(
                        src.getPlayerOrException().getUUID(), PermissionsHandler.clearChatCommand);
                } catch (Exception e) { return src.hasPermission(2); }
            })
            .executes(ctx -> executeClearAll(ctx.getSource()))
            .then(Commands.argument("player", EntityArgument.player())
                .executes(ctx -> executeClearPlayer(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))));

        // /clearchat alias
        dispatcher.register(Commands.literal("clearchat")
            .requires(src -> {
                try {
                    return PermissionsHandler.playerHasPermission(
                        src.getPlayerOrException().getUUID(), PermissionsHandler.clearChatCommand);
                } catch (Exception e) { return src.hasPermission(2); }
            })
            .executes(ctx -> executeClearAll(ctx.getSource()))
            .then(Commands.argument("player", EntityArgument.player())
                .executes(ctx -> executeClearPlayer(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))));
    }

    private static int executeClearAll(CommandSourceStack source) {
        int lineCount = ConfigHandler.config.clearChatLineCount.get();
        String adminName;
        try {
            adminName = source.getPlayerOrException().getGameProfile().getName();
        } catch (Exception e) {
            adminName = "Console";
        }

        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            // Skip OPs
            if (source.getServer().getPlayerList().isOp(player.getGameProfile())) continue;
            sendBlankLines(player, lineCount);
            player.sendSystemMessage(TextFormatter.stringToFormattedText(
                ConfigHandler.config.clearChatSelfMsg.get()));
        }

        String msg = ConfigHandler.config.clearChatAllSuccessMsg.get()
                .replace("$admin", adminName);
        source.sendSuccess(() -> TextFormatter.stringToFormattedText(msg), true);
        return 1;
    }

    private static int executeClearPlayer(CommandSourceStack source, ServerPlayer target) {
        int lineCount = ConfigHandler.config.clearChatLineCount.get();
        sendBlankLines(target, lineCount);
        target.sendSystemMessage(TextFormatter.stringToFormattedText(
            ConfigHandler.config.clearChatSelfMsg.get()));

        String msg = ConfigHandler.config.clearChatSuccessMsg.get()
                .replace("$player", target.getGameProfile().getName());
        source.sendSuccess(() -> TextFormatter.stringToFormattedText(msg), true);
        return 1;
    }

    private static void sendBlankLines(ServerPlayer player, int count) {
        Component blank = Component.literal("");
        for (int i = 0; i < count; i++) {
            player.sendSystemMessage(blank);
        }
    }
}

