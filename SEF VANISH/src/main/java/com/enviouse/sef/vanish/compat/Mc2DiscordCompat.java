package com.enviouse.sef.vanish.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Compatibility with mc2discord.
 * Uses reflection to avoid compile-time dependency.
 */
public class Mc2DiscordCompat {
	private static final Logger LOGGER = LogManager.getLogger("SEF/Mc2Discord");
	private static boolean initialized = false;
	private static Object hiddenPlayerList = null;
	private static Method addMethod = null;
	private static Method containsMethod = null;
	private static Method removeMethod = null;
	private static Class<?> hiddenPlayerEntryClass = null;
	private static java.lang.reflect.Constructor<?> hiddenPlayerEntryConstructor = null;

	// Message sending
	private static Method m2dUtilsIsNotConfigured = null;
	private static Method messageManagerSendInfoMessage = null;
	private static Object configInstance = null;

	private static void init() {
		if (initialized) return;
		initialized = true;
		try {
			Class<?> mc2discordClass = Class.forName("fr.denisd3d.mc2discord.core.Mc2Discord");
			Field instanceField = mc2discordClass.getDeclaredField("INSTANCE");
			Object mc2discordInstance = instanceField.get(null);
			if (mc2discordInstance == null) return;
			Field hiddenField = mc2discordInstance.getClass().getDeclaredField("hiddenPlayerList");
			hiddenPlayerList = hiddenField.get(mc2discordInstance);
			Field configField = mc2discordInstance.getClass().getDeclaredField("config");
			configInstance = configField.get(mc2discordInstance);

			Class<?> hplClass = hiddenPlayerList.getClass();
			containsMethod = hplClass.getMethod("contains", java.util.UUID.class);
			addMethod = hplClass.getMethod("add", Object.class);
			removeMethod = hplClass.getMethod("remove", java.util.UUID.class);

			hiddenPlayerEntryClass = Class.forName("fr.denisd3d.mc2discord.core.storage.HiddenPlayerEntry");
			hiddenPlayerEntryConstructor = hiddenPlayerEntryClass.getConstructor(java.util.UUID.class);

			Class<?> m2dUtilsClass = Class.forName("fr.denisd3d.mc2discord.core.M2DUtils");
			m2dUtilsIsNotConfigured = m2dUtilsClass.getMethod("isNotConfigured");

			Class<?> messageManagerClass = Class.forName("fr.denisd3d.mc2discord.core.MessageManager");
			messageManagerSendInfoMessage = messageManagerClass.getMethod("sendInfoMessage", String.class, String.class);
		} catch (Exception e) {
			LOGGER.debug("mc2discord reflection init failed: {}", e.getMessage());
		}
	}

	public static void hidePlayer(ServerPlayer player, boolean hide) {
		init();
		try {
			if (hiddenPlayerList == null) return;
			java.util.UUID uuid = player.getGameProfile().getId();
			boolean contains = (boolean) containsMethod.invoke(hiddenPlayerList, uuid);
			if (hide) {
				if (!contains) {
					Object entry = hiddenPlayerEntryConstructor.newInstance(uuid);
					addMethod.invoke(hiddenPlayerList, entry);
				}
			} else if (contains) {
				removeMethod.invoke(hiddenPlayerList, uuid);
			}
		} catch (Exception e) {
			LOGGER.debug("hidePlayer failed: {}", e.getMessage());
		}
	}

	public static boolean isHidden(ServerPlayer player) {
		init();
		try {
			if (hiddenPlayerList == null || containsMethod == null) return false;
			return (boolean) containsMethod.invoke(hiddenPlayerList, player.getGameProfile().getId());
		} catch (Exception e) {
			return false;
		}
	}

	public static void sendFakeJoinLeaveMessage(ServerPlayer player, boolean left) {
		init();
		try {
			if (m2dUtilsIsNotConfigured != null && (boolean) m2dUtilsIsNotConfigured.invoke(null))
				return;
			if (messageManagerSendInfoMessage == null || configInstance == null) return;

			// Get the message format from config
			Field messagesField = configInstance.getClass().getDeclaredField("messages");
			Object messages = messagesField.get(configInstance);
			String fieldName = left ? "leave" : "join";
			Field msgField = messages.getClass().getDeclaredField(fieldName);
			Object msgObj = msgField.get(messages);
			Method asStringMethod = msgObj.getClass().getMethod("asString");
			String msgTemplate = (String) asStringMethod.invoke(msgObj);

			// Build player entity via reflection
			Class<?> playerEntityClass = Class.forName("fr.denisd3d.mc2discord.core.entities.PlayerEntity");
			java.lang.reflect.Constructor<?> peCtor = playerEntityClass.getConstructor(String.class, String.class, java.util.UUID.class);
			Object playerEntity = peCtor.newInstance(player.getGameProfile().getName(), player.getDisplayName().getString(), player.getGameProfile().getId());

			Class<?> entityClass = Class.forName("fr.denisd3d.mc2discord.core.entities.Entity");
			Method replaceMethod = entityClass.getMethod("replace", String.class, java.util.List.class);
			String msg = (String) replaceMethod.invoke(null, msgTemplate, java.util.Collections.singletonList(playerEntity));

			Object result = messageManagerSendInfoMessage.invoke(null, "vanish", msg);
			if (result != null) {
				Method subscribe = result.getClass().getMethod("subscribe");
				subscribe.invoke(result);
			}
		} catch (Exception e) {
			LOGGER.debug("sendFakeJoinLeaveMessage failed: {}", e.getMessage());
		}
	}
}
