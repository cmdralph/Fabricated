package net.fabricmc.fabric.api.networking.v1;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Stand-in for fabric-networking-api-v1's {@code PayloadTypeRegistry} on 1.21.11, the part the forge test mod uses, in
 * Mojang's names as the remapped module has it at runtime. 26.x names the play registries {@code serverboundPlay} and
 * {@code clientboundPlay} (common26's stand-in). Never packaged.
 */
public interface PayloadTypeRegistry<B> {
	<T extends CustomPacketPayload> CustomPacketPayload.TypeAndCodec<? super B, T> register(CustomPacketPayload.Type<T> type, StreamCodec<? super B, T> codec);

	static PayloadTypeRegistry<RegistryFriendlyByteBuf> playC2S() {
		throw new AssertionError("stand-in");
	}

	static PayloadTypeRegistry<RegistryFriendlyByteBuf> playS2C() {
		throw new AssertionError("stand-in");
	}
}
