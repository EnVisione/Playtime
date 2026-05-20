package com.enviouse.sef.vanish.mixin.world;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.SleepStatus;
import com.enviouse.sef.vanish.VanishConfig;
import com.enviouse.sef.vanish.VanishUtil;

@Mixin(SleepStatus.class)
public class SleepStatusMixin {
	//Fixes that vanished players are taken into account when calculating the amount of players needed for the night to be skipped
	@ModifyVariable(method = "update", at = @At(value = "HEAD"), argsOnly = true)
	public List<ServerPlayer> vanishmod$updatePlayers(List<ServerPlayer> original) {
		if (VanishConfig.CONFIG.hidePlayersFromWorld.get())
			return VanishUtil.removeVanishedFromPlayerList(original, null);

		return original;
	}
}
