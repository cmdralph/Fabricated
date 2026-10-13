package io.github.cmdralph.fabricated.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.neoforge.networking.FabricPayloads;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerCommonPacketListener;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;

/**
 * Drops a Fabric payload the client has no channel for, as on Fabric (the client would ignore it), where NeoForge
 * would throw: Fabric mods often send without checking {@code canSend} first.
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonPacketListenerImplMixin {
	@Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V", at = @At("HEAD"), cancellable = true)
	private void fabricated$dropUnsendableFabricPayload(Packet<?> packet, ChannelFutureListener listener, CallbackInfo ci) {
		if (packet instanceof ClientboundCustomPayloadPacket custom
				&& FabricPayloads.unsendable(custom.payload().type().id(), (ServerCommonPacketListener) this, true)) {
			ci.cancel();
		}
	}
}
