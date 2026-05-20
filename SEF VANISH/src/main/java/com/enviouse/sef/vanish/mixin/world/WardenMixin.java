package com.enviouse.sef.vanish.mixin.world;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.warden.Warden;
import com.enviouse.sef.vanish.VanishConfig;
import com.enviouse.sef.vanish.VanishUtil;

@Mixin(Warden.class)
public abstract class WardenMixin {
	@Inject(method = "canTargetEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/monster/warden/Warden;isAlliedTo(Lnet/minecraft/world/entity/Entity;)Z"), cancellable = true)
	public void vanishmod$excludeVanished(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (VanishConfig.CONFIG.hidePlayersFromWorld.get() && VanishUtil.isVanished(entity))
			cir.setReturnValue(false);
	}
}


