package io.github.cmdralph.fabricated.forgetest;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** The client side of {@link ForgeNetworkingTest}: sends a ping as the client joins and logs the server's answer. */
@Environment(EnvType.CLIENT)
public final class ForgeNetworkingClient {
	private ForgeNetworkingClient() {
	}

	/** From the client entrypoint; only with Fabric API's networking module. */
	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(ForgeNetworkingTest.Ping.TYPE, (payload, context) -> {
			if (payload.value() == ForgeNetworkingTest.SENT + 1) {
				ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST networking round trip OK");
			} else {
				ForgeTestMod.LOGGER.warn("FABRICATED-FORGE-TEST networking: the server answered {}", payload.value());
			}
		});
		ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> {
			if (!ClientPlayNetworking.canSend(ForgeNetworkingTest.Ping.TYPE)) {
				ForgeTestMod.LOGGER.warn("FABRICATED-FORGE-TEST networking: the server can't receive the ping as the client joins");
			}
			ClientPlayNetworking.send(new ForgeNetworkingTest.Ping(ForgeNetworkingTest.SENT));
		});
	}
}
