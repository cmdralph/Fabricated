package io.github.cmdralph.fabricated.testmod.broken.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.MinecraftServer;

/**
 * Injects at a call its target method never makes: the mixin pre-flight must catch it before launch, and leave it out
 * as it does {@link ImpossibleMixin}. Applied, where pre-flight is told to ignore what it finds
 * ({@code ci/neoforge/smoke-apply-failure.sh}), Mixin finds the method but no such call in it and stops the game with
 * an injector's critical failure, which bypasses Mixin's error handlers; the next launch must leave it out (APPLY-1).
 */
@Mixin(MinecraftServer.class)
public abstract class UnmatchedCallMixin {
	@Inject(method = "tickServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;fabricatedCallThatIsNeverMade()V"), require = 1)
	private void fabricatedTest$never(CallbackInfo ci) {
		throw new AssertionError("unreachable");
	}
}
