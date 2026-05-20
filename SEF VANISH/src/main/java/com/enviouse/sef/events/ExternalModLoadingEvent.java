package com.enviouse.sef.events;

import com.enviouse.sef.ServerEssentialsForge;
import com.enviouse.sef.commands.NickCommands;
import com.enviouse.sef.config.ConfigHandler;
import com.enviouse.sef.utils.IntegratedNicknameProvider;
import com.enviouse.sef.utils.moddeps.FTBNicknameProvider;
import com.enviouse.sef.utils.moddeps.LuckPermsProvider;

import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber
public class ExternalModLoadingEvent {
	@SubscribeEvent public void onServerStarted(ServerStartedEvent e) {
		loadLuckPerms();
		loadFtbEssentials();
		loadIntegratedNicknameProvider();
	}

	private void loadIntegratedNicknameProvider() {
		if (ServerEssentialsForge.instance.nicknameProvider == null &&
				ConfigHandler.config.autoEnableChatNicknameCommand.get()) {
			ServerEssentialsForge.instance.nicknameProvider = new IntegratedNicknameProvider();
			NickCommands.nicknameIntegrationEnabled = true;
			ServerEssentialsForge.LOGGER.info("Integrated nickname management enabled successfully!");
		}
	}

	private void loadLuckPerms() {
			ServerEssentialsForge.LOGGER.info("Detected loaded status of luckperms: " + ModList.get().isLoaded("luckperms"));
			if (ModList.get().isLoaded("luckperms")) {
				if (!ConfigHandler.config.enableLuckPerms.get()) {
					ServerEssentialsForge.LOGGER.info("LuckPerms API was skipped by configuration file!");
					return;
				}
				ServerEssentialsForge.LOGGER.info("Attempting to integrate LuckPerms API!");
				try {
					ServerEssentialsForge.instance.metadataProvider = new LuckPermsProvider();
					ServerEssentialsForge.LOGGER.info("LuckPerms API found and integrated successfully!");
				} catch (Exception e) { // Could have a NoClassDefFoundError here!
					ServerEssentialsForge.instance.metadataProvider = null;
					ServerEssentialsForge.LOGGER.warn("Something went wrong — LuckPerms was reported loaded but its API threw; prefix/suffix will not be shown.\nIf you see this warning please submit an issue report.");
				}
			} else {
				ServerEssentialsForge.instance.metadataProvider = null;
				ServerEssentialsForge.LOGGER.info("LuckPerms was not found; prefix/suffix metadata will not be shown.");
			}
	}

	private void loadFtbEssentials() {
		ServerEssentialsForge.LOGGER.info("Detected loaded status of FTB Essentials: " + ModList.get().isLoaded("ftbessentials"));
		if (ModList.get().isLoaded("ftbessentials")) {
			if (!ConfigHandler.config.enableFtbEssentials.get()) {
				ServerEssentialsForge.LOGGER.info("FTB Essentials integration was skipped by configuration file!");
				return;
			}
			ServerEssentialsForge.LOGGER.info("Attempting to integrate FTB Essentials!");
			try {
				ServerEssentialsForge.instance.nicknameProvider = new FTBNicknameProvider();
				ServerEssentialsForge.LOGGER.info("FTB Essentials API found and integrated successfully!");
			} catch (Error e2) { // Could have a NoClassDefFoundError here!
				ServerEssentialsForge.instance.nicknameProvider = null;
				ServerEssentialsForge.LOGGER.warn("Something went wrong — FTB Essentials was reported loaded but threw; nicknames will not be shown.\nIf you see this warning please submit an issue report.");
			}
		} else {
			ServerEssentialsForge.instance.nicknameProvider = null;
			ServerEssentialsForge.LOGGER.info("FTB Essentials was not found; integrated nickname provider will be used (if enabled in config).");
		}
	}
}
