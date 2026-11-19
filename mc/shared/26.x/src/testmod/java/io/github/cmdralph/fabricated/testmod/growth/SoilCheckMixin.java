package io.github.cmdralph.fabricated.testmod.growth;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import io.github.cmdralph.fabricated.testmod.VanillaGrowthTest;
import net.minecraft.world.level.block.CropBlock;

/**
 * A hook of vanilla's moisture check in a crop's growth speed from 26.1, {@code soil.getValueOrElse(FarmlandBlock.MOISTURE,
 * 0)}, as Balm's custom farmland hooks it there: NeoForge and Forge ask the soil's {@code isFertile} there, and Fabricated
 * puts vanilla's check back for vanilla's farmland (SEAM-12, {@link VanillaGrowthTest}). It counts the checks.
 */
@Mixin(CropBlock.class)
public abstract class SoilCheckMixin {
	@ModifyExpressionValue(method = "getGrowthSpeed", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/state/BlockState;getValueOrElse(Lnet/minecraft/world/level/block/state/properties/Property;Ljava/lang/Comparable;)Ljava/lang/Comparable;"))
	private static Comparable<?> fabricatedTest$moisture(Comparable<?> moisture) {
		VanillaGrowthTest.soilChecked();
		return moisture;
	}
}
