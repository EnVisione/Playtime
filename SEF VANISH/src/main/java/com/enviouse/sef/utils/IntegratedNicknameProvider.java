package com.enviouse.sef.utils;

import org.checkerframework.checker.nullness.qual.NonNull;

import com.enviouse.sef.ServerEssentialsForge;
import com.enviouse.sef.config.PlayerData;
import com.mojang.authlib.GameProfile;

public class IntegratedNicknameProvider implements INicknameProvider {
	@Override public String getPlayerNickname(@NonNull GameProfile player) {
		String nick = PlayerData.getNickname(player.getId());
		ServerEssentialsForge.LOGGER.debug("retrieved nickname: {} for {}",nick,player.getName());
		return nick;
	}
	@Override public @NonNull String getProviderName() {
		return "ServerEssentialsForge";
	}
}
