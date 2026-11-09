package io.github.cmdralph.fabricated.testmod.frapi.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.cmdralph.fabricated.testmod.frapi.FrapiClientTestMod;

/**
 * Holds mc-runtime-test, which CI's client runs start the game with (ci/smoke-client.sh), until the
 * renderer test mod's moving block checks have logged ({@link FrapiClientTestMod#holding}), as the Fabric test mod's
 * {@code McRuntimeTestMixin} does for its server checks: its tick hook returning false skips its work that tick. Not a
 * Fabricated feature: only CI's runs have mc-runtime-test, and without it this mixin applies to nothing.
 */
@Pseudo
@Mixin(targets = "me.earth.mc_runtime_test.McRuntimeTest", remap = false)
public abstract class McRuntimeTestHoldMixin {
	@Inject(method = "tickHook", at = @At("HEAD"), cancellable = true, remap = false)
	private static void fabricatedFrapiTest$holdForMovingBlocks(CallbackInfoReturnable<Boolean> cir) {
		if (FrapiClientTestMod.holding()) cir.setReturnValue(false);
	}
}
