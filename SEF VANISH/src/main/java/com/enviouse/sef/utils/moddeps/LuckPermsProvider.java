package com.enviouse.sef.utils.moddeps;

import org.checkerframework.checker.nullness.qual.NonNull;

import com.enviouse.sef.ServerEssentialsForge;
import com.enviouse.sef.config.ConfigHandler;
import com.enviouse.sef.utils.IMetadataProvider;
import com.mojang.authlib.GameProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.user.User;

public class LuckPermsProvider implements IMetadataProvider {
	private final LuckPerms luckPerms;
	private static LuckPermsProvider instance;

	public LuckPermsProvider() {
		instance = this;
		this.luckPerms = net.luckperms.api.LuckPermsProvider.get();
	}
	public static LuckPermsProvider getInstance() {
		return instance;
	}

	private CachedMetaData getMetaData(GameProfile player) {

		if (this.luckPerms == null) {
			return null;
		} else {
			User user = this.luckPerms.getUserManager().getUser(player.getId());
			return user != null ? user.getCachedData().getMetaData() : null;
		}
	}

	@Override
	public String[] getPlayerPrefixAndSuffix(GameProfile player) {

		try {
			CachedMetaData metaData = this.getMetaData(player);
			if (metaData == null) return null;

			List<String> prefixes = new ArrayList<>();
			List<String> suffixes = new ArrayList<>();

			Map<Integer, String> prefixMap = metaData.getPrefixes();
			Map<Integer, String> suffixMap = metaData.getSuffixes();

			int maxPrefix = ConfigHandler.config.maxPrefixesDisplayed.get();
			int maxSuffix = ConfigHandler.config.maxSuffixesDisplayed.get();

			if(prefixMap != null && !prefixMap.isEmpty()) {
				TreeMap<Integer, String> sorted = new TreeMap<>(prefixMap); // ascending
				for(String val : sorted.descendingMap().values()) {
					if(val != null) prefixes.add(val);
					if(prefixes.size() >= maxPrefix) break;
				}
			} else if(metaData.getPrefix() != null && maxPrefix > 0) {
				prefixes.add(metaData.getPrefix());
			}

			if(suffixMap != null && !suffixMap.isEmpty()) {
				TreeMap<Integer, String> sorted = new TreeMap<>(suffixMap);
				for(String val : sorted.descendingMap().values()) {
					if(val != null) suffixes.add(val);
					if(suffixes.size() >= maxSuffix) break;
				}
			} else if(metaData.getSuffix() != null && maxSuffix > 0) {
				suffixes.add(metaData.getSuffix());
			}

			String prefixJoined = String.join("\n", prefixes);
			String suffixJoined = String.join("\n", suffixes);
			return new String[]{prefixJoined, suffixJoined};
		} catch(IllegalStateException | NullPointerException e) {
			ServerEssentialsForge.LOGGER.warn("Caught Exception: {} /n If {} is a fake player (added by mod) this warning can be ignored",e,player);
			return null;
		}
	}

	public @NonNull@Override
 String getProviderName() {
		return "LuckPerms";
	}
}
