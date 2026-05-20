package com.enviouse.sef.vanish.compat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Compatibility with the SDLink (Simple Discord Link) mod.
 * Uses reflection to avoid compile-time dependency on SDLink/CraterLib.
 * Hides/shows players from Discord via SDLink's HiddenPlayersManager
 * and sends fake join/leave Discord messages on vanish/unvanish.
 */
public class SDLinkCompat {
	private static final Logger LOGGER = LogManager.getLogger("SEF/SDLink");

	private static boolean initialized = false;

	// HiddenPlayersManager reflection
	private static Object hiddenPlayersManager = null;
	private static Method hidePlayerMethod = null;   // hidePlayer(String identifier, String displayName, String type)
	private static Method unhidePlayerMethod = null;  // unhidePlayer(String identifier)

	// SDLink DiscordMessage API reflection (for sending join/leave messages)
	private static boolean messageApiAvailable = false;
	private static Object messageTypeJoin = null;
	private static Object messageTypeLeave = null;
	private static Constructor<?> messageBuilderConstructor = null;
	private static Method builderMessageMethod = null;
	private static Method builderAuthorMethod = null;
	private static Method builderBuildMethod = null;
	private static Method discordMessageSendMethod = null;
	private static Method getServerAuthorMethod = null;
	private static Method setPlayerNameMethod = null;
	private static Method setPlayerAvatarMethod = null;

	// Config reflection for message formats and vanish compat check
	private static Field sdlinkConfigInstanceField = null;
	private static Field sdlinkCompatConfigInstanceField = null;

	private static void init() {
		if (initialized) return;
		initialized = true;

		// --- HiddenPlayersManager ---
		try {
			Class<?> hpmClass = Class.forName("com.hypherionmc.sdlink.core.managers.HiddenPlayersManager");
			Field instanceField = hpmClass.getDeclaredField("INSTANCE");
			hiddenPlayersManager = instanceField.get(null);
			hidePlayerMethod = hpmClass.getMethod("hidePlayer", String.class, String.class, String.class);
			unhidePlayerMethod = hpmClass.getMethod("unhidePlayer", String.class);
		} catch (Exception e) {
			LOGGER.debug("SDLink HiddenPlayersManager not available: {}", e.getMessage());
		}

		// --- MessageType enum ---
		try {
			@SuppressWarnings("unchecked")
			Class<? extends Enum> messageTypeClass = (Class<? extends Enum>) Class.forName("com.hypherionmc.sdlink.api.messaging.MessageType");
			messageTypeJoin = Enum.valueOf(messageTypeClass, "JOIN");
			messageTypeLeave = Enum.valueOf(messageTypeClass, "LEAVE");
		} catch (Exception e) {
			LOGGER.debug("SDLink MessageType not available: {}", e.getMessage());
		}

		// --- DiscordMessageBuilder & DiscordMessage ---
		try {
			Class<?> messageTypeClass = Class.forName("com.hypherionmc.sdlink.api.messaging.MessageType");
			Class<?> builderClass = Class.forName("com.hypherionmc.sdlink.api.messaging.discord.DiscordMessageBuilder");
			messageBuilderConstructor = builderClass.getConstructor(messageTypeClass);
			builderMessageMethod = builderClass.getMethod("message", String.class);
			Class<?> authorClass = Class.forName("com.hypherionmc.sdlink.api.accounts.DiscordAuthor");
			builderAuthorMethod = builderClass.getMethod("author", authorClass);
			builderBuildMethod = builderClass.getMethod("build");

			Class<?> discordMessageClass = Class.forName("com.hypherionmc.sdlink.api.messaging.discord.DiscordMessage");
			discordMessageSendMethod = discordMessageClass.getMethod("sendMessage");

			getServerAuthorMethod = authorClass.getMethod("getServer");
			setPlayerNameMethod = authorClass.getMethod("setPlayerName", String.class);
			setPlayerAvatarMethod = authorClass.getMethod("setPlayerAvatar", String.class, String.class);

			messageApiAvailable = true;
		} catch (Exception e) {
			LOGGER.debug("SDLink DiscordMessage API not available: {}", e.getMessage());
		}

		// --- SDLink config ---
		try {
			sdlinkConfigInstanceField = Class.forName("com.hypherionmc.sdlink.core.config.SDLinkConfig").getDeclaredField("INSTANCE");
		} catch (Exception e) {
			LOGGER.debug("SDLinkConfig not available: {}", e.getMessage());
		}

		try {
			sdlinkCompatConfigInstanceField = Class.forName("com.hypherionmc.sdlink.core.config.SDLinkCompatConfig").getDeclaredField("INSTANCE");
		} catch (Exception e) {
			LOGGER.debug("SDLinkCompatConfig not available: {}", e.getMessage());
		}
	}

	/**
	 * Called when vanish status changes. Hides/shows player in SDLink's HiddenPlayersManager.
	 * @deprecated Use {@link SDLinkHideTracker} instead to properly reference-count hide reasons.
	 */
	@Deprecated
	public static void onVanishChange(ServerPlayer player, boolean vanished) {
		setHidden(player.getStringUUID(), player.getName().getString(), vanished);
	}

