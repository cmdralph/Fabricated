package io.github.cmdralph.fabricated.testmod.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.cmdralph.fabricated.testmod.ClientHold;

/**
 * Holds mc-runtime-test, which CI's client runs start the game with (ci/smoke-client.sh), until the integrated server
 * has been in the world as long as the client ({@link ClientHold}). Its controller class's tick hook returning false
 * skips its work that tick, so it neither tests nor quits, as that class invites mods to do. Not a Fabricated feature:
 * only CI's runs have mc-runtime-test, and without it this mixin applies to nothing.
 */
@Pseudo
@Mixin(targets = "me.earth.mc_runtime_test.McRuntimeTest", remap = false)
public abstract class McRuntimeTestTickHookMixin {
	@Inject(method = "tickHook", at = @At("HEAD"), cancellable = true, remap = false)
	private static void fabricatedTest$holdForTheServer(CallbackInfoReturnable<Boolean> cir) {
		if (ClientHold.holding()) cir.setReturnValue(false);
	}
}
