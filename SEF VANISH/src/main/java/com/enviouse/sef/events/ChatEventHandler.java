package com.enviouse.sef.events;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

import com.enviouse.sef.ServerEssentialsForge;
import com.enviouse.sef.MarkdownFormatter;
import com.enviouse.sef.TextFormatter;
import com.enviouse.sef.chat.AdminChatHandler;
import com.enviouse.sef.chat.ChatMessageManager;
import com.enviouse.sef.chat.ChatReplyHandler;
import com.enviouse.sef.commands.MsgCommands;
import com.enviouse.sef.config.ConfigHandler;
import com.enviouse.sef.config.IReloadable;
import com.enviouse.sef.config.PermissionsHandler;
import com.enviouse.sef.utils.SEFUtilities;
import com.enviouse.sef.utils.moddeps.FTBMuteChecker;
import com.enviouse.sef.mute.MuteManager;
import com.mojang.authlib.GameProfile;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber
public class ChatEventHandler implements IReloadable {
	private SimpleDateFormat timestampFormat = null;
	private boolean markdownEnabled = false;
	private static String chatMessageFormat = "";
	private static String chatMessageColor = "";
	private boolean loaded = false;
	
        @Override
	public void reloadConfigOptions() {
		loaded = false;
		timestampFormat = ConfigHandler.config.enableTimestamp.get() ? new SimpleDateFormat(ConfigHandler.config.timestampFormat.get()) : null;
		markdownEnabled = ConfigHandler.config.enableMarkdown.get();
		chatMessageFormat = ConfigHandler.config.chatMessageFormat.get();
		chatMessageColor = ConfigHandler.config.chatMessageColor.get();
		loaded = true;
	}

	public static String getChatMessageColor() {
		ServerEssentialsForge.LOGGER.debug("Chat color from config: {}",chatMessageColor);
		String color = chatMessageColor;
		return color;
	}

	public Style getHoverClickEventStyle(Component old) {
		if(old instanceof TranslatableContents tcmp) {
            Object[] args = tcmp.getArgs();
			for(Object arg : args) {
				if(arg instanceof MutableComponent tc) {
                    if(tc.getStyle() != null && tc.getStyle().getClickEvent() != null)
						return ((MutableComponent) arg).getStyle();
				}
			}
		}
		return null;
	}
	
