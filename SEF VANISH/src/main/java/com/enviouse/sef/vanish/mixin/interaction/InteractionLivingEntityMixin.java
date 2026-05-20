package com.enviouse.sef.vanish.mixin.interaction;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.world.entity.LivingEntity;
import com.enviouse.sef.vanish.VanishConfig;
import com.enviouse.sef.vanish.VanishUtil;

@Mixin(LivingEntity.class)
public abstract class InteractionLivingEntityMixin {

	// Prevent vanished players from being pushable
	@WrapOperation(method = "isPushable", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isSpectator()Z"))
	public boolean vanishmod$preventPushing(LivingEntity entity, Operation<Boolean> original) {
		return original.call(entity) || (VanishConfig.CONFIG.preventEntityCollisions.get() && VanishUtil.isVanished(entity));
	}
}

