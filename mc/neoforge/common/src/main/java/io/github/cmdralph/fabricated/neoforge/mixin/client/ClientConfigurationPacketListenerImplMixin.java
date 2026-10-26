package io.github.cmdralph.fabricated.neoforge.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.neoforge.networking.ClientNetworkingBridge;
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import net.minecraft.network.DisconnectionDetails;

/**
 * Records the client's current configuration listener, which Fabric's client configuration API needs, and tells
 * Fabric when the connection closes during configuration; NeoForge has an event for neither. Injects at the end of the
 * constructor and at the start of {@code onDisconnect} only.
 */
@Mixin(ClientConfigurationPacketListenerImpl.class)
abstract class ClientConfigurationPacketListenerImplMixin {
	@Inject(method = "<init>", at = @At("TAIL"))
	private void fabricated$onCreated(CallbackInfo ci) {
		ClientNetworkingBridge.onConfigurationStart((ClientConfigurationPacketListenerImpl) (Object) this);
	}

	@Inject(method = "onDisconnect", at = @At("HEAD"))
	private void fabricated$onDisconnect(DisconnectionDetails details, CallbackInfo ci) {
		ClientNetworkingBridge.onConfigurationDisconnect((ClientConfigurationPacketListenerImpl) (Object) this);
	}
}
