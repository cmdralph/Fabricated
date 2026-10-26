package io.github.cmdralph.fabricated.neoforge.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.neoforge.resource.ClientResourceLoaderBridge;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ReloadableResourceManager;

/**
 * Holds back a reload listener a mod registers on the client's resource manager before NeoForge's reload listener
 * event, which refuses listeners it can't name and so stopped the game; the event adds it instead (see
 * {@link ClientResourceLoaderBridge#holdBack}). Injects at the start of {@code registerReloadListener} only.
 */
@Mixin(ReloadableResourceManager.class)
abstract class ClientReloadListenerMixin {
	@Inject(method = "registerReloadListener", at = @At("HEAD"), cancellable = true)
	private void fabricated$holdBackEarlyListener(PreparableReloadListener listener, CallbackInfo ci) {
		if (ClientResourceLoaderBridge.holdBack((ReloadableResourceManager) (Object) this, listener)) ci.cancel();
	}
}
