package com.enviouse.sef.vanish.mixin.world;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import com.enviouse.sef.vanish.VanishUtil;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {

	@Shadow
	@Nullable
	public abstract Entity getEntity(int id);

	// Hide block breaking progress from players who can't see the vanished breaker
	@WrapOperation(method = "destroyBlockProgress", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V"))
	public void vanishmod$hideBlockDestroyProgress(ServerGamePacketListenerImpl packetListener, Packet<?> packet, Operation<Void> original) {
		if (packet instanceof ClientboundBlockDestructionPacket destructionPacket) {
			Entity entity = this.getEntity(destructionPacket.getId());
			if (entity instanceof ServerPlayer breaker && VanishUtil.isVanished(breaker, packetListener.player)) {
				return; // Don't send the packet
			}
		}
		original.call(packetListener, packet);
	}

	// ...existing code...
	@Inject(method = "tickNonPassenger", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;tick()V"))
	public void vanishmod$beforeEntityTick(Entity entity, CallbackInfo ci) {
		VanishUtil.ACTIVE_ENTITY.set(entity);
	}

	@Inject(method = "tickNonPassenger", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;tick()V", shift = At.Shift.AFTER))
	public void vanishmod$afterEntityTick(Entity entity, CallbackInfo ci) {
		VanishUtil.ACTIVE_ENTITY.remove();
	}

	// Track active entity during tickPassenger
	@Inject(method = "tickPassenger", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;rideTick()V"))
	public void vanishmod$beforePassengerTick(Entity vehicle, Entity passenger, CallbackInfo ci) {
		VanishUtil.ACTIVE_ENTITY.set(passenger);
	}

	@Inject(method = "tickPassenger", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;rideTick()V", shift = At.Shift.AFTER))
	public void vanishmod$afterPassengerTick(Entity vehicle, Entity passenger, CallbackInfo ci) {
		VanishUtil.ACTIVE_ENTITY.remove();
	}
}

