package io.github.cmdralph.fabricated.testmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.testmod.TestMod;
import net.minecraft.server.MinecraftServer;

/** Proves Fabric mixins are applied: logs once after the first server tick. */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
	@Unique
	private static boolean fabricatedTest$logged;

	@Inject(method = "tickServer", at = @At("TAIL"))
	private void fabricatedTest$afterTick(CallbackInfo ci) {
		if (!fabricatedTest$logged) {
			fabricatedTest$logged = true;
			TestMod.LOGGER.info("FABRICATED-TEST mixin OK");
		}
	}
}
