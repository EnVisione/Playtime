package com.enviouse.sef.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.common.ForgeConfigSpec;

public class ConfigHandler {
	private static final ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
	public static final ConfigBuilder config = new ConfigBuilder(builder);
	public static ForgeConfigSpec spec = builder.build();

    public static void reloadFromDisk() {
        // Re-read the common config from disk and apply to the spec
        java.nio.file.Path path = FMLPaths.CONFIGDIR.get().resolve("sef").resolve("common.toml");
        try {
            java.nio.file.Files.createDirectories(path.getParent());
        } catch (java.io.IOException ignored) {}
        CommentedFileConfig cfg = CommentedFileConfig.builder(path)
                .sync()
                .autosave()
                .writingMode(WritingMode.REPLACE)
                .build();
        cfg.load();
        spec.setConfig(cfg);
    }

	public static class ConfigBuilder {
		public final ForgeConfigSpec.ConfigValue<String> playerNameFormat;
		public final ForgeConfigSpec.ConfigValue<String> chatMessageFormat;
		public final ForgeConfigSpec.ConfigValue<String> chatMessageColor;
		public final ForgeConfigSpec.ConfigValue<String> timestampFormat;
		public final ForgeConfigSpec.ConfigValue<String> discordBotToken;

		public final ForgeConfigSpec.ConfigValue<Integer> maximumNicknameLength;
		public final ForgeConfigSpec.ConfigValue<Integer> minimumNicknameLength;
		public final ForgeConfigSpec.ConfigValue<String> metaJoinSeparator;
		public final ForgeConfigSpec.ConfigValue<Integer> maxPrefixesDisplayed;
		public final ForgeConfigSpec.ConfigValue<Integer> maxSuffixesDisplayed;

		public final ForgeConfigSpec.ConfigValue<Boolean> enableTimestamp;
		public final ForgeConfigSpec.ConfigValue<Boolean> enableFtbEssentials;
		public final ForgeConfigSpec.ConfigValue<Boolean> enableLuckPerms;
		public final ForgeConfigSpec.ConfigValue<Boolean> enableMarkdown;
		public final ForgeConfigSpec.ConfigValue<Boolean> enableColorsCommand;
		public final ForgeConfigSpec.ConfigValue<Boolean> enableTabListIntegration;
		public final ForgeConfigSpec.ConfigValue<Boolean> enableMetadataInTabList;
		public final ForgeConfigSpec.ConfigValue<Boolean> enableNicknamesInTabList;
		public final ForgeConfigSpec.ConfigValue<Boolean> enableWhoisCommand;
		public final ForgeConfigSpec.ConfigValue<Boolean> enableChatNicknameCommand;
		public final ForgeConfigSpec.ConfigValue<Boolean> autoEnableChatNicknameCommand;
		public final ForgeConfigSpec.ConfigValue<Boolean> enableDiscordBotIntegration;
		public final ForgeConfigSpec.ConfigValue<Boolean> enableCustomTabHeaderFooter;
		public final ForgeConfigSpec.ConfigValue<String> tabHeaderFormat;
		public final ForgeConfigSpec.ConfigValue<String> tabFooterFormat;
		public final ForgeConfigSpec.ConfigValue<Boolean> enableAnnouncements;
		public final ForgeConfigSpec.ConfigValue<Integer> announcementIntervalSeconds;
		public final ForgeConfigSpec.ConfigValue<Boolean> announcementUseRandomOrder;
		public final ForgeConfigSpec.ConfigValue<Boolean> enableFilterSystem;
		public final ForgeConfigSpec.ConfigValue<Boolean> enableMessagingSystem;
		public final ForgeConfigSpec.ConfigValue<Boolean> enableChatReplies;
		public final ForgeConfigSpec.ConfigValue<Integer> replySummaryLength;
		public final ForgeConfigSpec.ConfigValue<Boolean> enableHelpOp;
		public final ForgeConfigSpec.ConfigValue<Boolean> enableAdminChat;
		public final ForgeConfigSpec.ConfigValue<Boolean> enableBannedItems;
		public final ForgeConfigSpec.ConfigValue<Boolean> enableBannedBlockScanning;
		public final ForgeConfigSpec.ConfigValue<Integer> bannedBlockScanRadius;
		public final ForgeConfigSpec.ConfigValue<Integer> bannedBlockScanInterval;
		public final ForgeConfigSpec.ConfigValue<String> bannedItemRemovedMsg;
		public final ForgeConfigSpec.ConfigValue<String> bannedAnnounceFormat;
		public final ForgeConfigSpec.ConfigValue<Boolean> enableCountdown;
		public final ForgeConfigSpec.ConfigValue<String> countdownTitleFormat;
		public final ForgeConfigSpec.ConfigValue<String> countdownSubtitleFormat;
		public final ForgeConfigSpec.ConfigValue<String> countdownChatFormat;
		public final ForgeConfigSpec.ConfigValue<Boolean> enableMotdSystem;
		public final ForgeConfigSpec.ConfigValue<Boolean> applyMotdOnStartup;

		// FTB Mute Integration
		public final ForgeConfigSpec.ConfigValue<Boolean> enableFtbMuteIntegration;
		public final ForgeConfigSpec.ConfigValue<String> mutedPlayerMessage;
		public final ForgeConfigSpec.ConfigValue<String> mutedMessageOpFormat;
		public final ForgeConfigSpec.ConfigValue<Boolean> sendMutedMessageToOps;

