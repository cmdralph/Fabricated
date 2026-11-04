package io.github.cmdralph.fabricated.testmod.frapi.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.cmdralph.fabricated.testmod.frapi.ParticleSpriteCheck;

/**
 * What Forge 66's extraction met for the screen effect check ({@code ParticleSpriteCheck}, PART-4), which that check
 * logs as the game stops where the effect never asked: each extraction of the player's state, and what Forge's lookup
 * of the block the camera is in ({@code getOverlayBlock}, Forge 66's own) returned. Applies to nothing where those
 * methods aren't (Forge 64 and 65 draw the effect elsewhere); static handlers, as either method may be. Test code only.
 */
@Pseudo
@Mixin(targets = "net.minecraft.client.renderer.extract.LevelExtractor")
abstract class OverlayProbeMixin {
	@Inject(method = "extractPlayerState", at = @At("HEAD"), require = 0)
	private static void fabricatedTest$extracting(CallbackInfo ci) {
		ParticleSpriteCheck.extracting();
	}

	@Inject(method = "getOverlayBlock", at = @At("RETURN"), require = 0)
	private static void fabricatedTest$overlayFound(CallbackInfoReturnable<Object> cir) {
		ParticleSpriteCheck.overlayFound(cir.getReturnValue());
	}
}
