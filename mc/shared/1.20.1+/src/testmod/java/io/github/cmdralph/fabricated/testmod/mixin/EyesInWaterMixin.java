package io.github.cmdralph.fabricated.testmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import io.github.cmdralph.fabricated.testmod.VanillaEyesTest;
import net.minecraft.world.entity.player.Player;

/**
 * Artifacts' kind of hook of vanilla's eyes-in-water check in a player's mining speed, {@code
 * isEyeInFluid(FluidTags.WATER)} in {@code Player.getDestroySpeed}, which NeoForge 26.x asks of the fluid types at the
 * eyes and Fabricated puts back where the eyes are in vanilla's water, lava or no fluid (SEAM-9, {@link VanillaEyesTest}):
 * a {@code @ModifyExpressionValue} of the call, which takes the slower mining underwater away while the check asks it to.
 * Where the platform moved the method's code into {@code getDigSpeed} (NeoForge 21.1, Forge 47) or into the overload
 * taking the position (NeoForge 21.11 on, Forge 52 on), pre-flight points it there.
 */
@Mixin(Player.class)
public abstract class EyesInWaterMixin {
	@ModifyExpressionValue(method = "getDestroySpeed", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/player/Player;isEyeInFluid(Lnet/minecraft/tags/TagKey;)Z"))
	private boolean fabricatedTest$eyesInWater(boolean inWater) {
		return VanillaEyesTest.eyesInWater(inWater);
	}
}
