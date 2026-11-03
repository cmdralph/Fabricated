package io.github.cmdralph.fabricated.forgetest.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.cmdralph.fabricated.forgetest.ClientHold;

/**
 * Holds mc-runtime-test, which CI's client runs start the game with (ci/forge/smoke-client.sh), while the test mod's
 * checks on the integrated server haven't logged their results ({@link ClientHold}). Its tick hook returning false
 * skips its work that tick, so it neither counts down to quitting nor quits, as its controller class invites mods to
 * do. Not a Fabricated feature: only CI's runs have mc-runtime-test, and without it this mixin applies to nothing.
 */
@Pseudo
@Mixin(targets = "me.earth.mc_runtime_test.McRuntimeTest", remap = false)
public abstract class McRuntimeTestMixin {
	@Inject(method = "tickHook", at = @At("HEAD"), cancellable = true, remap = false)
	private static void fabricatedForgeTest$holdForServerChecks(CallbackInfoReturnable<Boolean> cir) {
		if (ClientHold.holding()) cir.setReturnValue(false);
	}
}