		// Persistent Mute System
		public final ForgeConfigSpec.ConfigValue<Boolean> enableMuteSystem;
		public final ForgeConfigSpec.ConfigValue<String> muteNotifyPlayerFormat;
		public final ForgeConfigSpec.ConfigValue<String> muteAdminNotifyFormat;
		public final ForgeConfigSpec.ConfigValue<String> unmuteNotifyPlayerFormat;
		public final ForgeConfigSpec.ConfigValue<String> unmuteAdminNotifyFormat;
		public final ForgeConfigSpec.ConfigValue<String> muteConfirmFormat;
		public final ForgeConfigSpec.ConfigValue<String> unmuteConfirmFormat;
		public final ForgeConfigSpec.ConfigValue<String> muteAlreadyMutedMsg;
		public final ForgeConfigSpec.ConfigValue<String> muteNotMutedMsg;
		public final ForgeConfigSpec.ConfigValue<String> muteListHeaderFormat;
		public final ForgeConfigSpec.ConfigValue<String> muteListEntryFormat;
		public final ForgeConfigSpec.ConfigValue<String> muteListEmptyMsg;
		public final ForgeConfigSpec.ConfigValue<String> mutedPlayerChatMsg;
		public final ForgeConfigSpec.ConfigValue<String> mutedPlayerChatMsgWithRemaining;

		// InvSee System
		public final ForgeConfigSpec.ConfigValue<Boolean> enableInvSee;
		public final ForgeConfigSpec.ConfigValue<Boolean> invSeeDisableFtbInvsee;
		public final ForgeConfigSpec.ConfigValue<String> invSeeTitle;
		public final ForgeConfigSpec.ConfigValue<String> invSeeArmorLabel;
		public final ForgeConfigSpec.ConfigValue<String> invSeeOffhandLabel;
		public final ForgeConfigSpec.ConfigValue<String> invSeeCuriosLabel;
		public final ForgeConfigSpec.ConfigValue<String> invSeeMainInvLabel;
		public final ForgeConfigSpec.ConfigValue<String> invSeeNextPageLabel;
		public final ForgeConfigSpec.ConfigValue<String> invSeePrevPageLabel;
		public final ForgeConfigSpec.ConfigValue<Boolean> invSeeReadOnly;

		// Clear Chat System
		public final ForgeConfigSpec.ConfigValue<Boolean> enableClearChat;
		public final ForgeConfigSpec.ConfigValue<Integer> clearChatLineCount;
		public final ForgeConfigSpec.ConfigValue<String> clearChatSuccessMsg;
		public final ForgeConfigSpec.ConfigValue<String> clearChatAllSuccessMsg;
		public final ForgeConfigSpec.ConfigValue<String> clearChatSelfMsg;

		// Sudo System
		public final ForgeConfigSpec.ConfigValue<Boolean> enableSudo;
		public final ForgeConfigSpec.ConfigValue<String> sudoExecutedMsg;
		public final ForgeConfigSpec.ConfigValue<String> sudoNotifyMsg;

		// Inventory Lock System
		public final ForgeConfigSpec.ConfigValue<Boolean> enableInvLock;
		public final ForgeConfigSpec.ConfigValue<String> invLockLockedMsg;
		public final ForgeConfigSpec.ConfigValue<String> invLockUnlockedMsg;
		public final ForgeConfigSpec.ConfigValue<String> invLockAdminLockMsg;
		public final ForgeConfigSpec.ConfigValue<String> invLockAdminUnlockMsg;
		public final ForgeConfigSpec.ConfigValue<String> invLockBlockedMsg;

		// Disable Building System
		public final ForgeConfigSpec.ConfigValue<Boolean> enableDisableBuilding;
		public final ForgeConfigSpec.ConfigValue<String> dbEnabledMsg;
		public final ForgeConfigSpec.ConfigValue<String> dbDisabledMsg;
		public final ForgeConfigSpec.ConfigValue<String> dbPlayerNotifyMsg;
		public final ForgeConfigSpec.ConfigValue<String> dbBlockedMsg;

		// Check Alts System
		public final ForgeConfigSpec.ConfigValue<Boolean> enableCheckAlts;
		public final ForgeConfigSpec.ConfigValue<String> checkAltsHeaderFormat;
		public final ForgeConfigSpec.ConfigValue<String> checkAltsEntryFormat;
		public final ForgeConfigSpec.ConfigValue<String> checkAltsNoAltsMsg;

		// Warn System
		public final ForgeConfigSpec.ConfigValue<Boolean> enableWarnSystem;
		public final ForgeConfigSpec.ConfigValue<String> warnAddedMsg;
		public final ForgeConfigSpec.ConfigValue<String> warnRemovedMsg;
		public final ForgeConfigSpec.ConfigValue<String> warnListHeaderFormat;
		public final ForgeConfigSpec.ConfigValue<String> warnEntryFormat;
		public final ForgeConfigSpec.ConfigValue<String> warnExpiredTag;
		public final ForgeConfigSpec.ConfigValue<String> warnNoWarnsMsg;
		public final ForgeConfigSpec.ConfigValue<String> warnNotifyPlayerMsg;
		public final ForgeConfigSpec.ConfigValue<Boolean> warnPlaySound;

