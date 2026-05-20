package com.enviouse.sef.vanish.mixin.interaction;

import java.util.List;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import com.enviouse.sef.vanish.VanishConfig;
import com.enviouse.sef.vanish.VanishUtil;

@Mixin(Player.class)
public abstract class InteractionPlayerMixin {

	// Prevent vanished players from picking up entities (arrows, XP, items, tridents)
	@WrapOperation(method = "touch", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;playerTouch(Lnet/minecraft/world/entity/player/Player;)V"))
	private void vanishmod$preventPickup(Entity entity, Player player, Operation<Void> original) {
		if (VanishUtil.isVanished(player) && VanishConfig.CONFIG.preventEntityPickup.get())
			return; // Don't touch/pick up
		original.call(entity, player);
	}

	// Prevent projectiles from hitting vanished players
	@Inject(method = "canBeHitByProjectile", at = @At("HEAD"), cancellable = true)
	public void vanishmod$preventProjectileHits(CallbackInfoReturnable<Boolean> cir) {
		if (VanishUtil.isVanished((Player) (Object) this) && VanishConfig.CONFIG.preventEntityCollisions.get())
			cir.setReturnValue(false);
	}

	// Make vanished players invulnerable
	@Inject(method = "isInvulnerableTo", at = @At("HEAD"), cancellable = true)
	public void vanishmod$makeInvulnerable(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
		if (VanishConfig.CONFIG.makeVanishedInvulnerable.get() && VanishUtil.isVanished((Player) (Object) this))
			cir.setReturnValue(true);
	}

	// Prevent sweeping edge from hitting vanished players
	@WrapOperation(method = "attack", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
	private List<LivingEntity> vanishmod$preventSweepingEdge(Level level, Class<LivingEntity> entityClass, AABB aabb, Operation<List<LivingEntity>> original) {
		if (VanishConfig.CONFIG.preventEntityCollisions.get())
			return level.getEntitiesOfClass(entityClass, aabb, VanishUtil.NO_SPECTATORS_AND_NO_VANISH);
		return original.call(level, entityClass, aabb);
	}
}

