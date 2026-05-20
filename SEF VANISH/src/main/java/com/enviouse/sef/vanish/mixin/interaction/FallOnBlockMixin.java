package com.enviouse.sef.vanish.mixin.interaction;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.TurtleEggBlock;
import net.minecraft.world.level.block.state.BlockState;
import com.enviouse.sef.vanish.VanishConfig;
import com.enviouse.sef.vanish.VanishUtil;

@Mixin(value = {FarmBlock.class, TurtleEggBlock.class})
public abstract class FallOnBlockMixin {

	@Inject(method = "fallOn", at = @At("HEAD"), cancellable = true)
	private void vanishmod$cancelEntityFallOnBlock(Level level, BlockState state, BlockPos pos, Entity entity, float fallDistance, CallbackInfo ci) {
		if (VanishUtil.isVanished(entity) && VanishConfig.CONFIG.preventBlockInteractions.get())
			ci.cancel();
	}
}

