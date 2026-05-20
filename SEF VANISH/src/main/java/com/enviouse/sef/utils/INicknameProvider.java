package com.enviouse.sef.utils;

import com.enviouse.sef.ServerEssentialsForge;
import org.checkerframework.checker.nullness.qual.NonNull;

import com.mojang.authlib.GameProfile;

public interface INicknameProvider {
	@NonNull public String getProviderName();

	public String getPlayerNickname(@NonNull GameProfile player);
	@NonNull public default String getPlayerChatName(@NonNull GameProfile player) {
		
		String nick = getPlayerNickname(player);
		if(nick == null || nick.isEmpty()){
			ServerEssentialsForge.LOGGER.debug("no nickname found for: {}",player.getName());
			return player.getName();
		}else{
			ServerEssentialsForge.LOGGER.debug("found nickname: {} for {}",nick,player.getName());
			return nick;
		}
	}
}