package io.github.cmdralph.fabricated.neoforge.mixin;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ReloadableResourceManager;

/**
 * Lets a reload listener be registered the vanilla way after NeoForge has sorted the listeners. NeoForge replaces the
 * list with an immutable sorted one once its reload listener event has run, so {@code registerReloadListener} throws
 * from then on; on Fabric it never does, and mods register from their own mixins into {@code Minecraft}'s constructor
 * (OneConfig's font listener). Such a listener is added after the sorted ones, as vanilla adds it, and takes part in
 * the next reload.
 */
@Mixin(ReloadableResourceManager.class)
abstract class ReloadableResourceManagerMixin {
	// Not final: NeoForge's access transformer opens it to replace the list.
	@Shadow
	private List<PreparableReloadListener> listeners;

	@Inject(method = "registerReloadListener", at = @At("HEAD"))
	private void fabricated$reopenListeners(PreparableReloadListener listener, CallbackInfo ci) {
		if (!(listeners instanceof ArrayList)) listeners = new ArrayList<>(listeners);
	}
}
