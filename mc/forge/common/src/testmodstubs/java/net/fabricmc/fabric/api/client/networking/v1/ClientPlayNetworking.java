package net.fabricmc.fabric.api.client.networking.v1;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Stand-in for fabric-networking-api-v1's {@code ClientPlayNetworking}, the part the forge test mod uses (signatures as
 * upstream's on 1.21.11, 26.1.2, 26.2 and 26.3). Never packaged.
 */
public final class ClientPlayNetworking {
	private ClientPlayNetworking() {
	}

	public static <T extends CustomPacketPayload> boolean registerGlobalReceiver(CustomPacketPayload.Type<T> type, PlayPayloadHandler<T> handler) {
		throw new AssertionError("stand-in");
	}

	public static boolean canSend(CustomPacketPayload.Type<?> type) {
		throw new AssertionError("stand-in");
	}

	public static void send(CustomPacketPayload payload) {
		throw new AssertionError("stand-in");
	}

	@FunctionalInterface
	public interface PlayPayloadHandler<T extends CustomPacketPayload> {
		void receive(T payload, Context context);
	}

	public interface Context {
	}
}
