package com.enviouse.sef.vanish.mixin.interaction;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RedStoneOreBlock;
import net.minecraft.world.level.block.SculkSensorBlock;
import net.minecraft.world.level.block.SculkShriekerBlock;
import net.minecraft.world.level.block.TurtleEggBlock;
import net.minecraft.world.level.block.state.BlockState;
import com.enviouse.sef.vanish.VanishConfig;
import com.enviouse.sef.vanish.VanishUtil;

@Mixin(value = {RedStoneOreBlock.class, SculkSensorBlock.class, SculkShriekerBlock.class, TurtleEggBlock.class})
public abstract class StepOnBlockMixin {

	@Inject(method = "stepOn", at = @At("HEAD"), cancellable = true)
	private void vanishmod$cancelEntityStepOnBlock(Level level, BlockPos pos, BlockState state, Entity entity, CallbackInfo ci) {
		if (VanishUtil.isVanished(entity) && VanishConfig.CONFIG.preventBlockInteractions.get())
			ci.cancel();
	}
}

