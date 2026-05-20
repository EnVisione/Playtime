package com.enviouse.sef.commands;

import com.enviouse.sef.ServerEssentialsForge;
import com.enviouse.sef.TextFormatter;
import com.enviouse.sef.config.ConfigHandler;
import com.enviouse.sef.config.PermissionsHandler;
import com.enviouse.sef.vanish.compat.SDLinkHideTracker;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Private messaging commands: /msg, /tell, /w, /r, and private-chat toggle.
 * These commands do NOT require permissions to USE — they are available to everyone.
 * Permissions can DENY access (set sef.commands.msg to false to block a player).
 */
public class MsgCommands {
    /** Tracks the last person each player messaged (for /r). */
    private static final Map<UUID, UUID> lastMessaged = new HashMap<>();
    /** Players with private-chat toggled on and their chat partner. */
    private static final Map<UUID, UUID> privateChatToggled = new HashMap<>();

    public static boolean isPrivateChatToggled(UUID uuid) {
        return privateChatToggled.containsKey(uuid);
    }

    public static UUID getPrivateChatPartner(UUID uuid) {
        return privateChatToggled.get(uuid);
    }

    public static void handleLogout(UUID uuid) {
        lastMessaged.remove(uuid);
        privateChatToggled.remove(uuid);
        // Remove anyone who had this player as a partner
        privateChatToggled.values().removeIf(partner -> partner.equals(uuid));
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // Remove vanilla /msg, /tell, /w command nodes to prevent conflicts
        removeCommandNode(dispatcher, "msg");
        removeCommandNode(dispatcher, "tell");
        removeCommandNode(dispatcher, "w");

        // /msg <player> <message> — no .requires() so it always shows in tab complete
        dispatcher.register(Commands.literal("msg")
            .then(Commands.argument("target", EntityArgument.player())
                .then(Commands.argument("message", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        if (!checkPermission(ctx.getSource())) return 0;
                        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                        String message = StringArgumentType.getString(ctx, "message");
                        return sendPrivateMessage(ctx.getSource(), target, message);
                    }))));