		// Freeze System
		public final ForgeConfigSpec.ConfigValue<Boolean> enableFreezeSystem;
		public final ForgeConfigSpec.ConfigValue<String> freezeMessageToPlayer;
		public final ForgeConfigSpec.ConfigValue<String> freezeReasonFormat;
		public final ForgeConfigSpec.ConfigValue<String> freezeReminderFormat;
		public final ForgeConfigSpec.ConfigValue<Integer> freezeReminderIntervalSeconds;
		public final ForgeConfigSpec.ConfigValue<String> freezeAdminNotifyFormat;
		public final ForgeConfigSpec.ConfigValue<String> unfreezeMessageToPlayer;
		public final ForgeConfigSpec.ConfigValue<String> unfreezeAdminNotifyFormat;
		public final ForgeConfigSpec.ConfigValue<String> freezeCommandBlockedMsg;
		public final ForgeConfigSpec.ConfigValue<String> freezeActionBlockedMsg;
		public final ForgeConfigSpec.ConfigValue<Boolean> freezePlaySound;
		public final ForgeConfigSpec.ConfigValue<Boolean> freezeAllowChat;

		// Message Format Options
		public final ForgeConfigSpec.ConfigValue<String> msgSentFormat;
		public final ForgeConfigSpec.ConfigValue<String> msgReceivedFormat;
		public final ForgeConfigSpec.ConfigValue<String> replyHeaderFormat;
		public final ForgeConfigSpec.ConfigValue<String> replyBodyFormat;
		public final ForgeConfigSpec.ConfigValue<String> helpOpRequestFormat;
		public final ForgeConfigSpec.ConfigValue<String> helpOpReplyFormat;
		public final ForgeConfigSpec.ConfigValue<String> adminChatFormat;
		public final ForgeConfigSpec.ConfigValue<String> announcementConfirmFormat;

		// Sound Options
		public final ForgeConfigSpec.ConfigValue<Boolean> enableMsgSound;
		public final ForgeConfigSpec.ConfigValue<Boolean> enableReplySound;
		public final ForgeConfigSpec.ConfigValue<Boolean> enableHelpOpSound;
		public final ForgeConfigSpec.ConfigValue<Boolean> enableAdminChatSound;

		// Toggle Messages
		public final ForgeConfigSpec.ConfigValue<String> adminChatEnabledMsg;
		public final ForgeConfigSpec.ConfigValue<String> adminChatDisabledMsg;
		public final ForgeConfigSpec.ConfigValue<String> helpOpSentMsg;
		public final ForgeConfigSpec.ConfigValue<String> helpOpReplySentMsg;
		public final ForgeConfigSpec.ConfigValue<String> noReplyTargetMsg;
		public final ForgeConfigSpec.ConfigValue<String> playerOfflineMsg;
		public final ForgeConfigSpec.ConfigValue<String> messageNotFoundMsg;
		public final ForgeConfigSpec.ConfigValue<String> noPermissionMsg;

		// Hover Text Options
		public final ForgeConfigSpec.ConfigValue<String> clickToReplyHover;
		public final ForgeConfigSpec.ConfigValue<String> clickToMessageHover;
		public final ForgeConfigSpec.ConfigValue<String> helpOpReplyHover;

		// Announcement Format Options
		public final ForgeConfigSpec.ConfigValue<String> announcementListHeaderText;
		public final ForgeConfigSpec.ConfigValue<String> announcementListHeaderCmd;
		public final ForgeConfigSpec.ConfigValue<String> toggleListHeader;
		public final ForgeConfigSpec.ConfigValue<String> toggleOnText;
		public final ForgeConfigSpec.ConfigValue<String> toggleOffText;

