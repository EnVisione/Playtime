package com.enviouse.sef.chat;

import com.enviouse.sef.ServerEssentialsForge;
import com.enviouse.sef.TextFormatter;
import com.enviouse.sef.config.ConfigHandler;
import com.enviouse.sef.config.PermissionsHandler;
import com.enviouse.sef.vanish.compat.SDLinkHideTracker;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.*;

/**
 * Handles admin-chat toggling and the /ac, /chat admin commands.
 */
public class AdminChatHandler {
    private static final Set<UUID> toggledPlayers = new HashSet<>();

    public static boolean isAdminChatToggled(UUID uuid) {
        return toggledPlayers.contains(uuid);
    }

    /**
     * Clean up admin chat toggle for a player logging out.
     * Removes the toggle so SDLinkHideTracker.clearAll can properly clean up.
     */
    public static void handleLogout(UUID uuid) {
        toggledPlayers.remove(uuid);
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        if (!ConfigHandler.config.enableAdminChat.get()) return;

        // /ac <message>
        dispatcher.register(Commands.literal("ac")
            .requires(src -> {
                try {
                    return PermissionsHandler.playerHasPermission(
                        src.getPlayerOrException().getUUID(), PermissionsHandler.adminChatUse);
                } catch (Exception e) { return true; } // Console always allowed
            })
            .then(Commands.argument("message", StringArgumentType.greedyString())
                .executes(ctx -> {
                    String msg = StringArgumentType.getString(ctx, "message");
                    return sendAdminMessage(ctx.getSource(), msg);
                })));

        // /chat admin (toggle) — requires player, console gets an error message
        dispatcher.register(Commands.literal("chat")
            .then(Commands.literal("admin")
                .requires(src -> {
                    try {
                        return PermissionsHandler.playerHasPermission(
                            src.getPlayerOrException().getUUID(), PermissionsHandler.adminChatUse);
                    } catch (Exception e) { return true; } // Console always allowed
                })
                .executes(ctx -> {
                    ServerPlayer player;
                    try {
                        player = ctx.getSource().getPlayerOrException();
                    } catch (Exception e) {
                        ctx.getSource().sendFailure(TextFormatter.stringToFormattedText("&cAdmin chat toggle can only be used by players. Use /ac <message> instead."));
                        return 0;
                    }
                    UUID uuid = player.getUUID();
                    if (toggledPlayers.contains(uuid)) {
                        toggledPlayers.remove(uuid);
                        // Unhide from SDLink (remove admin chat hide reason)
                        SDLinkHideTracker.removeReason(player, SDLinkHideTracker.HideReason.ADMIN_CHAT);
                        player.sendSystemMessage(TextFormatter.stringToFormattedText(
                            ConfigHandler.config.adminChatDisabledMsg.get()));
                    } else {
                        toggledPlayers.add(uuid);
                        // Hide from SDLink (add admin chat hide reason) so messages don't leak to Discord
                        SDLinkHideTracker.addReason(player, SDLinkHideTracker.HideReason.ADMIN_CHAT);
                        player.sendSystemMessage(TextFormatter.stringToFormattedText(
                            ConfigHandler.config.adminChatEnabledMsg.get()));
                    }
                    return 1;
                })));

        // /helpop <message> — no .requires() so it always shows in tab complete
        // Permission check is inside the executor
        if (ConfigHandler.config.enableHelpOp.get()) {
            dispatcher.register(Commands.literal("helpop")
                .then(Commands.argument("message", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        // Check permission in executor body — deny with message
                        try {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            if (!PermissionsHandler.playerHasPermission(player.getUUID(), PermissionsHandler.helpOpSend)) {
                                ctx.getSource().sendFailure(TextFormatter.stringToFormattedText(ConfigHandler.config.noPermissionMsg.get()));
                                return 0;
                            }
                        } catch (Exception e) {
                            // Console — always allowed
                        }
                        String msg = StringArgumentType.getString(ctx, "message");
                        return sendHelpOp(ctx.getSource(), msg);
                    })));

            // /helpopop <player> <message> (reply)
            dispatcher.register(Commands.literal("helpopop")
                .requires(src -> {
                    try {
                        return PermissionsHandler.playerHasPermission(
                            src.getPlayerOrException().getUUID(), PermissionsHandler.helpOpReply);
                    } catch (Exception e) { return true; } // Console always allowed
                })
                .then(Commands.argument("player", StringArgumentType.word())
                    .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String targetName = StringArgumentType.getString(ctx, "player");
                            String msg = StringArgumentType.getString(ctx, "message");
                            return sendHelpOpReply(ctx.getSource(), targetName, msg);
                        }))));
        }
    }

    private static int sendAdminMessage(CommandSourceStack source, String message) {
        String senderName;
        try {
            senderName = source.getPlayerOrException().getGameProfile().getName();
        } catch (Exception e) {
            senderName = "Console";
        }

        String format = ConfigHandler.config.adminChatFormat.get()
            .replace("$sender", senderName)
            .replace("$message", message);
        MutableComponent component = TextFormatter.stringToFormattedText(format);

        // Send to all players with adminChatSee permission
        int count = 0;
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            if (PermissionsHandler.playerHasPermission(player.getUUID(), PermissionsHandler.adminChatSee)) {
                player.sendSystemMessage(component);
                if (ConfigHandler.config.enableAdminChatSound.get()) {
                    player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.MASTER, 1.0f, 1.0f);
                }
                count++;
            }
        }
        ServerEssentialsForge.LOGGER.info("[ADMIN CHAT] {}: {}", senderName, message);
        return count > 0 ? 1 : 0;
    }

    private static int sendHelpOp(CommandSourceStack source, String message) {
        String senderName;
        try {
            senderName = source.getPlayerOrException().getGameProfile().getName();
        } catch (Exception e) {
            senderName = "Console";
        }

        String format = ConfigHandler.config.helpOpRequestFormat.get()
            .replace("$sender", senderName)
            .replace("$message", message);
        MutableComponent component = TextFormatter.stringToFormattedText(format);

        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            if (PermissionsHandler.playerHasPermission(player.getUUID(), PermissionsHandler.helpOpReceive)) {
                player.sendSystemMessage(component);
                if (ConfigHandler.config.enableHelpOpSound.get()) {
                    player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.MASTER, 1.0f, 1.0f);
                }
            }
        }

        String sentMsg = ConfigHandler.config.helpOpSentMsg.get();
        source.sendSuccess(() -> TextFormatter.stringToFormattedText(sentMsg), false);
        ServerEssentialsForge.LOGGER.info("[HELPOP] {}: {}", senderName, message);
        return 1;
    }

    private static int sendHelpOpReply(CommandSourceStack source, String targetName, String message) {
        ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            source.sendFailure(TextFormatter.stringToFormattedText(ConfigHandler.config.playerOfflineMsg.get()));
            return 0;
        }

        String format = ConfigHandler.config.helpOpReplyFormat.get().replace("$message", message);
        target.sendSystemMessage(TextFormatter.stringToFormattedText(format));
        if (ConfigHandler.config.enableHelpOpSound.get()) {
            target.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.MASTER, 1.0f, 1.0f);
        }

        String sentMsg = ConfigHandler.config.helpOpReplySentMsg.get().replace("$player", targetName);
        source.sendSuccess(() -> TextFormatter.stringToFormattedText(sentMsg), false);
        return 1;
    }
}

