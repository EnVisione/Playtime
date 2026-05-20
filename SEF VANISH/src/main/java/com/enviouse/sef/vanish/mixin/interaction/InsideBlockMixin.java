package com.enviouse.sef.vanish.mixin.interaction;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.BigDripleafBlock;
import net.minecraft.world.level.block.TripWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import com.enviouse.sef.vanish.VanishConfig;
import com.enviouse.sef.vanish.VanishUtil;

@Mixin(value = {BasePressurePlateBlock.class, BigDripleafBlock.class, TripWireBlock.class})
public abstract class InsideBlockMixin {

	@Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
	private void vanishmod$cancelEntityInsideBlock(BlockState state, Level level, BlockPos pos, Entity entity, CallbackInfo ci) {
		if (VanishUtil.isVanished(entity) && VanishConfig.CONFIG.preventBlockInteractions.get())
			ci.cancel();
	}
}

