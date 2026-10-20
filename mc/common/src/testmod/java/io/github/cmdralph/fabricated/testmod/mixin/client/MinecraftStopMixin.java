package io.github.cmdralph.fabricated.testmod.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.testmod.TestMod;
import net.minecraft.client.Minecraft;

/**
 * CI diagnostics: logs who asked the game to stop, with the stack of the request. The 26.x corpus clients close during
 * model loading without an error; mc-runtime-test stops a client that passed the same way.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftStopMixin {
	@Inject(method = "stop", at = @At("HEAD"))
	private void fabricatedTest$whoStops(CallbackInfo ci) {
		TestMod.LOGGER.info("FABRICATED-TEST game stop requested", new Throwable("stack of the request"));
	}
}