	@SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST, receiveCanceled = false)
    public void onServerChat(ServerChatEvent e) {
		// Early exit if event is already cancelled
		if(e.isCanceled()) return;
		if(!loaded) return; // Just do nothing until everything's ready to go!
    	ServerPlayer player = e.getPlayer();
		if(player == null) return;
        GameProfile profile = player.getGameProfile();
    	UUID uuid = profile.getId();
        String msg = e.getMessage().getString();
		if(msg == null || (msg).isEmpty()) return;

		// Check if player is muted via persistent MuteManager (our own system)
		if(ConfigHandler.config.enableMuteSystem.get()) {
			MuteManager muteManager = CommandRegistrationHandler.getMuteManager();
			if(muteManager != null && muteManager.isMuted(uuid)) {
				MuteManager.MuteEntry muteEntry = muteManager.getMuteEntry(uuid);
				// Show muted message with remaining time if applicable
				String muteMsg;
				if(muteEntry != null && !muteEntry.isPermanent()) {
					muteMsg = ConfigHandler.config.mutedPlayerChatMsgWithRemaining.get()
						.replace("$remaining", muteEntry.getRemainingString());
				} else {
					muteMsg = ConfigHandler.config.mutedPlayerChatMsg.get();
				}
				player.sendSystemMessage(TextFormatter.stringToFormattedText(muteMsg));

				// Optionally relay to operators
				if(ConfigHandler.config.sendMutedMessageToOps.get()) {
					String opFormat = ConfigHandler.config.mutedMessageOpFormat.get()
						.replace("$username", profile.getName())
						.replace("$message", msg);
					MutableComponent opMsg = TextFormatter.stringToFormattedText(opFormat);
					for(ServerPlayer op : player.getServer().getPlayerList().getPlayers()) {
						if(PermissionsHandler.playerHasPermission(op.getUUID(), PermissionsHandler.muteSeeBlocked)) {
							op.sendSystemMessage(opMsg);
						}
					}
				}

				e.setCanceled(true);
				ServerEssentialsForge.LOGGER.info("[MUTED] {} tried to say: {}", profile.getName(), msg);
				return;
			}
		}

		// Check if player is muted via FTB Essentials
		if(ConfigHandler.config.enableFtbMuteIntegration.get() && FTBMuteChecker.isMuted(player)) {
			// Send muted message to the player
			player.sendSystemMessage(TextFormatter.stringToFormattedText(
				ConfigHandler.config.mutedPlayerMessage.get()));

			// Optionally relay to operators
			if(ConfigHandler.config.sendMutedMessageToOps.get()) {
				String opFormat = ConfigHandler.config.mutedMessageOpFormat.get()
					.replace("$username", profile.getName())
					.replace("$message", msg);
				MutableComponent opMsg = TextFormatter.stringToFormattedText(opFormat);
				for(ServerPlayer op : player.getServer().getPlayerList().getPlayers()) {
					if(PermissionsHandler.playerHasPermission(op.getUUID(), PermissionsHandler.muteSeeBlocked)) {
						op.sendSystemMessage(opMsg);
					}
				}
			}

			e.setCanceled(true);
			ServerEssentialsForge.LOGGER.info("[MUTED] {} tried to say: {}", profile.getName(), msg);
			return;
		}

		// IMPORTANT: Check if this should be a private/admin message FIRST
		// We need to BOTH cancel the event AND execute the command
		// This ensures Discord doesn't see it while still processing the message

		// Check if player has admin chat toggled
		if(ConfigHandler.config.enableAdminChat.get() && AdminChatHandler.isAdminChatToggled(uuid)) {
			// Execute the /ac command directly
			ServerEssentialsForge.LOGGER.info("[SEF] Intercepting admin chat from {}: {}", profile.getName(), msg);
			player.getServer().getCommands().performPrefixedCommand(
				player.createCommandSourceStack(),
				"ac " + msg
			);
			e.setCanceled(true);
			return;
		}

		// Check if player has private chat toggled
		if(ConfigHandler.config.enableMessagingSystem.get() && MsgCommands.isPrivateChatToggled(uuid)) {
			java.util.UUID partnerUUID = MsgCommands.getPrivateChatPartner(uuid);
			if(partnerUUID != null) {
				ServerPlayer partner = player.getServer().getPlayerList().getPlayer(partnerUUID);
				if(partner != null) {
					ServerEssentialsForge.LOGGER.info("[SEF] Intercepting private chat from {} to {}: {}",
						profile.getName(), partner.getGameProfile().getName(), msg);
					player.getServer().getCommands().performPrefixedCommand(
						player.createCommandSourceStack(),
						"msg " + partner.getGameProfile().getName() + " " + msg
					);
					e.setCanceled(true);
					return;
				}
			}
		}

		// Apply word filters if enabled
		if(ConfigHandler.config.enableFilterSystem.get() && CommandRegistrationHandler.getFilterManager() != null) {
			msg = CommandRegistrationHandler.getFilterManager().applyFilters(msg);
		}

		String tstamp = timestampFormat == null ? "" : timestampFormat.format(new Date());
		String name = SEFUtilities.getRawPreferredPlayerName(profile);
		String fmat = chatMessageFormat.replace("$time", tstamp).replace("$name", name);
		int msgIdx = fmat.indexOf("$msg");
		if(msgIdx < 0) {
			ServerEssentialsForge.LOGGER.warn("[SEF] chatMessageFormat is missing '$msg' placeholder — appending to end. Current format: {}", chatMessageFormat);
			fmat = fmat + "$msg";
			msgIdx = fmat.indexOf("$msg");
		}
		MutableComponent beforeMsg = TextFormatter.stringToFormattedText(fmat.substring(0, msgIdx));
		MutableComponent afterMsg = TextFormatter.stringToFormattedText(fmat.substring(msgIdx + 4));
		boolean enableColor = PermissionsHandler.playerHasPermission(uuid, PermissionsHandler.coloredChatNode);
		boolean enableStyle = PermissionsHandler.playerHasPermission(uuid, PermissionsHandler.styledChatNode);
		
		// Create an error message if the player isn't allowed to use styles/colors
		String emsg = "";
		if(!enableColor && TextFormatter.messageContainsColorsOrStyles(msg, true))
			emsg = "You are not permitted to use colors";
		if(!enableStyle && TextFormatter.messageContainsColorsOrStyles(msg, false))
			emsg += !emsg.isEmpty() ? " or styles" : "You are not permitted to use styles";
		if(!emsg.isEmpty()) {
			MutableComponent ecmp = Component.literal(emsg + "!");
			ecmp.withStyle(ChatFormatting.BOLD);
			ecmp.withStyle(ChatFormatting.RED);
			player.sendSystemMessage(ecmp);
		}
		// Convert markdown to normal essentials formatting
		if(markdownEnabled && enableStyle && PermissionsHandler.playerHasPermission(uuid, PermissionsHandler.markdownChatNode))
			msg = MarkdownFormatter.markdownStringToFormattedString(msg);

		// Start generating the main TextComponent
		MutableComponent msgComp = TextFormatter.stringToFormattedText(msg, enableColor, enableStyle, uuid);

		e.setCanceled(true);
		
		MutableComponent newMessage = beforeMsg.append(msgComp.append(afterMsg));
		
		// Record the message and get its ID for the reply system
		final String finalMsg = msg;
		final String formattedName = name; // name already includes prefix/suffix from getRawPreferredPlayerName
		final String rawName = profile.getName(); // raw username without prefix/suffix
		player.server.execute(() -> {
			// Record message for /ans reply system if enabled
			long messageId = -1;
			if(ConfigHandler.config.enableChatReplies.get()) {
				messageId = ChatMessageManager.recordMessage(uuid, rawName, formattedName, finalMsg);
			}

			// Make message clickable if chat replies are enabled
			MutableComponent clickableMessage;
			if(ConfigHandler.config.enableChatReplies.get() && messageId > 0) {
				final long msgId = messageId;
				clickableMessage = newMessage.withStyle(style -> style
					.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/ans " + msgId + " "))
					.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
						TextFormatter.stringToFormattedText("&eClick to reply"))));
			} else {
				clickableMessage = newMessage;
			}

			ServerEssentialsForge.LOGGER.info("[CHAT] "+clickableMessage.getString());
			ServerMessageEvent.broadcastMessageVanishAware(player.level(), clickableMessage, player);
		});
		
    }
}