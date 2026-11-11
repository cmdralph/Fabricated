package io.github.cmdralph.fabricated.testmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.testmod.TestMod;
import net.minecraft.server.Main;

/**
 * owo-lib's registry freeze hook: after Fabric Loader's {@code Hooks.startServer}, which its launcher adds to the dedicated
 * server's {@code Main.main} where it runs mods' entrypoints. Pre-flight points it where the platform loads mods
 * (HOOK-1, HOOK-4); it must run once, after the server entrypoints.
 */
@Mixin(Main.class)
public abstract class LoaderHookMixin {
	@Inject(method = "main", at = @At(value = "INVOKE", target = "Lnet/fabricmc/loader/impl/game/minecraft/Hooks;startServer(Ljava/io/File;Ljava/lang/Object;)V",
			shift = At.Shift.AFTER, remap = false))
	private static void fabricatedTest$afterLoaderHook(CallbackInfo ci) {
		if (TestMod.serverEntrypointRan) TestMod.LOGGER.info("FABRICATED-TEST loader server hook OK");
		else TestMod.LOGGER.error("FABRICATED-TEST loader server hook FAILED: it ran before the server entrypoints");
	}
}
