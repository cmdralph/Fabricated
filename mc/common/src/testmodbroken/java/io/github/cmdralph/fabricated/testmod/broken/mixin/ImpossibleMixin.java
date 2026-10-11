package io.github.cmdralph.fabricated.testmod.broken.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.MinecraftServer;

/** Targets a method that does not exist: the mixin pre-flight must disable this mod before launch. */
@Mixin(MinecraftServer.class)
public abstract class ImpossibleMixin {
	@Inject(method = "fabricatedMethodThatDoesNotExist", at = @At("HEAD"), require = 1)
	private void fabricatedTest$never(CallbackInfo ci) {
		throw new AssertionError("unreachable");
	}
}
