package io.github.cmdralph.fabricated.neoforge.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.neoforge.EntrypointBridge;
import net.minecraft.client.Minecraft;

/**
 * Runs Fabric's client entrypoints where Fabric Loader does: in the constructor, just before it records the game
 * thread ({@code Thread.currentThread()}), once the instance, game directory and user are set. If a version lacks that
 * call, at the end of the constructor instead (the entrypoints run once either way).
 */
@Mixin(Minecraft.class)
public abstract class MinecraftEntrypointsMixin {
	@Inject(method = "<init>", at = @At(value = "INVOKE", target = "Ljava/lang/Thread;currentThread()Ljava/lang/Thread;", ordinal = 0),
			require = 0)
	private void fabricated$runClientEntrypoints(CallbackInfo ci) {
		EntrypointBridge.runClientEntrypoints();
	}

	@Inject(method = "<init>", at = @At("TAIL"))
	private void fabricated$runClientEntrypointsLate(CallbackInfo ci) {
		EntrypointBridge.runClientEntrypoints();
	}
}
