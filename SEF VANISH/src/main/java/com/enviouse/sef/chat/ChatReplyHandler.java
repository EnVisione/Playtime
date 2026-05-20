package com.enviouse.sef.chat;

import com.enviouse.sef.ServerEssentialsForge;
import com.enviouse.sef.TextFormatter;
import com.enviouse.sef.config.ConfigHandler;
import com.enviouse.sef.config.PermissionsHandler;
import com.enviouse.sef.utils.SEFUtilities;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Handles the /ans (reply to a chat message) command.
 */
public class ChatReplyHandler {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /ans <messageId> <reply>
        dispatcher.register(Commands.literal("ans")
            .requires(src -> {
                try {
                    return PermissionsHandler.playerHasPermission(
                        src.getPlayerOrException().getUUID(), PermissionsHandler.ansCommand);
                } catch (Exception e) { return true; }
            })
            .then(Commands.argument("messageId", LongArgumentType.longArg(1))
                .then(Commands.argument("reply", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        long messageId = LongArgumentType.getLong(ctx, "messageId");
                        String reply = StringArgumentType.getString(ctx, "reply");
                        return handleReply(ctx.getSource(), messageId, reply);
                    }))));
    }

    private static int handleReply(CommandSourceStack source, long messageId, String reply) {
        ServerPlayer replier;
        try {
            replier = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(TextFormatter.stringToFormattedText("&cOnly players can reply to messages."));
            return 0;
        }

        ChatMessageManager.ChatRecord original = ChatMessageManager.getMessage(messageId);
        if (original == null) {
            source.sendFailure(TextFormatter.stringToFormattedText(ConfigHandler.config.messageNotFoundMsg.get()));
            return 0;
        }

        ServerPlayer originalSender = source.getServer().getPlayerList().getPlayer(original.senderUuid());
        if (originalSender == null) {
            source.sendFailure(TextFormatter.stringToFormattedText(ConfigHandler.config.playerOfflineMsg.get()));
            return 0;
        }

        // Build summary of original message
        String summary = original.message();
        int maxLen = ConfigHandler.config.replySummaryLength.get();
        if (maxLen > 0 && summary.length() > maxLen) {
            summary = summary.substring(0, maxLen) + "...";
        }

        // Get the replier's formatted name (with rank/prefix/suffix)
        String replierFormattedName = SEFUtilities.getRawPreferredPlayerName(replier.getGameProfile());

        // Build reply header
        String headerFormat = ConfigHandler.config.replyHeaderFormat.get()
            .replace("$replier", replierFormattedName)
            .replace("$original_sender", original.formattedName())
            .replace("$summary", summary);
        MutableComponent header = TextFormatter.stringToFormattedText(headerFormat);

        // Build reply body with the replier's full name
        String bodyFormat = ConfigHandler.config.replyBodyFormat.get()
            .replace("$replier", replierFormattedName)
            .replace("$message", reply);
        MutableComponent replyMsg = TextFormatter.stringToFormattedText(bodyFormat);
        MutableComponent fullReply = header.append("\n").append(replyMsg);

        // Send to original sender
        originalSender.sendSystemMessage(fullReply);
        if (ConfigHandler.config.enableReplySound.get()) {
            originalSender.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.MASTER, 1.0f, 1.0f);
        }

        // Send confirmation to replier
        replier.sendSystemMessage(fullReply);

        ServerEssentialsForge.LOGGER.info("[REPLY] {} replied to {}'s message #{}: {}",
            replier.getGameProfile().getName(), original.rawName(), messageId, reply);
        return 1;
    }
}

