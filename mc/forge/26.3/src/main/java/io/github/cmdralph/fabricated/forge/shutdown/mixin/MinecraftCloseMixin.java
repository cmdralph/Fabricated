package io.github.cmdralph.fabricated.forge.shutdown.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.electronwill.nightconfig.core.file.FileWatcher;

import net.minecraft.client.Minecraft;

/**
 * Stops night-config's default file watcher as the client closes, where Forge stops its own config watchers
 * ({@code ConfigTracker.forceUnload}, CORPUS-116). Minecraft 26.3's client no longer exits once its main thread ends,
 * and a thread no one stops keeps the game up until its shutdown watchdog writes a crash report and stops it. A Fabric
 * mod bundling night-config (Forge Config API Port's Fabric build) gets Forge's copy instead (the package guard: one
 * copy of a package loads), night-config 3.7.4, whose watcher runs a change's reload on an executor thread that isn't
 * a daemon, where the mod's own 3.8 runs it on a daemon thread: once a watched file changed, that thread kept Forge
 * 66's client up after the player quit. Nothing watches a file once the client has closed.
 */
@Mixin(Minecraft.class)
abstract class MinecraftCloseMixin {
	@Inject(method = "close", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/fml/config/ConfigTracker;forceUnload()V", shift = At.Shift.AFTER,
			remap = false))
	private void fabricated$stopDefaultFileWatcher(CallbackInfo ci) {
		FileWatcher.defaultInstance().stop();
	}
}
