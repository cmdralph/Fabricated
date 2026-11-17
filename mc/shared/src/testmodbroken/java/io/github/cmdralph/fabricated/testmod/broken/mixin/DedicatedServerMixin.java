package io.github.cmdralph.fabricated.testmod.broken.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.dedicated.DedicatedServer;

/**
 * Injects at a call the dedicated server's start never makes, as {@link UnmatchedCallMixin} does into
 * {@code MinecraftServer}, which pre-flight leaves out. Applied, where pre-flight is told to ignore what it finds (the
 * apply-failure runs), it fails with an injector's critical failure as {@code DedicatedServer} first loads: in the
 * server's {@code main}, after mod loading, which catches it and only logs it on NeoForge 21.11 to 26.3 and Forge 61
 * to 66. The launch after the two others are left out must record it, and the next one leave all three out (APPLY-4).
 */
@Mixin(DedicatedServer.class)
public abstract class DedicatedServerMixin {
	@Inject(method = "initServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/dedicated/DedicatedServer;fabricatedCallThatIsNeverMade()V"),
			require = 1)
	private void fabricatedTest$never(CallbackInfoReturnable<Boolean> cir) {
		throw new AssertionError("unreachable");
	}
}
