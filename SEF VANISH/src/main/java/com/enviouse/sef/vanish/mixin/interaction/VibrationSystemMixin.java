package com.enviouse.sef.vanish.mixin.interaction;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import net.minecraft.world.phys.Vec3;
import com.enviouse.sef.vanish.VanishConfig;
import com.enviouse.sef.vanish.VanishUtil;

@Mixin(VibrationSystem.Listener.class)
public class VibrationSystemMixin {

	@Inject(method = "handleGameEvent", at = @At("HEAD"), cancellable = true)
	private void vanishmod$preventVibrations(ServerLevel level, GameEvent gameEvent, GameEvent.Context context, Vec3 pos, CallbackInfoReturnable<Boolean> cir) {
		Entity sourceEntity = context.sourceEntity();
		if (sourceEntity != null && VanishUtil.isVanished(sourceEntity) && VanishConfig.CONFIG.preventVibrations.get())
			cir.setReturnValue(false);
	}
}

