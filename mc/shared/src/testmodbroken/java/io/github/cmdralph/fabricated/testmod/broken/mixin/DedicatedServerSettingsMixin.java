package io.github.cmdralph.fabricated.testmod.broken.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.dedicated.DedicatedServerSettings;

/**
 * Injects at a call the server's settings never make, as {@link UnmatchedCallMixin} does into {@code MinecraftServer},
 * which pre-flight leaves out. Applied, where pre-flight is told to ignore what it finds (the apply-failure runs), it
 * fails with an injector's critical failure as {@code DedicatedServerSettings} first loads: in the dedicated server's
 * {@code main}, right after mod loading and before the world is touched, which catches it and only logs it on NeoForge
 * 21.11 to 26.3 and Forge 61 to 66. The launch after the two others are left out must record it, and the next one
 * leave all three out (APPLY-4).
 */
@Mixin(DedicatedServerSettings.class)
public abstract class DedicatedServerSettingsMixin {
	@Inject(method = "forceSave", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/server/dedicated/DedicatedServerSettings;fabricatedCallThatIsNeverMade()V"), require = 1)
	private void fabricatedTest$never(CallbackInfo ci) {
		throw new AssertionError("unreachable");
	}
}
