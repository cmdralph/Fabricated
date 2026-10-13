package io.github.cmdralph.fabricated.neoforge.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.neoforge.networking.FabricPayloads;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientCommonPacketListener;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;

/** The client side of {@code ServerPayloadSendMixin}: a server without the channel ignores the payload. */
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ClientPayloadSendMixin {
	@Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
	private void fabricated$dropUnsendableFabricPayload(Packet<?> packet, CallbackInfo ci) {
		if (packet instanceof ServerboundCustomPayloadPacket custom
				&& FabricPayloads.unsendable(custom.payload().type().id(), (ClientCommonPacketListener) this, false)) {
			ci.cancel();
		}
	}
}
