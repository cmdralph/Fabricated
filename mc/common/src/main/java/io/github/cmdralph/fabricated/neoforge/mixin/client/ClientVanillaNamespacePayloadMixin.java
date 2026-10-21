package io.github.cmdralph.fabricated.neoforge.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.neoforge.networking.ClientNetworkingBridge;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.common.ClientCommonPacketListener;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;

/** The client side of {@code VanillaNamespacePayloadMixin} (NET-4). */
@Mixin(ClientCommonPacketListenerImpl.class)
abstract class ClientVanillaNamespacePayloadMixin {
	@Inject(method = "handleCustomPayload(Lnet/minecraft/network/protocol/common/ClientboundCustomPayloadPacket;)V", at = @At("HEAD"), cancellable = true)
	private void fabricated$receiveVanillaNamespace(ClientboundCustomPayloadPacket packet, CallbackInfo ci) {
		if (ClientNetworkingBridge.receiveVanillaNamespace((ClientCommonPacketListener) this, packet.payload())) ci.cancel();
	}
}
