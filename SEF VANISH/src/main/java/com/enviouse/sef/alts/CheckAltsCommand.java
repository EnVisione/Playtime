package com.enviouse.sef.alts;

import com.enviouse.sef.TextFormatter;
import com.enviouse.sef.config.ConfigHandler;
import com.enviouse.sef.config.PermissionsHandler;
import com.enviouse.sef.events.CommandRegistrationHandler;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Registers /checkalts command.
 *
 * Usage:
 *   /checkalts <player>  — shows all accounts that share the same IP
 *
 * All messages are config-driven.
 */
public class CheckAltsCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("checkalts")
            .requires(src -> {
                try {
                    return PermissionsHandler.playerHasPermission(
                        src.getPlayerOrException().getUUID(), PermissionsHandler.checkAltsCommand);
                } catch (Exception e) { return src.hasPermission(2); }
            })
            .then(Commands.argument("player", EntityArgument.player())
                .executes(ctx -> {
                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                    return executeCheckAlts(ctx.getSource(), target);
                })));
    }

    private static int executeCheckAlts(CommandSourceStack source, ServerPlayer target) {
        AltTracker tracker = CommandRegistrationHandler.getAltTracker();
        if (tracker == null) {
            source.sendFailure(TextFormatter.stringToFormattedText("&cAlt tracking is not initialized."));
            return 0;
        }

        List<AltTracker.AltEntry> alts = tracker.getAltsForPlayer(target.getUUID());
        String ip = tracker.getIpForPlayer(target.getUUID());

        if (alts.isEmpty() || alts.size() <= 1) {
            String msg = ConfigHandler.config.checkAltsNoAltsMsg.get()
                    .replace("$player", target.getGameProfile().getName());
            source.sendSuccess(() -> TextFormatter.stringToFormattedText(msg), false);
            return 1;
        }

        // Show header
        String maskedIp = ip != null ? ip : "unknown";
        String header = ConfigHandler.config.checkAltsHeaderFormat.get()
                .replace("$player", target.getGameProfile().getName())
                .replace("$ip", maskedIp);
        source.sendSuccess(() -> TextFormatter.stringToFormattedText(header), false);

        // Show each alt
        for (AltTracker.AltEntry entry : alts) {
            // Skip the target player themselves
            if (entry.uuid.equals(target.getUUID().toString())) continue;

            String line = ConfigHandler.config.checkAltsEntryFormat.get()
                    .replace("$name", entry.name)
                    .replace("$uuid", entry.uuid)
                    .replace("$lastseen", entry.lastSeen != null ? entry.lastSeen : "unknown");
            source.sendSuccess(() -> TextFormatter.stringToFormattedText(line), false);
        }

        return 1;
    }
}

