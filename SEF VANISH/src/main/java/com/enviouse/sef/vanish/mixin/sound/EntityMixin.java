package com.enviouse.sef.vanish.mixin.sound;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import com.enviouse.sef.vanish.VanishConfig;
import com.enviouse.sef.vanish.VanishUtil;
import com.enviouse.sef.vanish.misc.SoundSuppressionHelper;

@Mixin(Entity.class)
public class EntityMixin {
	//Invalidates the hit results of a vanished player if its position changes, because then their crosshair is most likely on a different block
	@Inject(method = "move", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;setPos(DDD)V", ordinal = 1))
	private void vanishmod$onActualMove(MoverType type, Vec3 pos, CallbackInfo callbackInfo) {
		if (SoundSuppressionHelper.shouldCapturePlayers() && (Object) this instanceof ServerPlayer player && !player.hasContainerOpen())
			SoundSuppressionHelper.invalidateHitResults(player);
	}

	//Makes a vanished player pretend that they are invisible serverside, so e.g. minimap mods hide those players
	@Inject(method = "isInvisible", at = @At("HEAD"), cancellable = true)
	private void vanishmod$isInvisible(CallbackInfoReturnable<Boolean> callbackInfo) {
		if ((Object) this instanceof ServerPlayer player && VanishConfig.CONFIG.spoofVanishedPlayerInvisibility.get() && VanishUtil.isVanished(player))
			callbackInfo.setReturnValue(true);
	}

	// Hide vanished players and their traceable entities from other players' tracking
	@Inject(method = "broadcastToPlayer", at = @At("HEAD"), cancellable = true)
	private void vanishmod$shouldBroadcast(ServerPlayer observer, CallbackInfoReturnable<Boolean> cir) {
		Entity self = (Entity) (Object) this;
		ServerPlayer actor = null;

		if (self instanceof ServerPlayer player) {
			actor = player;
		} else if (VanishConfig.CONFIG.hideTraceableEntities.get() && self instanceof TraceableEntity traceableEntity && traceableEntity.getOwner() instanceof ServerPlayer owner) {
			actor = owner;
		}

		if (actor != null && VanishUtil.isVanished(actor, observer)) {
			cir.setReturnValue(false);
		}
	}

	// Make vanished players invisible to other players
	@Inject(method = "isInvisibleTo", at = @At("HEAD"), cancellable = true)
	private void vanishmod$markRenderInvisible(Player player, CallbackInfoReturnable<Boolean> cir) {
		Entity self = (Entity) (Object) this;
		if (self instanceof ServerPlayer actor && player instanceof ServerPlayer observer && VanishUtil.isVanished(actor, observer)) {
			cir.setReturnValue(true);
		}
	}

	// Prevent vanished players and their traceable entities from producing sounds
	@Inject(method = "playSound(Lnet/minecraft/sounds/SoundEvent;FF)V", at = @At("HEAD"), cancellable = true)
	private void vanishmod$preventSound(SoundEvent soundEvent, float volume, float pitch, CallbackInfo ci) {
		if (!VanishConfig.CONFIG.preventSounds.get()) return;

		Entity self = (Entity) (Object) this;
		ServerPlayer actor = null;

		if (self instanceof ServerPlayer player) {
			actor = player;
		} else if (self instanceof TraceableEntity traceableEntity && traceableEntity.getOwner() instanceof ServerPlayer owner) {
			actor = owner;
		}

		if (actor != null && VanishUtil.isVanished(actor)) {
			ci.cancel();
		}
	}
}
