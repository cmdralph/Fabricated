package io.github.cmdralph.fabricated.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.neoforge.networking.NetworkingBridge;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientCommonPacketListener;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerCommonPacketListener;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/**
 * Sends a payload NeoForge has no registration for, as vanilla does, where NeoForge would throw (NET-5). No NeoForge
 * or Fabric mod declared such a payload (Fabric payload types are all registered with NeoForge): a mod put it on the
 * wire with its own codec and receives it with its own hooks, as Polymer does its handshake.
 */
@Mixin(NetworkRegistry.class)
abstract class NetworkRegistryUnregisteredPayloadMixin {
	@Inject(method = "checkPacket(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/protocol/common/ServerCommonPacketListener;)V",
			at = @At("HEAD"), cancellable = true)
	private static void fabricated$sendUnregisteredToClient(Packet<?> packet, ServerCommonPacketListener listener, CallbackInfo ci) {
		if (packet instanceof ClientboundCustomPayloadPacket custom && NetworkingBridge.unregistered(custom.payload().type().id())) ci.cancel();
	}

	@Inject(method = "checkPacket(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/protocol/common/ClientCommonPacketListener;)V",
			at = @At("HEAD"), cancellable = true)
	private static void fabricated$sendUnregisteredToServer(Packet<?> packet, ClientCommonPacketListener listener, CallbackInfo ci) {
		if (packet instanceof ServerboundCustomPayloadPacket custom && NetworkingBridge.unregistered(custom.payload().type().id())) ci.cancel();
	}
}
