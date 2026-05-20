package com.enviouse.sef.vanish.mixin.interaction;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import com.enviouse.sef.vanish.VanishConfig;
import com.enviouse.sef.vanish.VanishUtil;

@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {

	// Prevent vanished players from causing chunk generation
	@Inject(method = "skipPlayer", at = @At("RETURN"), cancellable = true)
	public void vanishmod$preventChunkGeneration(ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValue() && VanishConfig.CONFIG.preventChunkLoading.get() && VanishUtil.isVanished(player))
			cir.setReturnValue(true);
	}

	// Prevent mob spawning near vanished players
	@WrapOperation(method = "playerIsCloseEnoughForSpawning", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;isSpectator()Z"))
	public boolean vanishmod$preventMobSpawning(ServerPlayer player, Operation<Boolean> original) {
		return original.call(player) || (VanishConfig.CONFIG.preventMobSpawning.get() && VanishUtil.isVanished(player));
	}
}

