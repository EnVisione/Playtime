package com.enviouse.sef.vanish.mixin.world;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import com.enviouse.sef.vanish.VanishConfig;
import com.enviouse.sef.vanish.VanishUtil;

@Mixin(value = {BarrelBlockEntity.class, ChestBlockEntity.class, PlayerEnderChestContainer.class, ShulkerBoxBlockEntity.class})
public abstract class ContainerMixin {

	@Inject(method = "startOpen", at = @At("HEAD"), cancellable = true)
	public void vanishmod$cancelOpenAnimation(Player player, CallbackInfo ci) {
		if (player instanceof ServerPlayer serverPlayer && VanishConfig.CONFIG.preventContainerAnimations.get() && VanishUtil.isVanished(serverPlayer))
			ci.cancel();
	}

	@Inject(method = "stopOpen", at = @At("HEAD"), cancellable = true)
	public void vanishmod$cancelCloseAnimation(Player player, CallbackInfo ci) {
		if (player instanceof ServerPlayer serverPlayer && VanishConfig.CONFIG.preventContainerAnimations.get() && VanishUtil.isVanished(serverPlayer))
			ci.cancel();
	}
}

