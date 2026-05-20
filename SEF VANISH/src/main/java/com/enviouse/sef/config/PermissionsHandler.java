package com.enviouse.sef.config;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.enviouse.sef.ServerEssentialsForge;
import com.enviouse.sef.TextFormatter;

import net.minecraft.network.chat.Component;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.server.permission.PermissionAPI;
import net.minecraftforge.server.permission.events.PermissionGatherEvent.Nodes;
import net.minecraftforge.server.permission.nodes.PermissionNode;
import net.minecraftforge.server.permission.nodes.PermissionTypes;

@SuppressWarnings("unused") // Fields are accessed via reflection in registerPermissionNodes
@EventBusSubscriber
public class PermissionsHandler {	
	// Chat Permissions
	public static PermissionNode<Boolean> coloredChatNode =
			ezyPermission("chat.colors", true, "Chat colors", "Enables/Disables colors in chat");
	public static PermissionNode<Boolean> styledChatNode = 
			ezyPermission("chat.styles", true, "Chat styles", "Enables/Disables styles in chat");
	public static PermissionNode<Boolean> markdownChatNode = 
			ezyPermission("chat.styles.md", true, "Chat markdown styling", "Enables/Disables markdown styling in chat");

	// Tab List Permissions
	public static PermissionNode<Boolean> tabListNicknameNode =
			ezyPermission("tablist.nickname", true, "Tab list nicknames", "Enables/Disables nicknames showing in the tab list");
	public static PermissionNode<Boolean> tabListMetadataNode = 
			ezyPermission("tablist.metadata", true, "Tab list metadata", "Enables/Disables prefixes&suffixes showing in the tab list");
	
	// Core Command Permissions
	public static PermissionNode<Boolean> colorsCommand =
			ezyPermission("commands.colors", true, "Colors command", "Enables/Disables the \"/colors\" command");
	public static PermissionNode<Boolean> sefCommand = 
			ezyPermission("commands.sef.allowed", true, "SEF command", "Enables/Disables the \"/sef\" command");
	public static PermissionNode<Boolean> sefCommandColorsSubCommand =
			ezyPermission("commands.sef.colors", true, "SEF colors sub-command", "Enables/Disables the \"/sef colors\" sub-command");
	public static PermissionNode<Boolean> sefCommandInfoSubCommand =
			ezyPermission("commands.sef.info", true, "SEF info sub-command", "Enables/Disables the \"/sef info\" sub-command");
	public static PermissionNode<Boolean> sefCommandReloadSubCommand =
			ezyPermission("commands.sef.reload", true, "SEF reload sub-command", "Enables/Disables the \"/sef reload\" sub-command");

	// Private Messaging Permissions
	public static PermissionNode<Boolean> msgCommand =
			ezyPermission("commands.msg", true, "Private messages", "Allows using /msg, /r and aliases");
	public static PermissionNode<Boolean> msgReceive =
			ezyPermission("msg.receive", true, "Receive messages", "Allows receiving private messages");
	public static PermissionNode<Boolean> msgSendToOffline =
			ezyPermission("msg.sendoffline", false, "Message offline", "Allows sending messages to offline players (queued)");

	// Nickname Permissions
	public static PermissionNode<Boolean> whoisCommand =
			ezyPermission("commands.whois", true, "Nickname", "Enables/Disables the \"/whois <nickname>\" command");
	public static PermissionNode<Boolean> nickCommand = 
			ezyPermission("commands.nick", true, "Nickname", "Enables/Disables the \"/nick <nickname>\" command");
	public static PermissionNode<Boolean> nickOthersCommand = 
			ezyPermission("commands.nick.others", true, "Modify nicknames", "Enables/Disables the \"/nick <username> <nickname>\" command");
	public static PermissionNode<Boolean> nickColorsAllowed =
			ezyPermission("nick.colors", false, "Nickname colors", "Allows using color codes in nicknames");
	public static PermissionNode<Boolean> nickStylesAllowed =
			ezyPermission("nick.styles", false, "Nickname styles", "Allows using style codes in nicknames");

