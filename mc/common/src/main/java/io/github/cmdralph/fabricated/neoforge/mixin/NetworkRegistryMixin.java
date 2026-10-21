package io.github.cmdralph.fabricated.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.cmdralph.fabricated.neoforge.networking.ClientboundCodecs;
import io.github.cmdralph.fabricated.neoforge.networking.NetworkingBridge;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/**
 * Lets a Fabric payload id use its own codec for clientbound packets. NeoForge looks up one codec per id for
 * encoding and decoding, given the packet's direction; Fabric mods such as {@code fabric-data-attachment-api-v1}
 * send a different payload type in each direction under one id. A Fabric payload in the {@code minecraft} namespace,
 * which NeoForge doesn't register, gets its Fabric codec here too (NET-4).
 */
@Mixin(NetworkRegistry.class)
public abstract class NetworkRegistryMixin {
	@Inject(method = "getCodec", at = @At("HEAD"), cancellable = true)
	@SuppressWarnings("unchecked")
	private static void fabricated$clientboundCodec(Identifier id, ConnectionProtocol protocol, PacketFlow flow,
			CallbackInfoReturnable<StreamCodec<? super FriendlyByteBuf, ? extends CustomPacketPayload>> cir) {
		StreamCodec<?, ?> vanilla = NetworkingBridge.vanillaNamespaceCodec(id, protocol, flow);
		if (vanilla != null) {
			cir.setReturnValue((StreamCodec<? super FriendlyByteBuf, ? extends CustomPacketPayload>) vanilla);
			return;
		}
		if (flow != PacketFlow.CLIENTBOUND) return;
		StreamCodec<?, ?> codec = ClientboundCodecs.get(protocol, id);
		if (codec != null) cir.setReturnValue((StreamCodec<? super FriendlyByteBuf, ? extends CustomPacketPayload>) codec);
	}
}
