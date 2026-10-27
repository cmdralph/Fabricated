package io.github.cmdralph.fabricated.forgetest;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/** Registers {@link ForgeNetworkingTest}'s payload type both ways, in Fabric API's 1.21.11 names for the play registries. */
final class ForgePayloadTypes {
	private ForgePayloadTypes() {
	}

	static void register() {
		PayloadTypeRegistry.playC2S().register(ForgeNetworkingTest.Ping.TYPE, ForgeNetworkingTest.Ping.CODEC);
		PayloadTypeRegistry.playS2C().register(ForgeNetworkingTest.Ping.TYPE, ForgeNetworkingTest.Ping.CODEC);
	}
}
