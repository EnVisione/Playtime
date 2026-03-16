package com.enviouse.playtime;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * Forge config spec for the Playtime mod.
 * All tuneable values live here; baked into public statics on config load.
 */
@Mod.EventBusSubscriber(modid = Playtime.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // ── Features ──────────────────────────────────────────────────────────────

    private static final ForgeConfigSpec.BooleanValue CLAIMS_ENABLED = BUILDER
            .comment("Enable the claims system (claim limits per rank, /claims command).",
                     "Set to false if your server doesn't use OpenPAC claims.")
            .define("features.claimsEnabled", true);

    private static final ForgeConfigSpec.BooleanValue FORCELOADS_ENABLED = BUILDER
            .comment("Enable the forceload system (forceload limits per rank).",
                     "Set to false if your server doesn't use chunk forceloading.")
            .define("features.forceloadsEnabled", true);

    // ── AFK Detection ──────────────────────────────────────────────────────────

    private static final ForgeConfigSpec.IntValue AFK_TIMEOUT_TICKS = BUILDER
            .comment("Ticks of no sufficient activity before a player is considered AFK.",
                     "Default: 1200 (1 minute at 20 TPS). Player must produce at least",
                     "afkMinSignals distinct signals within this window to stay active.")
            .defineInRange("afk.timeoutTicks", 1200, 200, 72000);

    private static final ForgeConfigSpec.IntValue AFK_CHECK_INTERVAL = BUILDER
            .comment("Ticks between each AFK/activity check.",
                     "Default: 20 (once per second).")
            .defineInRange("afk.checkInterval", 20, 1, 200);

    private static final ForgeConfigSpec.DoubleValue AFK_LOOK_THRESHOLD = BUILDER
            .comment("Minimum camera rotation (degrees) to count as a rotation signal.",
                     "Default: 2.0.")
            .defineInRange("afk.lookThresholdDegrees", 2.0, 0.1, 45.0);

    private static final ForgeConfigSpec.DoubleValue AFK_MOVE_THRESHOLD = BUILDER
            .comment("Minimum distance (blocks) the player must move to count as a position signal.",
                     "Prevents minecart/water drift from counting. Default: 0.15.")
            .defineInRange("afk.moveThresholdBlocks", 0.15, 0.01, 10.0);

    private static final ForgeConfigSpec.IntValue AFK_MIN_SIGNALS = BUILDER
            .comment("Minimum number of distinct activity signal types required within the",
                     "timeout window to prove the player is active.",
                     "Signal types: rotation, position, hotbar, sprint, interaction (block/attack/chat).",
                     "Higher values = harder to AFK-bot. Default: 2. Range: 1-5.")
            .defineInRange("afk.minSignals", 2, 1, 5);

    private static final ForgeConfigSpec.IntValue AFK_NOTIFY_INTERVAL = BUILDER
            .comment("Ticks between repeated AFK notifications to the player.",
                     "Default: 6000 (5 minutes).")
            .defineInRange("afk.notifyInterval", 6000, 600, 72000);

    // ── Saving ─────────────────────────────────────────────────────────────────

    private static final ForgeConfigSpec.IntValue SAVE_INTERVAL_TICKS = BUILDER
            .comment("Ticks between periodic data saves.",
                     "Default: 6000 (5 minutes).")
            .defineInRange("saving.intervalTicks", 6000, 600, 72000);

    private static final ForgeConfigSpec.IntValue FLUSH_INTERVAL_TICKS = BUILDER
            .comment("Ticks between flushing active session time to totals.",
                     "Default: 600 (30 seconds).")
            .defineInRange("saving.flushIntervalTicks", 600, 100, 6000);

    // ── Backups ────────────────────────────────────────────────────────────────

    private static final ForgeConfigSpec.BooleanValue BACKUP_ENABLED = BUILDER
            .comment("Enable rotating backup system.")
            .define("backup.enabled", true);

    private static final ForgeConfigSpec.IntValue BACKUP_CHECK_INTERVAL = BUILDER
            .comment("Ticks between backup eligibility checks.",
                     "Default: 12000 (10 minutes).")
            .defineInRange("backup.checkIntervalTicks", 12000, 1200, 72000);

    // ── Cleanup ────────────────────────────────────────────────────────────────

    private static final ForgeConfigSpec.BooleanValue CLEANUP_ENABLED = BUILDER
            .comment("Enable automatic inactivity-based claim cleanup.")
            .define("cleanup.enabled", true);

    private static final ForgeConfigSpec.IntValue CLEANUP_DELAY_TICKS = BUILDER
            .comment("Ticks after server start before running the first cleanup check.",
                     "Default: 6000 (5 minutes).")
            .defineInRange("cleanup.delayTicks", 6000, 600, 72000);

    // ── Integrations ───────────────────────────────────────────────────────────

    private static final ForgeConfigSpec.BooleanValue LUCKPERMS_ENABLED = BUILDER
            .comment("Enable LuckPerms group sync on rank change.")
            .define("integration.luckpermsEnabled", true);

    private static final ForgeConfigSpec.BooleanValue LUCKPERMS_FORCE_SYNC = BUILDER
            .comment("Run '/lp sync' after rank changes (for tab reload).")
            .define("integration.luckpermsForceSync", true);

    private static final ForgeConfigSpec.BooleanValue LOGIN_RANK_SYNC = BUILDER
            .comment("Sync LuckPerms rank groups on every login.",
                     "Removes ALL rank groups (including legacy KubeJS groups) and sets only the correct one.",
                     "Ensures imported players are properly migrated to the new rank system.")
            .define("integration.loginRankSync", true);

    private static final ForgeConfigSpec.BooleanValue OPAC_ENABLED = BUILDER
            .comment("Enable OpenPAC integration for claim cleanup.")
            .define("integration.opacEnabled", true);

    private static final ForgeConfigSpec.ConfigValue<String> DEFAULT_CLAIM_COLOR = BUILDER
            .comment("Default OPAC claim color hex for first-join players.")
            .define("integration.defaultClaimColorHex", "7F7F7F");

    // ── Rank-up Effects ────────────────────────────────────────────────────────

    private static final ForgeConfigSpec.BooleanValue RUN_COMMAND_ON_RANKUP = BUILDER
            .comment("If true, commands defined in each rank's 'commands' list in ranks.json",
                     "will be executed when a player claims that rank.",
                     "Commands should include the leading /, e.g. \"/give @p diamond\".",
                     "The placeholder @p is replaced with the ranking-up player's name.")
            .define("rankup.runCommandOnRankup", false);

    private static final ForgeConfigSpec.BooleanValue RANKUP_BROADCAST = BUILDER
            .comment("Broadcast rank-up messages to all players.")
            .define("rankup.broadcast", true);

    private static final ForgeConfigSpec.ConfigValue<String> RANKUP_SOUND = BUILDER
            .comment("Sound resource to play on rank-up.")
            .define("rankup.sound", "minecraft:entity.player.levelup");

    private static final ForgeConfigSpec.DoubleValue RANKUP_SOUND_VOLUME = BUILDER
            .comment("Volume of the rank-up sound.")
            .defineInRange("rankup.soundVolume", 1.0, 0.0, 2.0);

    private static final ForgeConfigSpec.DoubleValue RANKUP_SOUND_PITCH = BUILDER
            .comment("Pitch of the rank-up sound.")
            .defineInRange("rankup.soundPitch", 1.2, 0.1, 2.0);

    private static final ForgeConfigSpec.IntValue RANKUP_TITLE_FADEIN = BUILDER
            .comment("Title fade-in ticks.")
            .defineInRange("rankup.titleFadeIn", 10, 0, 100);

    private static final ForgeConfigSpec.IntValue RANKUP_TITLE_STAY = BUILDER
            .comment("Title stay ticks.")
            .defineInRange("rankup.titleStay", 60, 0, 200);

    private static final ForgeConfigSpec.IntValue RANKUP_TITLE_FADEOUT = BUILDER
            .comment("Title fade-out ticks.")
            .defineInRange("rankup.titleFadeOut", 20, 0, 100);

    // ── Commands ───────────────────────────────────────────────────────────────

    private static final ForgeConfigSpec.IntValue ADMIN_PERMISSION_LEVEL = BUILDER
            .comment("Permission level required for /playtimeadmin commands (2 = op).")
            .defineInRange("commands.adminPermissionLevel", 2, 0, 4);

    private static final ForgeConfigSpec.IntValue RANKS_PAGE_SIZE = BUILDER
            .comment("Number of ranks to show per page in /ranks.",
                     "Default: 16.")
            .defineInRange("commands.ranksPageSize", 16, 1, 100);

    private static final ForgeConfigSpec.IntValue TOP_PAGE_SIZE = BUILDER
            .comment("Number of players to show per page in /playtime top.",
                     "Default: 10.")
            .defineInRange("commands.topPageSize", 10, 1, 100);

    // ── First Join ─────────────────────────────────────────────────────────────

    private static final ForgeConfigSpec.BooleanValue FIRST_JOIN_BROADCAST = BUILDER
            .comment("Broadcast a welcome message when a player joins for the first time.")
            .define("firstJoin.broadcast", true);

    // ── Integrated Ranks (fallback when LuckPerms is disabled) ─────────────────

    private static final ForgeConfigSpec.ConfigValue<String> CHAT_MESSAGE_FORMAT = BUILDER
            .comment("Chat message format used by the integrated rank handler when LuckPerms is disabled.",
                     "Placeholders: {rank-display} = formatted rank+name, {msg} = the chat message.",
                     "Default: \"<{rank-display}> {msg}\"")
            .define("integrated-ranks.chatMessageFormat", "<{rank-display}> {msg}");

    private static final ForgeConfigSpec.ConfigValue<String> RANK_DISPLAY_FORMAT = BUILDER
            .comment("How the rank display is formatted within the chat message.",
                     "Placeholders: {rank} = the coloured rank name, {username} = the player's name.",
                     "Default: \"{rank} {username}\"")
            .define("integrated-ranks.rankDisplayFormat", "{rank} {username}");

    private static final ForgeConfigSpec.BooleanValue HEX_FORMATTING_ENABLED = BUILDER
            .comment("Enable hex colour (&#RRGGBB) and gradient parsing in integrated chat.",
                     "Applies to rank colours in the chat message when using the integrated handler.",
                     "Default: true")
            .define("integrated-ranks.hexFormattingEnabled", true);

    // ── Spec ───────────────────────────────────────────────────────────────────

    static final ForgeConfigSpec SPEC = BUILDER.build();

    // ── Baked values ───────────────────────────────────────────────────────────

    public static boolean claimsEnabled;
    public static boolean forceloadsEnabled;

    public static int afkTimeoutTicks;
    public static int afkCheckInterval;
    public static double afkLookThreshold;
    public static double afkMoveThreshold;
    public static int afkMinSignals;
    public static int afkNotifyInterval;

    public static int saveIntervalTicks;
    public static int flushIntervalTicks;

    public static boolean backupEnabled;
    public static int backupCheckIntervalTicks;

    public static boolean cleanupEnabled;
    public static int cleanupDelayTicks;

    public static boolean luckpermsEnabled;
    public static boolean luckpermsForceSync;
    public static boolean loginRankSync;
    public static boolean opacEnabled;
    public static String defaultClaimColorHex;

    public static boolean rankupBroadcast;
    public static boolean runCommandOnRankup;
    public static String rankupSound;
    public static double rankupSoundVolume;
    public static double rankupSoundPitch;
    public static int rankupTitleFadeIn;
    public static int rankupTitleStay;
    public static int rankupTitleFadeOut;

    public static int adminPermissionLevel;
    public static int ranksPageSize;
    public static int topPageSize;
    public static boolean firstJoinBroadcast;

    public static String chatMessageFormat;
    public static String rankDisplayFormat;
    public static boolean hexFormattingEnabled;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        claimsEnabled = CLAIMS_ENABLED.get();
        forceloadsEnabled = FORCELOADS_ENABLED.get();

        afkTimeoutTicks = AFK_TIMEOUT_TICKS.get();
        afkCheckInterval = AFK_CHECK_INTERVAL.get();
        afkLookThreshold = AFK_LOOK_THRESHOLD.get();
        afkMoveThreshold = AFK_MOVE_THRESHOLD.get();
        afkMinSignals = AFK_MIN_SIGNALS.get();
        afkNotifyInterval = AFK_NOTIFY_INTERVAL.get();

        saveIntervalTicks = SAVE_INTERVAL_TICKS.get();
        flushIntervalTicks = FLUSH_INTERVAL_TICKS.get();

        backupEnabled = BACKUP_ENABLED.get();
        backupCheckIntervalTicks = BACKUP_CHECK_INTERVAL.get();

        cleanupEnabled = CLEANUP_ENABLED.get();
        cleanupDelayTicks = CLEANUP_DELAY_TICKS.get();

        luckpermsEnabled = LUCKPERMS_ENABLED.get();
        luckpermsForceSync = LUCKPERMS_FORCE_SYNC.get();
        loginRankSync = LOGIN_RANK_SYNC.get();
        opacEnabled = OPAC_ENABLED.get();
        defaultClaimColorHex = DEFAULT_CLAIM_COLOR.get();

        rankupBroadcast = RANKUP_BROADCAST.get();
        runCommandOnRankup = RUN_COMMAND_ON_RANKUP.get();
        rankupSound = RANKUP_SOUND.get();
        rankupSoundVolume = RANKUP_SOUND_VOLUME.get();
        rankupSoundPitch = RANKUP_SOUND_PITCH.get();
        rankupTitleFadeIn = RANKUP_TITLE_FADEIN.get();
        rankupTitleStay = RANKUP_TITLE_STAY.get();
        rankupTitleFadeOut = RANKUP_TITLE_FADEOUT.get();

        adminPermissionLevel = ADMIN_PERMISSION_LEVEL.get();
        ranksPageSize = RANKS_PAGE_SIZE.get();
        topPageSize = TOP_PAGE_SIZE.get();
        firstJoinBroadcast = FIRST_JOIN_BROADCAST.get();

        chatMessageFormat = CHAT_MESSAGE_FORMAT.get();
        rankDisplayFormat = RANK_DISPLAY_FORMAT.get();
        hexFormattingEnabled = HEX_FORMATTING_ENABLED.get();
    }
}
