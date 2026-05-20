 package com.enviouse.sef;

import org.slf4j.Logger;

import com.enviouse.sef.commands.NickCommands;
import com.enviouse.sef.chat.ChatMessageManager;
import com.enviouse.sef.chat.OpBulletinHandler;
import com.enviouse.sef.config.ConfigHandler;
import com.enviouse.sef.config.ConfigurationEventHandler;
import com.enviouse.sef.config.PermissionsHandler;
import com.enviouse.sef.events.ChatEventHandler;
import com.enviouse.sef.events.CommandRegistrationHandler;
import com.enviouse.sef.events.ExternalModLoadingEvent;
import com.enviouse.sef.events.PlayerEventHandler;
import com.enviouse.sef.freeze.FreezeEventHandler;
import com.enviouse.sef.freeze.FreezeManager;
import com.enviouse.sef.mute.MuteManager;
import com.enviouse.sef.utils.SEFUtilities;
import com.enviouse.sef.utils.IMetadataProvider;
import com.enviouse.sef.utils.INicknameProvider;
import com.enviouse.sef.utils.loader;
import com.enviouse.sef.vanish.VanishConfig;
import com.enviouse.sef.vanish.VanishEventListener;
import com.mojang.logging.LogUtils;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

// The value here should match an entry in the META-INF/mods.toml file
@SuppressWarnings("FieldCanBeLocal") // Fields are kept as fields for event bus registration
@Mod(ServerEssentialsForge.MODID)
public class ServerEssentialsForge {
	public static final String CHAT_ID_STR = 
			"&e&lServer&6Essentials&bForge&r &d(c) EnVy 2022-2026&r\n";
	public static final String MODID = "sef";
	public static final String VERSION = "V1.1";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static ServerEssentialsForge instance;

    // Vanish compat detection flags (set in ExternalModLoadingEvent / VanishEventListener)
    public static boolean mc2discordDetected = false;
    public static boolean playtimeDetected = false;
    public static boolean sdlinkDetected = false;

    public IMetadataProvider metadataProvider = null;
    public INicknameProvider nicknameProvider = null;
    
    private final ChatEventHandler chatHandler = new ChatEventHandler();
    private final ExternalModLoadingEvent modLoadingEvent = new ExternalModLoadingEvent();
    private final PlayerEventHandler playerEventHandler = new PlayerEventHandler();
    private final PermissionsHandler permissionsHandler = new PermissionsHandler();
    private final CommandRegistrationHandler commandRegistrator = new CommandRegistrationHandler();

    @SuppressWarnings("removal")
    public ServerEssentialsForge() {
        instance = this;
        ConfigurationEventHandler.registerReloadable(playerEventHandler);
        ConfigurationEventHandler.registerReloadable(chatHandler);
        ConfigurationEventHandler.registerReloadable(() -> {
            NickCommands.reloadConfig();
            SEFUtilities.reloadConfig();
        });
        ConfigurationEventHandler.registerReloadable(() -> ServerEssentialsForge.LOGGER.info("Configuration options loaded!"));

        loader.MlContext(true); // Set mod to ignore whether the other side has it (we only care about the server side - but this works on the client's side too)
        loader.MLConfig("COMMON", ConfigHandler.spec);//set config type and config to register
        loader.register(permissionsHandler); //register permissions handler

        // Register Vanish config as a separate server config file
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, VanishConfig.SERVER_SPEC, "sef-vanish-server.toml");