	/**
	 * Low-level hide/unhide from SDLink's HiddenPlayersManager.
	 * Callers should generally use {@link SDLinkHideTracker} instead of calling this directly,
	 * so that multiple hide reasons (vanish, admin chat, private msg) don't conflict.
	 *
	 * @param identifier  The player UUID string
	 * @param displayName The player display name (only needed when hiding, may be null for unhide)
	 * @param hide        true to hide, false to unhide
	 */
	public static void setHidden(String identifier, String displayName, boolean hide) {
		init();
		try {
			if (hiddenPlayersManager == null) return;
			if (hide) {
				if (hidePlayerMethod != null)
					hidePlayerMethod.invoke(hiddenPlayersManager, identifier, displayName != null ? displayName : "Unknown", "sef");
			} else {
				if (unhidePlayerMethod != null)
					unhidePlayerMethod.invoke(hiddenPlayersManager, identifier);
			}
		} catch (Exception e) {
			LOGGER.debug("Failed to update SDLink hidden player status: {}", e.getMessage());
		}
	}

	/**
	 * Sends a fake join or leave message to Discord via SDLink's DiscordMessage API.
	 * NOTE: This method only sends the Discord message. Hide/unhide is now managed
	 * by {@link SDLinkHideTracker} to prevent conflicts between vanish, admin chat,
	 * and private message toggle hide reasons.
	 */
	public static void sendFakeJoinLeave(ServerPlayer player, boolean leaving) {
		init();
		sendDiscordJoinLeaveMessage(player, leaving);
	}

	private static void sendDiscordJoinLeaveMessage(ServerPlayer player, boolean leaving) {
		if (!messageApiAvailable) {
			LOGGER.warn("SDLink DiscordMessage API not available — cannot send fake {} message for {}", leaving ? "leave" : "join", player.getName().getString());
			return;
		}
		try {
			if (!isBotReady()) {
				LOGGER.warn("SDLink bot not ready — cannot send fake {} message for {}", leaving ? "leave" : "join", player.getName().getString());
				return;
			}
			if (!shouldSendFakeJoinLeave()) return;
			if (leaving && !isChatConfigFlag("playerLeave")) return;
			if (!leaving && !isChatConfigFlag("playerJoin")) return;

			// Get the message format from SDLink config, with a fallback
			String msg = getMessageFormat(leaving);
			if (msg == null) {
				msg = leaving ? "%player% left the game" : "%player% joined the game";
			}
			msg = msg.replace("%player%", player.getName().getString());

			// Build the Discord message
			Object messageType = leaving ? messageTypeLeave : messageTypeJoin;
			Object builder = messageBuilderConstructor.newInstance(messageType);
			builder = builderMessageMethod.invoke(builder, msg);

			// Create the server author with player info (for avatar)
			Object author = getServerAuthorMethod.invoke(null);
			setPlayerNameMethod.invoke(author, player.getName().getString());
			setPlayerAvatarMethod.invoke(author, player.getGameProfile().getName(), player.getStringUUID());
			builder = builderAuthorMethod.invoke(builder, author);

			Object discordMessage = builderBuildMethod.invoke(builder);
			discordMessageSendMethod.invoke(discordMessage);

			LOGGER.debug("Sent fake {} message to Discord for {}", leaving ? "leave" : "join", player.getName().getString());
		} catch (Exception e) {
			LOGGER.warn("Failed to send fake {} message to SDLink for {}: {}", leaving ? "leave" : "join", player.getName().getString(), e.getMessage());
		}
	}

	// --- Helper methods for reading SDLink config via reflection ---

	private static boolean isBotReady() {
		try {
			Class<?> botControllerClass = Class.forName("com.hypherionmc.sdlink.core.discord.BotController");
			Field bcField = botControllerClass.getDeclaredField("INSTANCE");
			bcField.setAccessible(true);
			Object bc = bcField.get(null);
			if (bc == null) return false;
			Method ready = botControllerClass.getMethod("isBotReady");
			return (boolean) ready.invoke(bc);
		} catch (Exception e) {
			return false;
		}
	}

	private static boolean shouldSendFakeJoinLeave() {
		try {
			if (sdlinkCompatConfigInstanceField == null) return true;
			Object compatConfig = sdlinkCompatConfigInstanceField.get(null);
			if (compatConfig == null) return true;
			Field vanishCompatField = compatConfig.getClass().getDeclaredField("vanishCompat");
			vanishCompatField.setAccessible(true);
			Object vanishCompat = vanishCompatField.get(compatConfig);
			if (vanishCompat == null) return true;
			Field sendFakeField = vanishCompat.getClass().getDeclaredField("sendFakeJoinLeaveMessage");
			sendFakeField.setAccessible(true);
			return sendFakeField.getBoolean(vanishCompat);
		} catch (Exception e) {
			return true;
		}
	}

	private static boolean isChatConfigFlag(String fieldName) {
		try {
			if (sdlinkConfigInstanceField == null) return true;
			Object config = sdlinkConfigInstanceField.get(null);
			if (config == null) return true;
			Field chatConfigField = config.getClass().getDeclaredField("chatConfig");
			chatConfigField.setAccessible(true);
			Object chatConfig = chatConfigField.get(config);
			if (chatConfig == null) return true;
			Field flagField = chatConfig.getClass().getDeclaredField(fieldName);
			flagField.setAccessible(true);
			return flagField.getBoolean(chatConfig);
		} catch (Exception e) {
			return true;
		}
	}

	private static String getMessageFormat(boolean leaving) {
		try {
			if (sdlinkConfigInstanceField == null) return null;
			Object config = sdlinkConfigInstanceField.get(null);
			if (config == null) return null;
			Field messageFmtField = config.getClass().getDeclaredField("messageFormatting");
			messageFmtField.setAccessible(true);
			Object messageFormatting = messageFmtField.get(config);
			if (messageFormatting == null) return null;
			String fieldName = leaving ? "playerLeft" : "playerJoined";
			Field formatField = messageFormatting.getClass().getDeclaredField(fieldName);
			formatField.setAccessible(true);
			return (String) formatField.get(messageFormatting);
		} catch (Exception e) {
			return null;
		}
	}
}
