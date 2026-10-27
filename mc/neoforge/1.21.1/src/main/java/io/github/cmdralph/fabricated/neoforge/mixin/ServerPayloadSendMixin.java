package io.github.cmdralph.fabricated.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.neoforge.networking.NetworkingBridge;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerCommonPacketListener;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;

/**
 * Drops a Fabric payload the client has no channel for, as on Fabric (the client would ignore it), where NeoForge
 * would throw: Fabric mods often send without checking {@code canSend} first. Minecraft 1.21.1's send takes a
 * {@code PacketSendListener}.
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerPayloadSendMixin {
	@Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V", at = @At("HEAD"), cancellable = true)
	private void fabricated$dropUnsendableFabricPayload(Packet<?> packet, PacketSendListener listener, CallbackInfo ci) {
		if (packet instanceof ClientboundCustomPayloadPacket custom
				&& NetworkingBridge.unsendable(custom.payload().type().id(), (ServerCommonPacketListener) this, true)) {
			ci.cancel();
		}
	}
}
