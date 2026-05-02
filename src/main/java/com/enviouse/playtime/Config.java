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
                     "Server signal types (5): rotation, position, hotbar, sprint, interaction.",
                     "Client signal types (7, require Playtime mod on client): keyboard, mouseMove,",
                     "mouseClick, gui, scroll, inventory, windowFocus.",
                     "Higher values = harder to AFK-bot, but values >5 effectively require the mod",
                     "on the client. Default: 3. Range: 1-12.")
            .defineInRange("afk.minSignals", 3, 1, 12);

    private static final ForgeConfigSpec.IntValue AFK_NOTIFY_INTERVAL = BUILDER
            .comment("Ticks between repeated AFK notifications to the player.",
                     "Default: 6000 (5 minutes).")
            .defineInRange("afk.notifyInterval", 6000, 600, 72000);

    // ── AFK Signal Type Toggles ────────────────────────────────────────────
    // Each signal type can be individually enabled/disabled. Disabled signals
    // don't count toward minSignals. Use /playtimeadmin afk check <type> <on|off>
    // for runtime changes.

    private static final ForgeConfigSpec.BooleanValue AFK_SIG_ROTATION = BUILDER
            .comment("Count rotation (yaw/pitch) changes as an AFK signal. Default: true.")
            .define("afk.signal.rotation", true);

    private static final ForgeConfigSpec.BooleanValue AFK_SIG_POSITION = BUILDER
            .comment("Count position (player movement) changes as an AFK signal. Default: true.")
            .define("afk.signal.position", true);

    private static final ForgeConfigSpec.BooleanValue AFK_SIG_HOTBAR = BUILDER
            .comment("Count hotbar slot changes as an AFK signal. Default: true.")
            .define("afk.signal.hotbar", true);

    private static final ForgeConfigSpec.BooleanValue AFK_SIG_SPRINT = BUILDER
            .comment("Count sprint toggle as an AFK signal. Default: true.")
            .define("afk.signal.sprint", true);

    private static final ForgeConfigSpec.BooleanValue AFK_SIG_INTERACTION = BUILDER
            .comment("Count world interactions (block break/place, attack, use, chat) as a signal.",
                     "Default: true.")
            .define("afk.signal.interaction", true);

    private static final ForgeConfigSpec.BooleanValue AFK_SIG_KEYBOARD = BUILDER
            .comment("Count raw keyboard input (any key press) as a signal. Requires Playtime mod",
                     "on the client. Catches macros that move without pressing real keys.",
                     "Default: true.")
            .define("afk.signal.keyboard", true);

    private static final ForgeConfigSpec.BooleanValue AFK_SIG_MOUSE_MOVE = BUILDER
            .comment("Count raw mouse movement (cursor delta) as a signal. Requires Playtime mod",
                     "on the client. More sensitive than server-side rotation sampling.",
                     "Default: true.")
            .define("afk.signal.mouseMove", true);

    private static final ForgeConfigSpec.BooleanValue AFK_SIG_MOUSE_CLICK = BUILDER
            .comment("Count raw mouse button presses as a signal. Requires Playtime mod on client.",
                     "Catches GUI clicks the server doesn't see. Default: true.")
            .define("afk.signal.mouseClick", true);

    private static final ForgeConfigSpec.BooleanValue AFK_SIG_GUI = BUILDER
            .comment("Count GUI screen open events as a signal. Requires Playtime mod on client.",
                     "Default: true.")
            .define("afk.signal.gui", true);

    private static final ForgeConfigSpec.BooleanValue AFK_SIG_SCROLL = BUILDER
            .comment("Count mouse scroll events as a signal. Requires Playtime mod on client.",
                     "Default: true.")
            .define("afk.signal.scroll", true);

    private static final ForgeConfigSpec.BooleanValue AFK_SIG_INVENTORY = BUILDER
            .comment("Count inventory slot changes (item drag/move) as a signal. Requires Playtime",
                     "mod on client. Default: true.")
            .define("afk.signal.inventory", true);

    private static final ForgeConfigSpec.BooleanValue AFK_SIG_WINDOW_FOCUS = BUILDER
            .comment("Count Minecraft window focus as a signal. Requires Playtime mod on client.",
                     "Counts only when the window is the OS-foreground window. Default: true.")
            .define("afk.signal.windowFocus", true);

    // ── Client-side AFK signal options ─────────────────────────────────────

    private static final ForgeConfigSpec.BooleanValue AFK_CLIENT_REQUIRE_FOCUS = BUILDER
            .comment("If true, client-side signals (keyboard/mouse/etc.) only count when the",
                     "Minecraft window is OS-focused. Defeats jiggler scripts that act on a",
                     "minimised game. Default: true.")
            .define("afk.client.requireFocus", true);

    private static final ForgeConfigSpec.IntValue AFK_CLIENT_RATE_LIMIT = BUILDER
            .comment("Maximum client signal packets accepted per player per second. Excess packets",
                     "are dropped silently. Default: 5.")
            .defineInRange("afk.client.rateLimitPerSecond", 5, 1, 60);

    private static final ForgeConfigSpec.IntValue AFK_CLIENT_MIN_UNIQUE_KEYS = BUILDER
            .comment("Minimum number of distinct keys that must be pressed within an AFK timeout",
                     "window for keyboard signal to count toward heuristics. Defeats one-key macros.",
                     "Default: 2.")
            .defineInRange("afk.client.minUniqueKeys", 2, 1, 20);

    // ── Anti-spoof correlation ─────────────────────────────────────────────

    private static final ForgeConfigSpec.BooleanValue AFK_ANTISPOOF_ENABLED = BUILDER
            .comment("Enable client/server signal correlation. If client claims activity but",
                     "server sees no rotation/position changes for a sustained period, the",
                     "player is flagged as suspect and client signals stop counting.",
                     "Default: true.")
            .define("afk.antispoof.enabled", true);

    private static final ForgeConfigSpec.IntValue AFK_ANTISPOOF_THRESHOLD = BUILDER
            .comment("Number of consecutive 'client active, server silent' samples required",
                     "before the player is treated as spoofing. Default: 30 (~30 seconds at",
                     "the default 1-second AFK check interval).")
            .defineInRange("afk.antispoof.threshold", 30, 5, 600);

    // ── AFK Heuristic Analysis ──────────────────────────────────────────────

    private static final ForgeConfigSpec.BooleanValue AFK_HEURISTICS_ENABLED = BUILDER
            .comment("Enable advanced heuristic AFK analysis.",
                     "When enabled, even players producing enough basic signals (rotation, position, etc.)",
                     "will be flagged AFK if their patterns look robotic (AFK pools, mouse macros, etc.).",
                     "Default: true.")
            .define("afk.heuristics.enabled", true);

    private static final ForgeConfigSpec.IntValue AFK_HEURISTIC_WINDOW = BUILDER
            .comment("Number of samples in the rolling analysis window.",
                     "Each sample is taken once per afk.checkInterval ticks.",
                     "Default: 120 (= 2 minutes at 1-second check interval).")
            .defineInRange("afk.heuristics.windowSize", 120, 30, 600);

    private static final ForgeConfigSpec.IntValue AFK_HEURISTIC_MIN_SAMPLES = BUILDER
            .comment("Minimum number of samples before heuristic analysis kicks in.",
                     "Players won't be flagged until this many samples are collected.",
                     "Default: 20 (~20 seconds).")
            .defineInRange("afk.heuristics.minSamples", 20, 5, 120);

    private static final ForgeConfigSpec.DoubleValue AFK_HEURISTIC_THRESHOLD = BUILDER
            .comment("Composite suspicion score threshold (0.0–1.0) above which a player",
                     "is overridden to AFK even if basic signals are present.",
                     "Lower = more aggressive detection, higher = more lenient.",
                     "Default: 0.55.")
            .defineInRange("afk.heuristics.threshold", 0.55, 0.1, 1.0);

    private static final ForgeConfigSpec.DoubleValue AFK_WEIGHT_MOVEMENT = BUILDER
            .comment("Weight of the movement pattern analyzer in the composite score.",
                     "Detects AFK pools, circle-walking, rail loops.",
                     "Default: 1.0.")
            .defineInRange("afk.heuristics.weightMovement", 1.0, 0.0, 5.0);

    private static final ForgeConfigSpec.DoubleValue AFK_WEIGHT_CAMERA = BUILDER
            .comment("Weight of the camera/rotation analyzer in the composite score.",
                     "Detects mouse macro wiggling, sine-wave sweeps.",
                     "Default: 1.0.")
            .defineInRange("afk.heuristics.weightCamera", 1.0, 0.0, 5.0);

    private static final ForgeConfigSpec.DoubleValue AFK_WEIGHT_INTERACTION = BUILDER
            .comment("Weight of the interaction diversity analyzer in the composite score.",
                     "Detects auto-clickers, repetitive same-block interactions.",
                     "Default: 0.8.")
            .defineInRange("afk.heuristics.weightInteraction", 0.8, 0.0, 5.0);

    private static final ForgeConfigSpec.DoubleValue AFK_WEIGHT_TIMING = BUILDER
            .comment("Weight of the timing pattern analyzer in the composite score.",
                     "Detects robotic timing regularity in activity events.",
                     "Default: 0.7.")
            .defineInRange("afk.heuristics.weightTiming", 0.7, 0.0, 5.0);

    private static final ForgeConfigSpec.IntValue AFK_HEURISTIC_EVAL_INTERVAL = BUILDER
            .comment("How often (in AFK check cycles) to run the full heuristic evaluation.",
                     "Samples are always collected, but the expensive analysis runs every N cycles.",
                     "Default: 5 (= every 5 seconds at 1-second check interval).",
                     "Lower = more responsive but uses more CPU. Higher = less CPU.")
            .defineInRange("afk.heuristics.evalInterval", 5, 1, 30);

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

    private static final ForgeConfigSpec.ConfigValue<String> DISPLAY_RANK_MINIMUM_ID = BUILDER
            .comment("Minimum rank ID required to use the /playtime displayrank command.",
                     "Players below this rank cannot set a custom display rank.",
                     "Set to \"\" to allow all ranks. Default: \"technician\"")
            .define("commands.displayRankMinimumId", "technician");

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

    private static final ForgeConfigSpec.ConfigValue<String> CHAT_MESSAGE_FORMAT_NO_RANK = BUILDER
            .comment("Chat message format for players whose rank is BELOW the displayRankMinimum.",
                     "Placeholders: {username} = the player's name, {msg} = the chat message.",
                     "Only used when displayRankMinimum is set. Default: \"<{username}> {msg}\"")
            .define("integrated-ranks.chatMessageFormatNoRank", "<{username}> {msg}");

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

    private static final ForgeConfigSpec.ConfigValue<String> DISPLAY_RANK_MINIMUM = BUILDER
            .comment("The minimum rank ID required for a player's rank to be displayed in chat.",
                     "Players below this rank will use the chatMessageFormatNoRank format instead.",
                     "Set to \"\" (empty) to always show rank for all players.",
                     "Example: \"technician\" means only Technician and above show rank prefix.",
                     "Default: \"\" (show rank for everyone)")
            .define("integrated-ranks.displayRankMinimum", "");

    // ── Integrated Ranks: Style Thresholds ─────────────────────────────────────
    //    Each style can have a minimum rank. If the player's rank is at or above
    //    the threshold, that style is applied to their rank display in chat.
    //    Set to "" to disable the style, or to a rank ID to enable from that rank up.

    private static final ForgeConfigSpec.ConfigValue<String> STYLE_BOLD_MINIMUM = BUILDER
            .comment("Minimum rank ID for BOLD styling on the rank display in chat.",
                     "Players at or above this rank get their rank name shown in bold.",
                     "Set to \"\" to disable (bold is already baked into phase-final rank colours).",
                     "Default: \"\" (disabled — bold comes from rank fallbackColor instead)")
            .define("integrated-ranks.style.boldMinimumRank", "");

    private static final ForgeConfigSpec.ConfigValue<String> STYLE_UNDERLINE_MINIMUM = BUILDER
            .comment("Minimum rank ID for UNDERLINE styling on the rank display in chat.",
                     "Set to \"\" to disable underline for all ranks.",
                     "Default: \"\" (disabled)")
            .define("integrated-ranks.style.underlineMinimumRank", "");

    private static final ForgeConfigSpec.ConfigValue<String> STYLE_ITALIC_MINIMUM = BUILDER
            .comment("Minimum rank ID for ITALIC styling on the rank display in chat.",
                     "Set to \"\" to disable italic for all ranks.",
                     "Default: \"\" (disabled)")
            .define("integrated-ranks.style.italicMinimumRank", "");

    private static final ForgeConfigSpec.ConfigValue<String> STYLE_STRIKETHROUGH_MINIMUM = BUILDER
            .comment("Minimum rank ID for STRIKETHROUGH styling on the rank display in chat.",
                     "Set to \"\" to disable strikethrough for all ranks.",
                     "Default: \"\" (disabled)")
            .define("integrated-ranks.style.strikethroughMinimumRank", "");

    private static final ForgeConfigSpec.ConfigValue<String> STYLE_OBFUSCATED_MINIMUM = BUILDER
            .comment("Minimum rank ID for OBFUSCATED (magic text) styling on the rank display.",
                     "Set to \"\" to disable obfuscated for all ranks.",
                     "Default: \"\" (disabled)")
            .define("integrated-ranks.style.obfuscatedMinimumRank", "");

    private static final ForgeConfigSpec.BooleanValue STYLE_APPLY_TO_USERNAME = BUILDER
            .comment("If true, threshold-based styles (bold/underline/etc.) are also applied",
                     "to the player's username, not just the rank name.",
                     "Default: false")
            .define("integrated-ranks.style.applyToUsername", false);

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

    public static boolean afkSigRotation;
    public static boolean afkSigPosition;
    public static boolean afkSigHotbar;
    public static boolean afkSigSprint;
    public static boolean afkSigInteraction;
    public static boolean afkSigKeyboard;
    public static boolean afkSigMouseMove;
    public static boolean afkSigMouseClick;
    public static boolean afkSigGui;
    public static boolean afkSigScroll;
    public static boolean afkSigInventory;
    public static boolean afkSigWindowFocus;

    public static boolean afkClientRequireFocus;
    public static int afkClientRateLimitPerSecond;
    public static int afkClientMinUniqueKeys;

    public static boolean afkAntispoofEnabled;
    public static int afkAntispoofThreshold;

    public static boolean afkHeuristicsEnabled;
    public static int afkHeuristicWindow;
    public static int afkHeuristicMinSamples;
    public static float afkHeuristicThreshold;
    public static float afkWeightMovement;
    public static float afkWeightCamera;
    public static float afkWeightInteraction;
    public static float afkWeightTiming;
    public static int afkHeuristicEvalInterval;

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
    public static String displayRankMinimumId;
    public static boolean firstJoinBroadcast;

    public static String chatMessageFormat;
    public static String chatMessageFormatNoRank;
    public static String rankDisplayFormat;
    public static boolean hexFormattingEnabled;
    public static String displayRankMinimum;

    public static String styleBoldMinimumRank;
    public static String styleUnderlineMinimumRank;
    public static String styleItalicMinimumRank;
    public static String styleStrikethroughMinimumRank;
    public static String styleObfuscatedMinimumRank;
    public static boolean styleApplyToUsername;

    // ── Runtime setters (used by /playtimeadmin afk subcommands) ──────────
    // Each setter updates both the baked static (so it takes effect immediately)
    // AND the spec value (so it persists to playtime-common.toml on next save).

    public static void setAfkMinSignals(int n) {
        int clamped = Math.max(1, Math.min(12, n));
        AFK_MIN_SIGNALS.set(clamped);
        afkMinSignals = clamped;
    }

    public static void setAfkHeuristicsEnabled(boolean enabled) {
        AFK_HEURISTICS_ENABLED.set(enabled);
        afkHeuristicsEnabled = enabled;
    }

    public static void setAfkHeuristicThreshold(double v) {
        double clamped = Math.max(0.1, Math.min(1.0, v));
        AFK_HEURISTIC_THRESHOLD.set(clamped);
        afkHeuristicThreshold = (float) clamped;
    }

    /**
     * Toggle a single signal-type check by name. Returns true if the type was
     * recognised and applied, false otherwise. Names are case-insensitive.
     */
    public static boolean setSignalEnabled(String type, boolean enabled) {
        switch (type.toLowerCase()) {
            case "rotation":    AFK_SIG_ROTATION.set(enabled);    afkSigRotation = enabled; return true;
            case "position":    AFK_SIG_POSITION.set(enabled);    afkSigPosition = enabled; return true;
            case "hotbar":      AFK_SIG_HOTBAR.set(enabled);      afkSigHotbar = enabled; return true;
            case "sprint":      AFK_SIG_SPRINT.set(enabled);      afkSigSprint = enabled; return true;
            case "interaction": AFK_SIG_INTERACTION.set(enabled); afkSigInteraction = enabled; return true;
            case "keyboard":    AFK_SIG_KEYBOARD.set(enabled);    afkSigKeyboard = enabled; return true;
            case "mousemove":   AFK_SIG_MOUSE_MOVE.set(enabled);  afkSigMouseMove = enabled; return true;
            case "mouseclick":  AFK_SIG_MOUSE_CLICK.set(enabled); afkSigMouseClick = enabled; return true;
            case "gui":         AFK_SIG_GUI.set(enabled);         afkSigGui = enabled; return true;
            case "scroll":      AFK_SIG_SCROLL.set(enabled);      afkSigScroll = enabled; return true;
            case "inventory":   AFK_SIG_INVENTORY.set(enabled);   afkSigInventory = enabled; return true;
            case "windowfocus": AFK_SIG_WINDOW_FOCUS.set(enabled); afkSigWindowFocus = enabled; return true;
            default: return false;
        }
    }

    /** Names of all toggleable signal types — used for admin tab-complete and `show`. */
    public static final String[] SIGNAL_TYPES = {
            "rotation", "position", "hotbar", "sprint", "interaction",
            "keyboard", "mouseMove", "mouseClick", "gui", "scroll", "inventory", "windowFocus"
    };

    /** Read the current enabled state for a signal type by name. Returns false if unknown. */
    public static boolean isSignalEnabled(String type) {
        switch (type.toLowerCase()) {
            case "rotation":    return afkSigRotation;
            case "position":    return afkSigPosition;
            case "hotbar":      return afkSigHotbar;
            case "sprint":      return afkSigSprint;
            case "interaction": return afkSigInteraction;
            case "keyboard":    return afkSigKeyboard;
            case "mousemove":   return afkSigMouseMove;
            case "mouseclick":  return afkSigMouseClick;
            case "gui":         return afkSigGui;
            case "scroll":      return afkSigScroll;
            case "inventory":   return afkSigInventory;
            case "windowfocus": return afkSigWindowFocus;
            default: return false;
        }
    }

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

        afkSigRotation    = AFK_SIG_ROTATION.get();
        afkSigPosition    = AFK_SIG_POSITION.get();
        afkSigHotbar      = AFK_SIG_HOTBAR.get();
        afkSigSprint      = AFK_SIG_SPRINT.get();
        afkSigInteraction = AFK_SIG_INTERACTION.get();
        afkSigKeyboard    = AFK_SIG_KEYBOARD.get();
        afkSigMouseMove   = AFK_SIG_MOUSE_MOVE.get();
        afkSigMouseClick  = AFK_SIG_MOUSE_CLICK.get();
        afkSigGui         = AFK_SIG_GUI.get();
        afkSigScroll      = AFK_SIG_SCROLL.get();
        afkSigInventory   = AFK_SIG_INVENTORY.get();
        afkSigWindowFocus = AFK_SIG_WINDOW_FOCUS.get();

        afkClientRequireFocus       = AFK_CLIENT_REQUIRE_FOCUS.get();
        afkClientRateLimitPerSecond = AFK_CLIENT_RATE_LIMIT.get();
        afkClientMinUniqueKeys      = AFK_CLIENT_MIN_UNIQUE_KEYS.get();

        afkAntispoofEnabled  = AFK_ANTISPOOF_ENABLED.get();
        afkAntispoofThreshold = AFK_ANTISPOOF_THRESHOLD.get();

        afkHeuristicsEnabled = AFK_HEURISTICS_ENABLED.get();
        afkHeuristicWindow = AFK_HEURISTIC_WINDOW.get();
        afkHeuristicMinSamples = AFK_HEURISTIC_MIN_SAMPLES.get();
        afkHeuristicThreshold = AFK_HEURISTIC_THRESHOLD.get().floatValue();
        afkWeightMovement = AFK_WEIGHT_MOVEMENT.get().floatValue();
        afkWeightCamera = AFK_WEIGHT_CAMERA.get().floatValue();
        afkWeightInteraction = AFK_WEIGHT_INTERACTION.get().floatValue();
        afkWeightTiming = AFK_WEIGHT_TIMING.get().floatValue();
        afkHeuristicEvalInterval = AFK_HEURISTIC_EVAL_INTERVAL.get();

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
        displayRankMinimumId = DISPLAY_RANK_MINIMUM_ID.get();
        firstJoinBroadcast = FIRST_JOIN_BROADCAST.get();

        chatMessageFormat = CHAT_MESSAGE_FORMAT.get();
        chatMessageFormatNoRank = CHAT_MESSAGE_FORMAT_NO_RANK.get();
        rankDisplayFormat = RANK_DISPLAY_FORMAT.get();
        hexFormattingEnabled = HEX_FORMATTING_ENABLED.get();
        displayRankMinimum = DISPLAY_RANK_MINIMUM.get();

        styleBoldMinimumRank = STYLE_BOLD_MINIMUM.get();
        styleUnderlineMinimumRank = STYLE_UNDERLINE_MINIMUM.get();
        styleItalicMinimumRank = STYLE_ITALIC_MINIMUM.get();
        styleStrikethroughMinimumRank = STYLE_STRIKETHROUGH_MINIMUM.get();
        styleObfuscatedMinimumRank = STYLE_OBFUSCATED_MINIMUM.get();
        styleApplyToUsername = STYLE_APPLY_TO_USERNAME.get();
    }
}
