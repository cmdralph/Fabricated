package net.fabricmc.fabric.api.networking.v1;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Stand-in for fabric-networking-api-v1's {@code PayloadTypeRegistry} on 26.1.2, 26.2 and 26.3, the part the forge test
 * mod uses: Fabricated passes the module through, so the test mod compiles against this and runs against Fabric API's
 * jar. Never packaged.
 */
public interface PayloadTypeRegistry<B> {
	<T extends CustomPacketPayload> CustomPacketPayload.TypeAndCodec<? super B, T> register(CustomPacketPayload.Type<T> type, StreamCodec<? super B, T> codec);

	static PayloadTypeRegistry<RegistryFriendlyByteBuf> serverboundPlay() {
		throw new AssertionError("stand-in");
	}

	static PayloadTypeRegistry<RegistryFriendlyByteBuf> clientboundPlay() {
		throw new AssertionError("stand-in");
	}
}