	// Chat Reply Permissions
	public static PermissionNode<Boolean> ansCommand =
			ezyPermission("commands.ans", true, "Reply command", "Allows using the /ans reply command");
	public static PermissionNode<Boolean> ansReceiveNotification =
			ezyPermission("ans.notify", true, "Reply notifications", "Receive sound when someone replies to you");

	// HelpOp Permissions
	public static PermissionNode<Boolean> helpOpSend =
			ezyPermission("helpop.send", true, "Send HelpOp", "Allows sending /helpop requests");
	public static PermissionNode<Boolean> helpOpReceive =
			ezyPermission("helpop.receive", false, "Receive HelpOp", "Receives HelpOp messages (operators)");
	public static PermissionNode<Boolean> helpOpReply =
			ezyPermission("helpop.reply", false, "Reply HelpOp", "Allows using /helpopop to reply");

	// Admin Chat Permissions
	public static PermissionNode<Boolean> adminChatUse =
			ezyPermission("adminchat.use", false, "Use Admin Chat", "Allows using admin chat");
	public static PermissionNode<Boolean> adminChatSee =
			ezyPermission("adminchat.see", false, "See Admin Chat", "Can see admin chat messages");

	// Mute System Permissions
	public static PermissionNode<Boolean> muteSeeBlocked =
			ezyPermission("mute.seeblocked", false, "See muted messages", "Receives muted player messages relayed to operators");
	public static PermissionNode<Boolean> muteCommand =
			ezyPermission("commands.mute", false, "Mute command", "Allows using /mute to mute a player");
	public static PermissionNode<Boolean> unmuteCommand =
			ezyPermission("commands.unmute", false, "Unmute command", "Allows using /unmute to unmute a player");
	public static PermissionNode<Boolean> muteNotify =
			ezyPermission("mute.notify", false, "Mute notifications", "Receives notifications when players are muted/unmuted");

	// InvSee Permissions
	public static PermissionNode<Boolean> invSeeCommand =
			ezyPermission("commands.invsee", false, "InvSee command", "Allows using /invsee to view another player's inventory");

	// Freeze Permissions
	public static PermissionNode<Boolean> freezeCommand =
			ezyPermission("commands.freeze", false, "Freeze command", "Allows using /freeze to freeze a player in place");
	public static PermissionNode<Boolean> unfreezeCommand =
			ezyPermission("commands.unfreeze", false, "Unfreeze command", "Allows using /unfreeze to unfreeze a player");
	public static PermissionNode<Boolean> freezeNotify =
			ezyPermission("freeze.notify", false, "Freeze notifications", "Receives notifications when players are frozen/unfrozen");

	// Clear Chat Permissions
	public static PermissionNode<Boolean> clearChatCommand =
			ezyPermission("commands.clearchat", false, "Clear Chat command", "Allows using /cc and /clearchat to clear chat");

	// Sudo Permissions
	public static PermissionNode<Boolean> sudoCommand =
			ezyPermission("commands.sudo", false, "Sudo command", "Allows using /sudo to force a player to execute a command");

	// Inventory Lock Permissions
	public static PermissionNode<Boolean> invLockCommand =
			ezyPermission("commands.invlock", false, "InvLock command", "Allows using /invlock to lock/unlock a player's inventory");

	// Disable Building Permissions
	public static PermissionNode<Boolean> disableBuildingCommand =
			ezyPermission("commands.disablebuilding", false, "Disable Building command", "Allows using /disablebuilding to toggle building restrictions");

	// Check Alts Permissions
	public static PermissionNode<Boolean> checkAltsCommand =
			ezyPermission("commands.checkalts", false, "Check Alts command", "Allows using /checkalts to list alternate accounts");

	// Warn System Permissions
	public static PermissionNode<Boolean> warnCommand =
			ezyPermission("commands.warn", false, "Warn command", "Allows using /warn to add/check/remove warnings on players");
	public static PermissionNode<Boolean> warnsSelfCommand =
			ezyPermission("commands.warns", true, "Warns self command", "Allows using /warns to check own warnings");

