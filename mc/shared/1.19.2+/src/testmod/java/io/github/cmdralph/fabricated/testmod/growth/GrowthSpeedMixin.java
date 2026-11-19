package io.github.cmdralph.fabricated.testmod.growth;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.cmdralph.fabricated.testmod.VanillaGrowthTest;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;

/**
 * A hook of vanilla's {@code CropBlock.getGrowthSpeed(Block, BlockGetter, BlockPos)} by its descriptor, as Balm's are,
 * taking vanilla's parameters: NeoForge's method takes the crop's state for its block, which it works out of it first, and
 * pre-flight points the hook there with a bridge giving it that block (SEAM-12, {@link VanillaGrowthTest}).
 */
@Mixin(CropBlock.class)
public abstract class GrowthSpeedMixin {
	@Inject(method = "getGrowthSpeed(Lnet/minecraft/world/level/block/Block;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F",
			at = @At("RETURN"))
	private static void fabricatedTest$grown(Block crop, BlockGetter level, BlockPos pos, CallbackInfoReturnable<Float> info) {
		VanillaGrowthTest.grown(crop, info.getReturnValueF());
	}
}
