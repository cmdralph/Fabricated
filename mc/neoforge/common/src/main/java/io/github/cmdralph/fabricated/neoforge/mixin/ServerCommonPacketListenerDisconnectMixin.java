package io.github.cmdralph.fabricated.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.neoforge.networking.NetworkingBridge;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;

/**
 * Fires Fabric's {@code ServerConfigurationConnectionEvents.DISCONNECT} when a connection closes during configuration,
 * which NeoForge has no event for. Injects at the start of the listeners' common {@code onDisconnect}, which the
 * connection calls once as it closes.
 */
@Mixin(ServerCommonPacketListenerImpl.class)
abstract class ServerCommonPacketListenerDisconnectMixin {
	@Inject(method = "onDisconnect", at = @At("HEAD"))
	private void fabricated$onDisconnect(DisconnectionDetails details, CallbackInfo ci) {
		if ((Object) this instanceof ServerConfigurationPacketListenerImpl listener) NetworkingBridge.onConfigurationDisconnect(listener);
	}
}
