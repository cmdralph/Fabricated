package io.github.cmdralph.fabricated.testmod.growth;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import io.github.cmdralph.fabricated.testmod.VanillaGrowthTest;
import net.minecraft.world.level.block.CropBlock;

/**
 * A hook of vanilla's farmland check in a crop's growth speed up to 1.21.11, {@code soil.is(Blocks.FARMLAND)}, the
 * method's first {@code is} (its neighbours' checks follow), as Balm's custom farmland hooks it: NeoForge and Forge ask the
 * soil's {@code canSustainPlant} there, and Fabricated puts vanilla's check back where it answers theirs (SEAM-12,
 * {@link VanillaGrowthTest}). It counts the checks.
 */
@Mixin(CropBlock.class)
public abstract class SoilCheckMixin {
	@ModifyExpressionValue(method = "getGrowthSpeed", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/world/level/block/Block;)Z", ordinal = 0))
	private static boolean fabricatedTest$farmland(boolean farmland) {
		VanillaGrowthTest.soilChecked();
		return farmland;
	}
}
