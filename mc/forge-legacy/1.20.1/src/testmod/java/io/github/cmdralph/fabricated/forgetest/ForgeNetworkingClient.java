package io.github.cmdralph.fabricated.forgetest;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.class_2540;

/** The client side of {@link ForgeNetworkingTest}: sends a ping as the client joins and logs the server's answer. */
@Environment(EnvType.CLIENT)
public final class ForgeNetworkingClient {
	private ForgeNetworkingClient() {
	}

	/** From the client entrypoint; only with Fabric API's networking module. */
	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(ForgeNetworkingTest.PING, (client, handler, buf, responseSender) -> {
			int answer = buf.method_10816();
			if (answer == ForgeNetworkingTest.SENT + 1) {
				ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST networking round trip OK");
			} else {
				ForgeTestMod.LOGGER.warn("FABRICATED-FORGE-TEST networking: the server answered {}", answer);
			}
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			if (!ClientPlayNetworking.canSend(ForgeNetworkingTest.PING)) {
				ForgeTestMod.LOGGER.warn("FABRICATED-FORGE-TEST networking: the server can't receive the ping as the client joins");
			}
			class_2540 ping = PacketByteBufs.create();
			ping.method_10804(ForgeNetworkingTest.SENT);
			ClientPlayNetworking.send(ForgeNetworkingTest.PING, ping);
		});
	}
}