        // Register Vanish command handler and event listener on the Forge event bus
        MinecraftForge.EVENT_BUS.addListener(this::registerVanishCommands);
        MinecraftForge.EVENT_BUS.addListener(this::registerVanishPermissions);

        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::loadComplete); // Register mod loading completed event
        loader.register(this); // Register the mod itself
        loader.register(commandRegistrator); // Register commands
    }
    private void loadComplete(FMLLoadCompleteEvent e) {
        loader.register(chatHandler);
        loader.register(modLoadingEvent);
        loader.register(playerEventHandler);
    }

    // Vanish command registration
    public void registerVanishCommands(net.minecraftforge.event.RegisterCommandsEvent event) {
        com.enviouse.sef.vanish.VanishCommand.register(event.getDispatcher());
    }

    // Vanish permission registration
    public void registerVanishPermissions(net.minecraftforge.server.permission.events.PermissionGatherEvent.Nodes event) {
        // Vanish see permissions: sef.vanishsee.1, sef.vanishsee.2, sef.vanishsee.3
        for (int level = 1; level <= 3; level++) {
            net.minecraftforge.server.permission.nodes.PermissionNode<Boolean> node = new net.minecraftforge.server.permission.nodes.PermissionNode<>(MODID, "vanishsee." + level, net.minecraftforge.server.permission.nodes.PermissionTypes.BOOLEAN,
                    (player, uuid, context) -> false);
            com.enviouse.sef.vanish.Vanishmod.VANISH_SEE_NODES.put(level, node);
            event.addNodes(node);
        }
        // Vanish level permissions: sef.vanish.1, sef.vanish.2, sef.vanish.3
        for (int level = 1; level <= 3; level++) {
            net.minecraftforge.server.permission.nodes.PermissionNode<Boolean> node = new net.minecraftforge.server.permission.nodes.PermissionNode<>(MODID, "vanish." + level, net.minecraftforge.server.permission.nodes.PermissionTypes.BOOLEAN,
                    (player, uuid, context) -> false);
            com.enviouse.sef.vanish.Vanishmod.VANISH_LEVEL_NODES.put(level, node);
            event.addNodes(node);
        }
    }

    // Server lifecycle hooks
    @SubscribeEvent
    public void onServerStarted(net.minecraftforge.event.server.ServerStartedEvent ev) {
        if(ConfigHandler.config.enableAnnouncements.get()) {
            CommandRegistrationHandler.getAnnouncementManager().load(ev.getServer());
        }
        if(ConfigHandler.config.enableFilterSystem.get()) {
            CommandRegistrationHandler.getFilterManager().load(ev.getServer());
        }
        if(ConfigHandler.config.enableChatReplies.get()) {
            ChatMessageManager.init(ev.getServer());
        }
        // Initialize Op Bulletin
        OpBulletinHandler.init(ev.getServer());
        // Initialize Banned Items system
        if(ConfigHandler.config.enableBannedItems.get()) {
            CommandRegistrationHandler.getBannedItemsManager().load(ev.getServer());
        }
        // Initialize MOTD system (with singleplayer/LAN safety)
        if(ConfigHandler.config.enableMotdSystem.get()) {
            java.nio.file.Path configDir;
            // getServerDirectory() returns null on integrated servers (singleplayer/LAN)
            java.io.File serverDir = ev.getServer().getServerDirectory();
            if(serverDir != null) {
                configDir = serverDir.toPath().resolve("config").resolve("sef");
            } else {
                configDir = FMLPaths.CONFIGDIR.get().resolve("sef");
            }
            CommandRegistrationHandler.initMotdManager(configDir);
            // Apply MOTD on startup if configured (only on dedicated servers)
            if(ConfigHandler.config.applyMotdOnStartup.get() && ev.getServer().isDedicatedServer()) {
                CommandRegistrationHandler.getMotdManager().applyToServer(ev.getServer());
            }
        }
        // Initialize Alt Tracker
        if(ConfigHandler.config.enableCheckAlts.get()) {
            CommandRegistrationHandler.getAltTracker().load(ev.getServer());
        }
        // Initialize Warn System
        if(ConfigHandler.config.enableWarnSystem.get()) {
            CommandRegistrationHandler.getWarnManager().load(ev.getServer());
        }
        // Initialize Persistent Mute System
        if(ConfigHandler.config.enableMuteSystem.get()) {
            CommandRegistrationHandler.getMuteManager().load(ev.getServer());
        }

        // Vanish compat mod detection
        if (net.minecraftforge.fml.ModList.get().isLoaded("mc2discord"))
            mc2discordDetected = true;
        if (net.minecraftforge.fml.ModList.get().isLoaded("playtime"))
            playtimeDetected = true;
        if (net.minecraftforge.fml.ModList.get().isLoaded("sdlink"))
            sdlinkDetected = true;
    }
    @SubscribeEvent
    public void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent ev) {
        if(ev.phase == net.minecraftforge.event.TickEvent.Phase.END) {
            if(ConfigHandler.config.enableAnnouncements.get()) {
                CommandRegistrationHandler.getAnnouncementManager().tick(ev.getServer());
            }
            if(ConfigHandler.config.enableBannedItems.get()) {
                CommandRegistrationHandler.getBannedItemsManager().tick(ev.getServer(), ev.getServer().getTickCount());
            }
            if(ConfigHandler.config.enableFreezeSystem.get()) {
                FreezeManager.tick(ev.getServer());
            }
            if(ConfigHandler.config.enableMuteSystem.get()) {
                CommandRegistrationHandler.getMuteManager().tick(ev.getServer());
            }
            if(ConfigHandler.config.enableCountdown.get()) {
                com.enviouse.sef.countdown.CountdownManager.tick(ev.getServer());
            }
        }
    }
    @SubscribeEvent
    public void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent ev) {
        FreezeManager.clear();
        com.enviouse.sef.invlock.InvLockManager.clear();
        com.enviouse.sef.disablebuilding.DisableBuildingManager.clear();
        // Save mute data on server stop (persist across restarts)
        if(ConfigHandler.config.enableMuteSystem.get()) {
            CommandRegistrationHandler.getMuteManager().shutdown();
        }
        if(ConfigHandler.config.enableBannedItems.get()) {
            CommandRegistrationHandler.getBannedItemsManager().save();
        }
        com.enviouse.sef.countdown.CountdownManager.clear();
        // Clear vanished player list on server stop
        com.enviouse.sef.vanish.VanishUtil.VANISHED_PLAYERS.clear();
    }
}