        // /tell (alias for /msg)
        dispatcher.register(Commands.literal("tell")
            .then(Commands.argument("target", EntityArgument.player())
                .then(Commands.argument("message", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        if (!checkPermission(ctx.getSource())) return 0;
                        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                        String message = StringArgumentType.getString(ctx, "message");
                        return sendPrivateMessage(ctx.getSource(), target, message);
                    }))));

        // /w (alias for /msg)
        dispatcher.register(Commands.literal("w")
            .then(Commands.argument("target", EntityArgument.player())
                .then(Commands.argument("message", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        if (!checkPermission(ctx.getSource())) return 0;
                        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                        String message = StringArgumentType.getString(ctx, "message");
                        return sendPrivateMessage(ctx.getSource(), target, message);
                    }))));

        // /r <message> (reply to last person) — no .requires()
        dispatcher.register(Commands.literal("r")
            .then(Commands.argument("message", StringArgumentType.greedyString())
                .executes(ctx -> {
                    if (!checkPermission(ctx.getSource())) return 0;
                    // Handle console — console can't use /r
                    ServerPlayer sender;
                    try {
                        sender = ctx.getSource().getPlayerOrException();
                    } catch (Exception e) {
                        ctx.getSource().sendFailure(TextFormatter.stringToFormattedText("&cThis command can only be used by players."));
                        return 0;
                    }
                    UUID lastTarget = lastMessaged.get(sender.getUUID());
                    if (lastTarget == null) {
                        ctx.getSource().sendFailure(TextFormatter.stringToFormattedText(
                            ConfigHandler.config.noReplyTargetMsg.get()));
                        return 0;
                    }
                    ServerPlayer target = ctx.getSource().getServer().getPlayerList().getPlayer(lastTarget);
                    if (target == null) {
                        ctx.getSource().sendFailure(TextFormatter.stringToFormattedText(
                            ConfigHandler.config.playerOfflineMsg.get()));
                        return 0;
                    }
                    String message = StringArgumentType.getString(ctx, "message");
                    return sendPrivateMessage(ctx.getSource(), target, message);
                })));

        // /pchat <player> (toggle private chat with someone) — no .requires()
        dispatcher.register(Commands.literal("pchat")
            .executes(ctx -> {
                if (!checkPermission(ctx.getSource())) return 0;
                ServerPlayer sender;
                try {
                    sender = ctx.getSource().getPlayerOrException();
                } catch (Exception e) {
                    ctx.getSource().sendFailure(TextFormatter.stringToFormattedText("&cThis command can only be used by players."));
                    return 0;
                }
                if (privateChatToggled.containsKey(sender.getUUID())) {
                    privateChatToggled.remove(sender.getUUID());
                    // Remove private msg hide reason from SDLink tracker
                    SDLinkHideTracker.removeReason(sender, SDLinkHideTracker.HideReason.PRIVATE_MSG);
                    sender.sendSystemMessage(TextFormatter.stringToFormattedText("&7Private chat mode disabled."));
                    return 1;
                }
                sender.sendSystemMessage(TextFormatter.stringToFormattedText("&7Usage: /pchat <player> to toggle private chat"));
                return 0;
            })
            .then(Commands.argument("target", EntityArgument.player())
                .executes(ctx -> {
                    if (!checkPermission(ctx.getSource())) return 0;
                    ServerPlayer sender;
                    try {
                        sender = ctx.getSource().getPlayerOrException();
                    } catch (Exception e) {
                        ctx.getSource().sendFailure(TextFormatter.stringToFormattedText("&cThis command can only be used by players."));
                        return 0;
                    }
                    ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                    UUID senderUuid = sender.getUUID();
                    if (privateChatToggled.containsKey(senderUuid)) {
                        privateChatToggled.remove(senderUuid);
                        // Remove private msg hide reason from SDLink tracker
                        SDLinkHideTracker.removeReason(sender, SDLinkHideTracker.HideReason.PRIVATE_MSG);
                        sender.sendSystemMessage(TextFormatter.stringToFormattedText("&7Private chat mode disabled."));
                    } else {
                        privateChatToggled.put(senderUuid, target.getUUID());
                        // Add private msg hide reason to SDLink tracker to prevent Discord leaks
                        SDLinkHideTracker.addReason(sender, SDLinkHideTracker.HideReason.PRIVATE_MSG);
                        sender.sendSystemMessage(TextFormatter.stringToFormattedText(
                            "&dPrivate chat mode enabled with &e" + target.getGameProfile().getName() +
                            "&d. All messages will be sent privately. Type &7/pchat &dto toggle off."));
                    }
                    return 1;
                })));
    }

    /**
     * Checks if the source has permission to use messaging commands.
     * Console always has permission. Players are checked against the msgCommand permission node.
     * Returns true if allowed, false if denied (sends deny message).
     */
    private static boolean checkPermission(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            if (!PermissionsHandler.playerHasPermission(player.getUUID(), PermissionsHandler.msgCommand)) {
                source.sendFailure(TextFormatter.stringToFormattedText(ConfigHandler.config.noPermissionMsg.get()));
                return false;
            }
        } catch (Exception e) {
            // Console — always allowed
        }
        return true;
    }

    private static int sendPrivateMessage(CommandSourceStack source, ServerPlayer target, String message) {
        ServerPlayer sender;
        String tmpSenderName;
        try {
            sender = source.getPlayerOrException();
            tmpSenderName = sender.getGameProfile().getName();
        } catch (Exception e) {
            tmpSenderName = "Console";
            sender = null;
        }
        final String senderName = tmpSenderName;

        String receiverName = target.getGameProfile().getName();

        // Format sent message
        String sentFormat = ConfigHandler.config.msgSentFormat.get()
            .replace("$sender", senderName)
            .replace("$receiver", receiverName)
            .replace("$message", message);

        // Format received message
        String receivedFormat = ConfigHandler.config.msgReceivedFormat.get()
            .replace("$sender", senderName)
            .replace("$receiver", receiverName)
            .replace("$message", message);

        // Build hover text for click-to-message
        String hoverText = ConfigHandler.config.clickToMessageHover.get()
            .replace("$player", senderName);

        MutableComponent receivedComponent = TextFormatter.stringToFormattedText(receivedFormat);
        receivedComponent = receivedComponent.withStyle(style -> style
            .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/msg " + senderName + " "))
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                TextFormatter.stringToFormattedText(hoverText))));

        // Send to target
        target.sendSystemMessage(receivedComponent);
        if (ConfigHandler.config.enableMsgSound.get()) {
            target.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.MASTER, 1.0f, 1.0f);
        }

        // Send to sender
        source.sendSuccess(() -> TextFormatter.stringToFormattedText(sentFormat), false);

        // Track for /r
        if (sender != null) {
            lastMessaged.put(sender.getUUID(), target.getUUID());
            lastMessaged.put(target.getUUID(), sender.getUUID());
        }

        ServerEssentialsForge.LOGGER.info("[MSG] {} -> {}: {}", senderName, receiverName, message);
        return 1;
    }

    /**
     * Removes a command node from the dispatcher's root using reflection.
     * Needed to fully replace vanilla /msg, /tell, /w registrations.
     */
    @SuppressWarnings("unchecked")
    private static void removeCommandNode(CommandDispatcher<CommandSourceStack> dispatcher, String name) {
        try {
            RootCommandNode<CommandSourceStack> root = dispatcher.getRoot();
            Field childrenField = CommandNode.class.getDeclaredField("children");
            childrenField.setAccessible(true);
            Map<String, CommandNode<CommandSourceStack>> children =
                (Map<String, CommandNode<CommandSourceStack>>) childrenField.get(root);
            children.remove(name);

            Field literalsField = CommandNode.class.getDeclaredField("literals");
            literalsField.setAccessible(true);
            Map<String, ?> literals = (Map<String, ?>) literalsField.get(root);
            literals.remove(name);
        } catch (Exception e) {
            ServerEssentialsForge.LOGGER.warn("[SEF] Could not remove existing /{} command node: {}", name, e.getMessage());
        }
    }
}


