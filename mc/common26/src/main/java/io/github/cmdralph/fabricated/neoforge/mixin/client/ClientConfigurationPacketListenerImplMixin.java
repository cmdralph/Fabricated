package io.github.cmdralph.fabricated.neoforge.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.neoforge.networking.ClientNetworkingBridge;
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;

/**
 * Records the client's current configuration listener, which Fabric's client configuration API needs and
 * NeoForge exposes no event for. Injects at the end of the constructor only.
 */
@Mixin(ClientConfigurationPacketListenerImpl.class)
abstract class ClientConfigurationPacketListenerImplMixin {
	@Inject(method = "<init>", at = @At("TAIL"))
	private void fabricated$onCreated(CallbackInfo ci) {
		ClientNetworkingBridge.onConfigurationStart((ClientConfigurationPacketListenerImpl) (Object) this);
	}
}
