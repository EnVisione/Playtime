package com.enviouse.sef.events;

import com.enviouse.sef.alts.AltTracker;
import com.enviouse.sef.alts.CheckAltsCommand;
import com.enviouse.sef.announcements.AnnouncementManager;
import com.enviouse.sef.announcements.CommandAnnouncementCommand;
import com.enviouse.sef.announcements.TextAnnouncementCommand;
import com.enviouse.sef.announcements.TitleAnnouncementCommand;
import com.enviouse.sef.announcements.ToggleCommand;
import com.enviouse.sef.banned.BannedItemsCommands;
import com.enviouse.sef.banned.BannedItemsManager;
import com.enviouse.sef.chat.AdminChatHandler;
import com.enviouse.sef.chat.ChatReplyHandler;
import com.enviouse.sef.chat.OpBulletinHandler;
import com.enviouse.sef.clearchat.ClearChatCommand;
import com.enviouse.sef.commands.BfcCommands;
import com.enviouse.sef.commands.MsgCommands;
import com.enviouse.sef.config.ConfigHandler;
import com.enviouse.sef.countdown.CountdownCommand;
import com.enviouse.sef.disablebuilding.DisableBuildingCommand;
import com.enviouse.sef.filter.FilterManager;
import com.enviouse.sef.freeze.FreezeCommand;
import com.enviouse.sef.invlock.InvLockCommand;
import com.enviouse.sef.invsee.InvSeeCommand;
import com.enviouse.sef.motd.MotdCommands;
import com.enviouse.sef.motd.MotdManager;
import com.enviouse.sef.mute.MuteCommand;
import com.enviouse.sef.mute.MuteManager;
import com.enviouse.sef.sudo.SudoCommand;
import com.enviouse.sef.warn.WarnCommand;
import com.enviouse.sef.warn.WarnManager;

import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber
public class CommandRegistrationHandler {
    private static final AnnouncementManager ANNOUNCEMENT_MANAGER = new AnnouncementManager();
    private static final FilterManager FILTER_MANAGER = new FilterManager();
    private static final BannedItemsManager BANNED_ITEMS_MANAGER = new BannedItemsManager();
    private static final AltTracker ALT_TRACKER = new AltTracker();
    private static final WarnManager WARN_MANAGER = new WarnManager();
    private static final MuteManager MUTE_MANAGER = new MuteManager();
    private static MotdManager MOTD_MANAGER = null;

    public static AnnouncementManager getAnnouncementManager() { return ANNOUNCEMENT_MANAGER; }
    public static FilterManager getFilterManager() { return FILTER_MANAGER; }
    public static BannedItemsManager getBannedItemsManager() { return BANNED_ITEMS_MANAGER; }
    public static MotdManager getMotdManager() { return MOTD_MANAGER; }
    public static AltTracker getAltTracker() { return ALT_TRACKER; }
    public static WarnManager getWarnManager() { return WARN_MANAGER; }
    public static MuteManager getMuteManager() { return MUTE_MANAGER; }

    public static void initMotdManager(java.nio.file.Path configDir) {
        MOTD_MANAGER = new MotdManager(configDir);
        MOTD_MANAGER.load();
    }

    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent e) {
        // Always register base commands
        BfcCommands.register(e.getDispatcher());

        // Register filter commands if enabled
        if(ConfigHandler.config.enableFilterSystem.get()) {
            BfcCommands.initFilterManager(FILTER_MANAGER);
        }


        // Register announcement commands if enabled
        if(ConfigHandler.config.enableAnnouncements.get()) {
            TextAnnouncementCommand.setManager(ANNOUNCEMENT_MANAGER);
            TextAnnouncementCommand.register(e.getDispatcher(), ANNOUNCEMENT_MANAGER);
            CommandAnnouncementCommand.register(e.getDispatcher(), ANNOUNCEMENT_MANAGER);
            TitleAnnouncementCommand.register(e.getDispatcher());
            ToggleCommand.register(e.getDispatcher(), ANNOUNCEMENT_MANAGER);
        }

        // Register /ans chat reply command if enabled
        if(ConfigHandler.config.enableChatReplies.get()) {
            ChatReplyHandler.register(e.getDispatcher());
        }

        // Register /helpop and /chat admin commands
        AdminChatHandler.register(e.getDispatcher());

        // Register /opbulletin command
        OpBulletinHandler.register(e.getDispatcher());

        // Register /banned commands if enabled
        if(ConfigHandler.config.enableBannedItems.get()) {
            BannedItemsCommands.setManager(BANNED_ITEMS_MANAGER);
            BannedItemsCommands.register(e.getDispatcher());
        }

        // Register MOTD commands - always register, manager will be set later
        if(ConfigHandler.config.enableMotdSystem.get()) {
            MotdCommands.register(e.getDispatcher());
        }

        // Register /freeze and /unfreeze commands if enabled
        if(ConfigHandler.config.enableFreezeSystem.get()) {
            FreezeCommand.register(e.getDispatcher());
        }

        // Register /cc and /clearchat commands if enabled
        if(ConfigHandler.config.enableClearChat.get()) {
            ClearChatCommand.register(e.getDispatcher());
        }

        // Register /sudo command if enabled
        if(ConfigHandler.config.enableSudo.get()) {
            SudoCommand.register(e.getDispatcher());
        }

        // Register /invlock command if enabled
        if(ConfigHandler.config.enableInvLock.get()) {
            InvLockCommand.register(e.getDispatcher());
        }

        // Register /disablebuilding and /db commands if enabled
        if(ConfigHandler.config.enableDisableBuilding.get()) {
            DisableBuildingCommand.register(e.getDispatcher());
        }

        // Register /checkalts command if enabled
        if(ConfigHandler.config.enableCheckAlts.get()) {
            CheckAltsCommand.register(e.getDispatcher());
        }

        // Register /warn and /warns commands if enabled
        if(ConfigHandler.config.enableWarnSystem.get()) {
            WarnCommand.register(e.getDispatcher());
        }

        // Register /mute, /unmute, /mutelist commands if enabled
        if(ConfigHandler.config.enableMuteSystem.get()) {
            MuteCommand.register(e.getDispatcher());
        }

        // Register /countdown command if enabled
        if(ConfigHandler.config.enableCountdown.get()) {
            CountdownCommand.register(e.getDispatcher());
        }
    }

    /**
     * Register commands at LOW priority so they run AFTER vanilla and FTB Essentials'
     * command registration, allowing us to override their command nodes.
     */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOW)
    public void registerLowPriorityCommands(RegisterCommandsEvent e) {
        // Register custom /invsee command if enabled
        if(ConfigHandler.config.enableInvSee.get()) {
            InvSeeCommand.register(e.getDispatcher());
        }
        // Register messaging commands at LOW priority to override vanilla /msg, /tell, /w
        if(ConfigHandler.config.enableMessagingSystem.get()) {
            MsgCommands.register(e.getDispatcher());
        }
    }
}
