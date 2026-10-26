package io.github.cmdralph.fabricated.neoforge.mixin.client;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.neoforge.lifecycle.ClientLifecycleBridge;
import net.minecraft.client.Minecraft;

/**
 * Fires Fabric's {@code CLIENT_STARTED} where Fabric API does: as {@code run()} starts, once it has recorded the game
 * thread, before the first frame (mods hooking the frame expect it). Without this hook, the first client tick fires it.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftStartedMixin {
	@Inject(method = "run", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;gameThread:Ljava/lang/Thread;",
			opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER, ordinal = 0), require = 0)
	private void fabricated$clientStarted(CallbackInfo ci) {
		ClientLifecycleBridge.clientStarted((Minecraft) (Object) this);
	}
}