		public ConfigBuilder(ForgeConfigSpec.Builder builder) {
			builder.push("ServerEssentialsForgeModConfig");
			playerNameFormat = builder
					.comment("  Controls the chat message format",
							 "    $prefix is replaced by the user's prefix or nothing if the user has no prefix",
							 "    $suffix is replaced by the user's suffix or nothing if the user has no suffix",
							 "    $name is replaced by the user's name, or nickname if they have one")
					.define("playerNameFormat", "$prefix$name$suffix");
			chatMessageFormat = builder
					.comment("  Controls the chat message format",
							 "    $time is replaced by the timestamp field or nothing if disabled", 
							 "    $name is replaced by the user's name, or nickname if they have one",
							 "    colors can be uses in the formatting string. for a global message color see next section",
							 "    $msg is replaced by the username's message (if you use it more then once it WILL break this mod)")
					.define("chatMessageFormat", "$time | $name: $msg");
			chatMessageColor = builder
					.comment("  Sets the global color of the chat messages",
							"   Choose one of the following:",
							"    AQUA, RED, LIGHT_PURPLE, YELLOW, WHITE, BLACK, GOLD, GRAY, BLUE, GREEN,",
							"    DARK_GRAY, DARK_AQUA, DARK_RED, DARK_PURPLE, DARK_GREEN, DARK_BLUE")
					.define("chatMessageColor", "WHITE");
			timestampFormat = builder
					.comment("  Timestamp format following the java SimpleDateFormat",
							 "    Read more here: https://docs.oracle.com/javase/7/docs/api/java/text/SimpleDateFormat.html")
					.define("timestampFormat", "HH:mm");
			discordBotToken = builder.comment("  Discord bot token for discord integration").define("discordBotToken", "");
			metaJoinSeparator = builder.comment("  Separator inserted between multiple prefixes/suffixes when combined").define("metaJoinSeparator", " ");
			enableTimestamp = builder.comment("  Enables or disables the filling in of timestamps").define("enableTimestamp", true);
			enableFtbEssentials = builder.comment("  Enables or disables FTB essentials nickname integration").define("useFtbEssentials", true);
			enableLuckPerms = builder.comment("  Enables or disables LuckPerms integration").define("useLuckPerms", true);
			enableMarkdown = builder.comment("  Enables or disables markdown styling").define("markdownEnabled", true);
			enableTabListIntegration = builder.comment("  Enables or disables custom tab list information").define("tabList", true);
			enableMetadataInTabList = builder.comment("  Enables or disables prefixes&suffixes in the tab list").define("tabListMetadata", true);
			enableNicknamesInTabList = builder.comment("  Enables or disables nicknames in the tab list").define("tabListNicknames", true);
			enableColorsCommand = builder.comment("  Enables or disables the /colors command").define("enableColorsCommand", true);
			enableWhoisCommand = builder.comment(
					"  Enables or disables the integrated whois command"
				  + "    (If autoIntegratedNicknames is true, this setting is ignored) ").define("enableWhoisCommand", true);
			enableChatNicknameCommand = builder.comment(
					  "  Enables or disables the integrated nickname command"
					+ "   (If autoIntegratedNicknames is true, this setting is ignored) ").define("enableIntegratedNicknames", false);
			enableDiscordBotIntegration = builder.comment("  Enables or disables discord integration").define("enableDiscordIntegraation", false);
			enableCustomTabHeaderFooter = builder.comment("  Enables custom tab header/footer rendering server-side").define("enableCustomTabHeaderFooter", false);
			tabHeaderFormat = builder.comment("  Tab header format (placeholders: {server_ip}, {online}, {max})").define("tabHeaderFormat", "");
			tabFooterFormat = builder.comment("  Tab footer format (placeholders: {server_ip}, {online}, {max})").define("tabFooterFormat", "");
			autoEnableChatNicknameCommand = builder.comment("  When true, enables the integrated nickname-related commands if FTB essentials is not present").define("autoIntegratedNicknames", true);
			maximumNicknameLength = builder.comment("  Maximum allowed nickname length (for integrated nickname commands)").defineInRange("maximumNicknameLength", 50, 1, 500);
			minimumNicknameLength = builder.comment("  Minimum allowed nickname length (for integrated nickname commands)").defineInRange("minimumNicknameLength", 1, 1, 500);
			maxPrefixesDisplayed = builder.comment("  Maximum number of prefixes to show (weighted order)").defineInRange("maxPrefixesDisplayed", 1, 0, 50);
			maxSuffixesDisplayed = builder.comment("  Maximum number of suffixes to show (weighted order)").defineInRange("maxSuffixesDisplayed", 1, 0, 50);
			enableAnnouncements = builder.comment("  Enable scheduled server announcements and announcement commands").define("enableAnnouncements", true);
			announcementIntervalSeconds = builder.comment("  Interval in seconds between announcements").define("announcementIntervalSeconds", 300);
			announcementUseRandomOrder = builder.comment("  When true, announcements are chosen randomly; otherwise in order").define("announcementUseRandomOrder", false);
			enableFilterSystem = builder.comment("  Enable the word filter system (/sef filter commands)").define("enableFilterSystem", true);
			enableMessagingSystem = builder.comment("  Enable the private messaging system (/msg, /r, /tell, etc)").define("enableMessagingSystem", true);
			enableChatReplies = builder.comment("  Enable the chat reply system (/ans command, clickable messages, chat logging)").define("enableChatReplies", true);
			replySummaryLength = builder.comment("  Maximum length of message summary shown in reply headers (0 = no limit)").defineInRange("replySummaryLength", 50, 0, 500);
			enableHelpOp = builder.comment("  Enable the /helpop command for players to request help from operators").define("enableHelpOp", true);
			enableAdminChat = builder.comment("  Enable the admin chat system (/chat admin)").define("enableAdminChat", true);
			enableBannedItems = builder.comment("  Enable the banned items system (/banned commands)").define("enableBannedItems", true);
			enableBannedBlockScanning = builder.comment("  Enable scanning for banned blocks placed in the world (may affect TPS slightly)").define("enableBannedBlockScanning", true);
			bannedBlockScanRadius = builder.comment("  Default radius around players to scan for banned blocks (smaller = less TPS impact). Override at runtime with /banned setradius").defineInRange("bannedBlockScanRadius", 6, 1, 20);
			bannedBlockScanInterval = builder.comment("  Default interval in ticks between banned-block sweeps. Override at runtime with /banned setinterval. 20 = 1s, 40 = 2s.").defineInRange("bannedBlockScanInterval", 40, 1, 24000);
			bannedItemRemovedMsg = builder.comment(
				"  Message shown to a player when a banned item is confiscated.",
				"  Placeholders: $item, $reason, $by, $remaining.",
				"  Leave empty to use the built-in default."
			).define("bannedItemRemovedMsg", "");
			bannedAnnounceFormat = builder.comment(
				"  Server-wide message format when a banned entry has announce=true and a hit occurs.",
				"  Placeholders: $player, $item, $reason.",
				"  Leave empty to use the built-in default."
			).define("bannedAnnounceFormat", "");
			enableCountdown = builder.comment("  Enable the /countdown command for broadcasting timed countdowns").define("enableCountdown", true);
			countdownTitleFormat = builder.comment(
				"  Format for the title shown to players on each countdown beat.",
				"  Placeholders: $message, $time, $colored_time, $color.",
				"  Leave empty to use the built-in default ($message)."
			).define("countdownTitleFormat", "");
			countdownSubtitleFormat = builder.comment(
				"  Format for the subtitle shown to players on each countdown beat.",
				"  Placeholders: $message, $time, $colored_time, $color.",
				"  Leave empty to use the built-in default ($colored_time)."
			).define("countdownSubtitleFormat", "");
			countdownChatFormat = builder.comment(
				"  Chat-line format used when /countdown's chat_too argument is true.",
				"  Placeholders: $message, $time, $colored_time, $color.",
				"  Leave empty to use the built-in default."
			).define("countdownChatFormat", "");
			enableMotdSystem = builder.comment("  Enable the MOTD system (/sef motd commands)").define("enableMotdSystem", true);
			applyMotdOnStartup = builder.comment("  Automatically apply the configured MOTD when the server starts").define("applyMotdOnStartup", true);

			// FTB Mute Integration
			builder.comment("FTB Essentials Mute Integration",
					"  Requires FTB Essentials to be installed.",
					"  When a player is muted via /mute, their chat messages will be blocked by this mod's chat system.").push("ftbMuteIntegration");
			enableFtbMuteIntegration = builder.comment("  Enable checking FTB Essentials mute status to block muted players from chatting").define("enableFtbMuteIntegration", true);
			mutedPlayerMessage = builder.comment("  Message shown to the muted player when they try to chat").define("mutedPlayerMessage", "&cYou are muted and cannot send messages.");
			sendMutedMessageToOps = builder.comment("  When true, muted messages are relayed to online operators so they can see what the muted player tried to say").define("sendMutedMessageToOps", true);
			mutedMessageOpFormat = builder.comment("  Format for relaying muted messages to operators. Placeholders: $username, $message").define("mutedMessageOpFormat", "&c&lMuted Message &7From $username:&r $message");
			builder.pop(); // ftbMuteIntegration

			// Persistent Mute System
			builder.comment("Persistent Mute System",
					"  /mute and /unmute commands with persistent, tick-based mute tracking.",
					"  Mutes survive player disconnects and server restarts.",
					"  Duration counts in server ticks (stops when server is off).",
					"  Data stored in <world>/serverconfig/sef/mutes.json").push("muteSystem");
			enableMuteSystem = builder.comment("  Enable the persistent /mute and /unmute commands").define("enableMuteSystem", true);
			muteNotifyPlayerFormat = builder.comment("  Message sent to the player when muted. Placeholders: $admin, $reason, $duration")
					.define("muteNotifyPlayerFormat", "&c&l⚠ YOU HAVE BEEN MUTED ⚠\\n&7Reason: &f$reason\\n&7Muted by: &e$admin\\n&7Duration: &e$duration");
			muteAdminNotifyFormat = builder.comment("  Notification sent to admins when a player is muted. Placeholders: $player, $admin, $reason, $duration")
					.define("muteAdminNotifyFormat", "&e$admin &7has muted &e$player &7for &e$duration&7. Reason: &f$reason");
			unmuteNotifyPlayerFormat = builder.comment("  Message sent to the player when unmuted. Placeholder: $admin")
					.define("unmuteNotifyPlayerFormat", "&a&lYou have been unmuted by &e$admin&a&l.");
			unmuteAdminNotifyFormat = builder.comment("  Notification sent to admins when a player is unmuted. Placeholders: $player, $admin")
					.define("unmuteAdminNotifyFormat", "&e$admin &7has unmuted &e$player&7.");
			muteConfirmFormat = builder.comment("  Confirmation message to the admin who muted. Placeholders: $player, $admin, $reason, $duration")
					.define("muteConfirmFormat", "&aMuted &e$player &afor &e$duration&a. Reason: &7$reason");
			unmuteConfirmFormat = builder.comment("  Confirmation message to the admin who unmuted. Placeholders: $player, $admin")
					.define("unmuteConfirmFormat", "&aUnmuted &e$player&a.");
			muteAlreadyMutedMsg = builder.comment("  Message when trying to mute an already muted player. Placeholder: $player")
					.define("muteAlreadyMutedMsg", "&c$player is already muted.");
			muteNotMutedMsg = builder.comment("  Message when trying to unmute a player who isn't muted. Placeholder: $player")
					.define("muteNotMutedMsg", "&c$player is not muted.");
			muteListHeaderFormat = builder.comment("  Header for /mutelist. No placeholders.")
					.define("muteListHeaderFormat", "&6━━━━ Currently Muted Players ━━━━");
			muteListEntryFormat = builder.comment("  Format for each muted player entry. Placeholders: $player, $admin, $reason, $remaining, $duration")
					.define("muteListEntryFormat", "&7- &e$player &7by &e$admin &7| Reason: &f$reason &7| Remaining: &e$remaining &7/ &e$duration");
			muteListEmptyMsg = builder.comment("  Message when no one is currently muted")
					.define("muteListEmptyMsg", "&7No players are currently muted.");
			mutedPlayerChatMsg = builder.comment("  Message shown to a muted player when they try to chat (permanent mute). No placeholders.")
					.define("mutedPlayerChatMsg", "&cYou are muted and cannot send messages.");
			mutedPlayerChatMsgWithRemaining = builder.comment("  Message shown to a muted player when they try to chat (timed mute). Placeholder: $remaining")
					.define("mutedPlayerChatMsgWithRemaining", "&cYou are muted and cannot send messages. &7Time remaining: &e$remaining");
			builder.pop(); // muteSystem

			// InvSee System
			builder.comment("InvSee System",
					"  Custom /invsee command with Curios mod support.",
					"  If FTB Essentials is installed and invSeeDisableFtbInvsee is true,",
					"  this mod's /invsee will override FTB's version.").push("invSee");
			enableInvSee = builder.comment("  Enable the custom /invsee command").define("enableInvSee", true);
			invSeeDisableFtbInvsee = builder.comment("  When true and FTB Essentials is loaded, override FTB's /invsee with ours").define("invSeeDisableFtbInvsee", true);
			invSeeTitle = builder.comment("  Title of the InvSee GUI. Placeholder: $player").define("invSeeTitle", "&e$player's Inventory");
			invSeeArmorLabel = builder.comment("  Name shown on the glass pane separator for armor section").define("invSeeArmorLabel", "&9Armor");
			invSeeOffhandLabel = builder.comment("  Name shown on the glass pane separator for offhand section").define("invSeeOffhandLabel", "&6Offhand");
			invSeeCuriosLabel = builder.comment("  Name shown on the glass pane separator for curios section").define("invSeeCuriosLabel", "&dCurios");
			invSeeMainInvLabel = builder.comment("  Name shown on the glass pane separator for main inventory section").define("invSeeMainInvLabel", "&aInventory");
			invSeeNextPageLabel = builder.comment("  Name shown on the next page arrow item").define("invSeeNextPageLabel", "&eNext Page >>>");
			invSeePrevPageLabel = builder.comment("  Name shown on the previous page arrow item").define("invSeePrevPageLabel", "&e<<< Previous Page");
			invSeeReadOnly = builder.comment("  When true, players cannot move items in the InvSee GUI (view-only mode)").define("invSeeReadOnly", false);
			builder.pop(); // invSee

			// Clear Chat System
			builder.comment("Clear Chat System",
					"  /cc and /clearchat commands to clear player chat.").push("clearChat");
			enableClearChat = builder.comment("  Enable the /cc and /clearchat commands").define("enableClearChat", true);
			clearChatLineCount = builder.comment("  Number of blank lines to send to clear chat").defineInRange("clearChatLineCount", 100, 1, 500);
			clearChatSuccessMsg = builder.comment("  Message shown to admin when clearing a specific player's chat. Placeholder: $player").define("clearChatSuccessMsg", "&aChat cleared for $player.");
			clearChatAllSuccessMsg = builder.comment("  Message shown to admin when clearing all non-OP chats. Placeholder: $admin").define("clearChatAllSuccessMsg", "&aChat cleared for all non-OP players by $admin.");
			clearChatSelfMsg = builder.comment("  Message shown to the player whose chat was cleared").define("clearChatSelfMsg", "&7Your chat has been cleared by an operator.");
			builder.pop(); // clearChat

			// Sudo System
			builder.comment("Sudo System",
					"  /sudo command to force a player to execute a command.").push("sudo");
			enableSudo = builder.comment("  Enable the /sudo command").define("enableSudo", true);
			sudoExecutedMsg = builder.comment("  Message shown to the admin. Placeholders: $player, $command, $admin").define("sudoExecutedMsg", "&aForced $player to execute: &7/$command");
			sudoNotifyMsg = builder.comment("  Message shown to the target player. Placeholders: $admin, $command").define("sudoNotifyMsg", "&c$admin forced you to run: &7/$command");
			builder.pop(); // sudo

			// Inventory Lock System
			builder.comment("Inventory Lock System",
					"  /invlock command to lock/unlock a player's inventory.").push("invLock");
			enableInvLock = builder.comment("  Enable the /invlock command").define("enableInvLock", true);
			invLockLockedMsg = builder.comment("  Message shown to the player when their inventory is locked. Placeholder: $admin").define("invLockLockedMsg", "&c$admin has locked your inventory.");
			invLockUnlockedMsg = builder.comment("  Message shown to the player when their inventory is unlocked. Placeholder: $admin").define("invLockUnlockedMsg", "&a$admin has unlocked your inventory.");
			invLockAdminLockMsg = builder.comment("  Message shown to admin when locking. Placeholder: $player").define("invLockAdminLockMsg", "&eLocked inventory for $player.");
			invLockAdminUnlockMsg = builder.comment("  Message shown to admin when unlocking. Placeholder: $player").define("invLockAdminUnlockMsg", "&eUnlocked inventory for $player.");
			invLockBlockedMsg = builder.comment("  Message shown when a locked player tries to use their inventory").define("invLockBlockedMsg", "&cYour inventory is locked.");
			builder.pop(); // invLock

			// Disable Building System
			builder.comment("Disable Building System",
					"  /disablebuilding command to toggle building restrictions for a player.").push("disableBuilding");
			enableDisableBuilding = builder.comment("  Enable the /disablebuilding and /db commands").define("enableDisableBuilding", true);
			dbEnabledMsg = builder.comment("  Message shown to admin when disabling building. Placeholders: $player, $admin").define("dbEnabledMsg", "&cBuilding disabled for $player by $admin.");
			dbDisabledMsg = builder.comment("  Message shown to admin when re-enabling building. Placeholders: $player, $admin").define("dbDisabledMsg", "&aBuilding re-enabled for $player by $admin.");
			dbPlayerNotifyMsg = builder.comment("  Message shown to the player. Placeholders: $status (disabled/enabled), $admin").define("dbPlayerNotifyMsg", "&cYour building privileges have been $status by $admin.");
			dbBlockedMsg = builder.comment("  Message shown when a player with building disabled tries to build").define("dbBlockedMsg", "&cYou are not allowed to build.");
			builder.pop(); // disableBuilding

			// Check Alts System
			builder.comment("Check Alts System",
					"  /checkalts command to check alternate accounts by IP.").push("checkAlts");
			enableCheckAlts = builder.comment("  Enable the /checkalts command").define("enableCheckAlts", true);
			checkAltsHeaderFormat = builder.comment("  Header for alts list. Placeholders: $player, $ip").define("checkAltsHeaderFormat", "&6━━━━ Alts for $player ($ip) ━━━━");
			checkAltsEntryFormat = builder.comment("  Format for each alt entry. Placeholders: $name, $uuid, $lastseen").define("checkAltsEntryFormat", "&7- &e$name &7($uuid) Last seen: $lastseen");
			checkAltsNoAltsMsg = builder.comment("  Message when no alts found. Placeholder: $player").define("checkAltsNoAltsMsg", "&7No alternate accounts found for $player.");
			builder.pop(); // checkAlts

			// Warn System
			builder.comment("Warn System",
					"  /warn and /warns commands for player warning management.",
					"  Warns persist to JSON and support expiration durations.").push("warnSystem");
			enableWarnSystem = builder.comment("  Enable the warning system (/warn, /warns commands)").define("enableWarnSystem", true);
			warnAddedMsg = builder.comment("  Message to admin when warning added. Placeholders: $player, $reason, $admin, $id, $duration").define("warnAddedMsg", "&aWarning #$id added for $player: &7$reason &e(Duration: $duration)");
			warnRemovedMsg = builder.comment("  Message to admin when warning removed. Placeholders: $player, $id").define("warnRemovedMsg", "&eWarning #$id removed for $player.");
			warnListHeaderFormat = builder.comment("  Header for warnings list. Placeholder: $player").define("warnListHeaderFormat", "&6━━━━ Warnings for $player ━━━━");
			warnEntryFormat = builder.comment("  Format for each warning entry. Placeholders: $id, $reason, $admin, $date, $expired").define("warnEntryFormat", "&7#$id &f$reason &7(by $admin, $date)$expired");
			warnExpiredTag = builder.comment("  Text appended for expired warnings").define("warnExpiredTag", " &c(expired)");
			warnNoWarnsMsg = builder.comment("  Message when player has no warnings. Placeholder: $player").define("warnNoWarnsMsg", "&7$player has no warnings.");
			warnNotifyPlayerMsg = builder.comment("  Message shown to the warned player. Placeholders: $admin, $reason").define("warnNotifyPlayerMsg", "&c⚠ You have been warned by $admin: &f$reason");
			warnPlaySound = builder.comment("  Play a sound when a player is warned").define("warnPlaySound", true);
			builder.pop(); // warnSystem

			// Freeze System
			builder.comment("Freeze System",
					"  /freeze command to lock a player in place.",
					"  Frozen players cannot move, look around, jump, mine, break, place, or use commands.",
					"  They CAN still type in chat to respond to the admin.").push("freezeSystem");
			enableFreezeSystem = builder.comment("  Enable the /freeze command").define("enableFreezeSystem", true);
			freezeMessageToPlayer = builder.comment("  Message sent to the player when they are frozen. Placeholders: $reason, $admin, $duration")
					.define("freezeMessageToPlayer", "&c&l⚠ YOU HAVE BEEN FROZEN ⚠\n&7Reason: &f$reason\n&7Frozen by: &e$admin\n&7Duration: &e$duration\n&7&oPlease respond to the admin in chat.");
			freezeReasonFormat = builder.comment("  Format of the reason displayed. Placeholder: $reason")
					.define("freezeReasonFormat", "&c&lFROZEN &7- &f$reason");
			freezeReminderFormat = builder.comment("  Periodic reminder message to frozen players. Placeholders: $reason, $admin")
					.define("freezeReminderFormat", "&c&l⚠ You are still frozen! &7Reason: &f$reason &7- Please respond in chat.");
			freezeReminderIntervalSeconds = builder.comment("  How often (seconds) to remind frozen players (0 = no reminders)")
					.defineInRange("freezeReminderIntervalSeconds", 15, 0, 3600);
			freezeAdminNotifyFormat = builder.comment("  Notification sent to admins when a player is frozen. Placeholders: $player, $admin, $reason, $duration")
					.define("freezeAdminNotifyFormat", "&e$admin &7has frozen &e$player &7for &e$duration&7. Reason: &f$reason");
			unfreezeMessageToPlayer = builder.comment("  Message sent to the player when unfrozen. Placeholder: $admin")
					.define("unfreezeMessageToPlayer", "&a&lYou have been unfrozen by &e$admin&a&l.");
			unfreezeAdminNotifyFormat = builder.comment("  Notification sent to admins when a player is unfrozen. Placeholders: $player, $admin")
					.define("unfreezeAdminNotifyFormat", "&e$admin &7has unfrozen &e$player&7.");
			freezeCommandBlockedMsg = builder.comment("  Message when a frozen player tries to use a command")
					.define("freezeCommandBlockedMsg", "&cYou are frozen and cannot use commands. Please respond in chat.");
			freezeActionBlockedMsg = builder.comment("  Message when a frozen player tries to interact/mine/etc")
					.define("freezeActionBlockedMsg", "&cYou are frozen and cannot do that.");
			freezePlaySound = builder.comment("  Play a sound when a player is frozen").define("freezePlaySound", true);
			freezeAllowChat = builder.comment("  Allow frozen players to chat (so they can respond to the admin)").define("freezeAllowChat", true);
			builder.pop(); // freezeSystem

			// Message Formats
			builder.comment("Message Format Customization",
					"  Available placeholders vary by message type:",
					"  Private Messages: $sender, $receiver, $message",
					"  Reply System: $replier, $original_sender, $summary, $message",
					"  HelpOp: $sender, $message",
					"  Admin Chat: $sender, $message").push("messageFormats");

			msgSentFormat = builder
					.comment("  Format for outgoing private messages. Placeholders: $sender, $receiver, $message")
					.define("msgSentFormat", "&d&lTo &d$receiver&7: &r&7$message");
			msgReceivedFormat = builder
					.comment("  Format for incoming private messages. Placeholders: $sender, $receiver, $message")
					.define("msgReceivedFormat", "&d&lFrom &d$sender&7: &r&7$message");
			replyHeaderFormat = builder
					.comment("  Format for reply header. Placeholders: $replier, $original_sender, $summary")
					.define("replyHeaderFormat", "    &f&l┌────&r &7Replying to $original_sender&7: &7$summary");
			replyBodyFormat = builder
					.comment("  Format for the reply body line (the replier's message). Placeholders: $replier, $message",
							 "  $replier will include the player's rank/prefix/suffix from LuckPerms if available")
					.define("replyBodyFormat", "$replier&7: &r$message");
			helpOpRequestFormat = builder
					.comment("  Format for HelpOp requests to operators. Placeholders: $sender, $message")
					.define("helpOpRequestFormat", "&l&cHelpOp &fFrom &e$sender&7:&r&7 $message");
			helpOpReplyFormat = builder
					.comment("  Format for HelpOp replies to players. Placeholders: $message")
					.define("helpOpReplyFormat", "&l&cHelpOp &4OP&f Replied&7:&r&7 $message");
			adminChatFormat = builder
					.comment("  Format for Admin Chat messages. Placeholders: $sender, $message")
					.define("adminChatFormat", "&4&lAdmin Chat &e$sender&7:&r $message");
			announcementConfirmFormat = builder
					.comment("  Format for announcement added confirmation. Placeholders: $id, $interval, $message")
					.define("announcementConfirmFormat", "&aAdded announcement: &e$id &7(every $interval)");

			builder.pop(); // messageFormats

			// Sound Options
			builder.comment("Sound Notifications").push("sounds");
			enableMsgSound = builder.comment("  Play sound when receiving a private message").define("enableMsgSound", true);
			enableReplySound = builder.comment("  Play sound when someone replies to your message").define("enableReplySound", true);
			enableHelpOpSound = builder.comment("  Play sound for HelpOp notifications").define("enableHelpOpSound", true);
			enableAdminChatSound = builder.comment("  Play sound for Admin Chat messages").define("enableAdminChatSound", false);
			builder.pop(); // sounds

			// System Messages
			builder.comment("System Messages - Customize all feedback messages").push("systemMessages");
			adminChatEnabledMsg = builder.comment("  Message when admin chat is enabled").define("adminChatEnabledMsg", "&aAdmin chat enabled. &7Your messages will only be seen by operators.");
			adminChatDisabledMsg = builder.comment("  Message when admin chat is disabled").define("adminChatDisabledMsg", "&cAdmin chat disabled. &7You are now in public chat.");
			helpOpSentMsg = builder.comment("  Message when helpop is sent. No placeholders.").define("helpOpSentMsg", "&aMessage sent to all online operators. If there is no one online make a discord ticket.");
			helpOpReplySentMsg = builder.comment("  Message when helpop reply is sent. Placeholder: $player").define("helpOpReplySentMsg", "&aReply sent to $player");
			noReplyTargetMsg = builder.comment("  Message when there's no one to reply to").define("noReplyTargetMsg", "&cNo one to reply to.");
			playerOfflineMsg = builder.comment("  Message when target player is offline").define("playerOfflineMsg", "&cThat player is offline.");
			messageNotFoundMsg = builder.comment("  Message when reply target message not found").define("messageNotFoundMsg", "&cMessage not found or too old to reply to.");
			noPermissionMsg = builder.comment("  Message when player lacks permission").define("noPermissionMsg", "&cYou don't have permission to do that.");
			builder.pop(); // systemMessages

			// Hover Text
			builder.comment("Hover Text - Customize hover tooltips").push("hoverText");
			clickToReplyHover = builder.comment("  Hover text for click to reply. Placeholder: $player").define("clickToReplyHover", "&eClick to reply");
			clickToMessageHover = builder.comment("  Hover text for click to message. Placeholder: $player").define("clickToMessageHover", "&dClick to message $player");
			helpOpReplyHover = builder.comment("  Hover text for HelpOp reply. Placeholder: $player").define("helpOpReplyHover", "&7Click to reply to $player");
			builder.pop(); // hoverText

			// Announcement Formatting
			builder.comment("Announcement System Formatting").push("announcementFormatting");
			announcementListHeaderText = builder.comment("  Header for text announcement list").define("announcementListHeaderText", "&6━━━━━━━━ Text Announcements ━━━━━━━━");
			announcementListHeaderCmd = builder.comment("  Header for command announcement list").define("announcementListHeaderCmd", "&6━━━━━━━━ Command Announcements ━━━━━━━━");
			toggleListHeader = builder.comment("  Header for toggle list").define("toggleListHeader", "&6━━━━━━ Toggleable Announcements ━━━━━━");
			toggleOnText = builder.comment("  Text shown when toggle is ON").define("toggleOnText", "&a[ON]");
			toggleOffText = builder.comment("  Text shown when toggle is OFF").define("toggleOffText", "&c[OFF]");
			builder.pop(); // announcementFormatting

			builder.pop(); // ServerEssentialsForgeModConfig
		}
	}
}
