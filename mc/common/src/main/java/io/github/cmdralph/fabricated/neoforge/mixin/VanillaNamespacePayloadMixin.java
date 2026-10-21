package io.github.cmdralph.fabricated.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.neoforge.networking.NetworkingBridge;
import net.minecraft.network.protocol.common.ServerCommonPacketListener;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;

/**
 * Hands a Fabric payload in the {@code minecraft} namespace to Fabric's receivers (NET-4): NeoForge's modded payload
 * handling skips that namespace, which it keeps for vanilla's payloads, and vanilla's ignores what it doesn't know.
 */
@Mixin(ServerCommonPacketListenerImpl.class)
abstract class VanillaNamespacePayloadMixin {
	@Inject(method = "handleCustomPayload(Lnet/minecraft/network/protocol/common/ServerboundCustomPayloadPacket;)V", at = @At("HEAD"), cancellable = true)
	private void fabricated$receiveVanillaNamespace(ServerboundCustomPayloadPacket packet, CallbackInfo ci) {
		if (NetworkingBridge.receiveVanillaNamespace((ServerCommonPacketListener) this, packet.payload())) ci.cancel();
	}
}
