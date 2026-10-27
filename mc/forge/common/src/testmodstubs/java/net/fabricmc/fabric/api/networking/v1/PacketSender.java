package net.fabricmc.fabric.api.networking.v1;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Stand-in for fabric-networking-api-v1's {@code PacketSender}, the part the forge test mod uses (signatures as
 * upstream's on 1.21.11, 26.1.2, 26.2 and 26.3): Fabricated passes the module through, so the test mod compiles against
 * this and runs against Fabric API's jar. Never packaged.
 */
public interface PacketSender {
	default void sendPacket(CustomPacketPayload payload) {
		throw new AssertionError("stand-in");
	}
}
