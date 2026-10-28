package io.github.cmdralph.fabricated.neoforge.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.neoforge.lifecycle.ClientLifecycleBridge;
import net.minecraft.client.Minecraft;

/**
 * Fires Fabric's {@code CLIENT_STOPPING} where Fabric API does (Minecraft 1.21.1): as {@code destroy()} starts, once it
 * has logged that the game stops. NeoForge 21.1 has no event there.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftStoppingMixin {
	@Inject(method = "destroy", at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;info(Ljava/lang/String;)V", shift = At.Shift.AFTER,
			ordinal = 0, remap = false))
	private void fabricated$clientStopping(CallbackInfo ci) {
		ClientLifecycleBridge.clientStopping((Minecraft) (Object) this);
	}
}
