package io.github.cmdralph.fabricated.forgetest;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/** Registers {@link ForgeNetworkingTest}'s payload type both ways, in Fabric API's 26.x names for the play registries. */
final class ForgePayloadTypes {
	private ForgePayloadTypes() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(ForgeNetworkingTest.Ping.TYPE, ForgeNetworkingTest.Ping.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ForgeNetworkingTest.Ping.TYPE, ForgeNetworkingTest.Ping.CODEC);
	}
}
