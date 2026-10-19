package io.github.cmdralph.fabricated.neoforge.mixin.client;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.neoforge.EntrypointBridge;
import net.minecraft.client.Minecraft;

/** Runs Fabric's client entrypoints where Fabric Loader does: in the constructor, once {@code getInstance()} is set. */
@Mixin(Minecraft.class)
public abstract class MinecraftEntrypointsMixin {
	@Inject(method = "<init>", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;instance:Lnet/minecraft/client/Minecraft;",
			opcode = Opcodes.PUTSTATIC, shift = At.Shift.AFTER))
	private void fabricated$runClientEntrypoints(CallbackInfo ci) {
		EntrypointBridge.runClientEntrypoints();
	}
}