	// Announcement Permissions
	public static PermissionNode<Boolean> announcementManage =
			ezyPermission("announcements.manage", false, "Manage announcements", "Allows adding/removing announcements");
	public static PermissionNode<Boolean> announcementToggle =
			ezyPermission("announcements.toggle", true, "Toggle announcements", "Allows toggling announcements on/off");
	public static PermissionNode<Boolean> announcementBypass =
			ezyPermission("announcements.bypass", false, "Bypass announcements", "Receives announcements even if toggled off");
	public static PermissionNode<Boolean> titleAnnouncementUse =
			ezyPermission("announcements.title", false, "Title announcements", "Allows using /titleannouncement");

	// Filter Permissions
	public static PermissionNode<Boolean> filterManage =
			ezyPermission("filter.manage", false, "Manage filters", "Allows managing word filters");
	public static PermissionNode<Boolean> filterBypass =
			ezyPermission("filter.bypass", false, "Bypass filters", "Messages bypass word filter");

	// Banned Items Permissions
	public static PermissionNode<Boolean> bannedCommand =
			ezyPermission("commands.banned", false, "Banned command", "Allows using /banned subcommands (add/remove/etc)");
	public static PermissionNode<Boolean> bannedBypassNode =
			ezyPermission("banned.bypass", false, "Bypass banned items", "Player is exempt from banned-item confiscation and banned-block sweeps");

	// Countdown Permissions
	public static PermissionNode<Boolean> countdownCommand =
			ezyPermission("commands.countdown", false, "Countdown command", "Allows using /countdown to broadcast a timed countdown");

	// Sign and Misc Permissions
	public static PermissionNode<Boolean> signColorNode =
			ezyPermission("sign.colors", false, "Sign colors", "Allows usage of colors on signs");
	public static PermissionNode<Boolean> signStylesNode =
			ezyPermission("sign.styles", false, "Sign styles", "Allows usage of styles on signs");

	// Color Permissions
	public static final Map<Character, PermissionNode<Boolean>> perColorChatNodes = new HashMap<>();
	public static PermissionNode<Boolean> hexChatNode;

	static {
        // Allow hex by default? set to true for convenience
        hexChatNode = ezyPermission("chat.colors.hex", true, "Chat hex colors", "Allows usage of hex colors and gradients in chat");
        // Per-color nodes (&0 - &9, &a - &f)
        for(char c : "0123456789abcdef".toCharArray()) {
            perColorChatNodes.put(c, ezyPermission("chat.colors." + c, true, "Chat color &" + c, "Allows usage of &" + c + " in chat"));
        }
    }

	@SubscribeEvent public void registerPermissionNodes(Nodes pge) {
		for(Field fld : PermissionsHandler.class.getDeclaredFields()) {
			if(fld.getType() == PermissionNode.class) {
				try { // Fuck adding all these nodes manually
					pge.addNodes((PermissionNode<?>) fld.get(PermissionNode.class));
				} catch (Exception error) {
                    ServerEssentialsForge.LOGGER.trace("Exception: Caught on adding permission nodes", error);
				}
			}
		}
		// register per-color nodes
        for(PermissionNode<Boolean> node : perColorChatNodes.values()) {
            pge.addNodes(node);
        }
    }

	private static PermissionNode<Boolean> ezyPermission(String id, boolean defVal, String name, String desc) {
		PermissionNode<Boolean> node = new PermissionNode<>(ServerEssentialsForge.MODID, id, 
				PermissionTypes.BOOLEAN, (player, uuid, context) -> defVal);
		node.setInformation(Component.literal(name),TextFormatter.stringToFormattedText(desc));
		return node;
	}

	public static boolean playerHasPermission(UUID uuid, PermissionNode<Boolean> node) {
		boolean bool = false;
		try {
			bool = PermissionAPI.getOfflinePermission(uuid, node);
		} catch(IllegalStateException ise) {
			ServerEssentialsForge.LOGGER.trace("IllegalStateException when getting player tab list permissions, assuming false",ise);
		}
		return bool;
	}
	public static boolean playerHasColorPermission(UUID uuid, char code) {
        PermissionNode<Boolean> node = perColorChatNodes.get(Character.toLowerCase(code));
        if(node == null) return false;
        return playerHasPermission(uuid, node);
    }
}
