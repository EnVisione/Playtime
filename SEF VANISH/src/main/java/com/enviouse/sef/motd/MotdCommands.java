package com.enviouse.sef.motd;

import com.enviouse.sef.TextFormatter;
import com.enviouse.sef.events.CommandRegistrationHandler;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * Commands: /sef motd set, /sef motd reload, /sef motd show.
 */
public class MotdCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sef")
            .requires(src -> src.hasPermission(2))
            .then(Commands.literal("motd")
                // /sef motd set <line1> [| line2]
                .then(Commands.literal("set")
                    .then(Commands.argument("motd", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            MotdManager mgr = CommandRegistrationHandler.getMotdManager();
                            if (mgr == null) {
                                ctx.getSource().sendFailure(TextFormatter.stringToFormattedText("&cMOTD system not initialized"));
                                return 0;
                            }
                            String motd = StringArgumentType.getString(ctx, "motd");
                            String[] lines = motd.split("\\|", 2);
                            String line1 = lines[0].trim();
                            String line2 = lines.length > 1 ? lines[1].trim() : "";
                            mgr.setMotd(line1, line2);
                            mgr.applyToServer(ctx.getSource().getServer());
                            ctx.getSource().sendSuccess(() -> TextFormatter.stringToFormattedText(
                                "&aMOTD updated and applied"), false);
                            return 1;
                        })))
                // /sef motd reload
                .then(Commands.literal("reload")
                    .executes(ctx -> {
                        MotdManager mgr = CommandRegistrationHandler.getMotdManager();
                        if (mgr == null) {
                            ctx.getSource().sendFailure(TextFormatter.stringToFormattedText("&cMOTD system not initialized"));
                            return 0;
                        }
                        mgr.load();
                        mgr.applyToServer(ctx.getSource().getServer());
                        ctx.getSource().sendSuccess(() -> TextFormatter.stringToFormattedText(
                            "&aMOTD reloaded and applied"), false);
                        return 1;
                    }))
                // /sef motd show
                .then(Commands.literal("show")
                    .executes(ctx -> {
                        MotdManager mgr = CommandRegistrationHandler.getMotdManager();
                        if (mgr == null) {
                            ctx.getSource().sendFailure(TextFormatter.stringToFormattedText("&cMOTD system not initialized"));
                            return 0;
                        }
                        var data = mgr.getData();
                        ctx.getSource().sendSuccess(() -> TextFormatter.stringToFormattedText(
                            "&6Current MOTD:"), false);
                        ctx.getSource().sendSuccess(() -> TextFormatter.stringToFormattedText(
                            "&7Line 1: &f" + data.line1()), false);
                        ctx.getSource().sendSuccess(() -> TextFormatter.stringToFormattedText(
                            "&7Line 2: &f" + data.line2()), false);
                        return 1;
                    }))));
    }
}

