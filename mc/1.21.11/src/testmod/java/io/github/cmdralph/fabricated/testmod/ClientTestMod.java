package io.github.cmdralph.fabricated.testmod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/**
 * Client part of the CI test mod (run by the client smoke test, which joins a singleplayer world): client
 * entrypoint, client lifecycle, a client resource reload listener and a networking round trip with the integrated
 * server (the server side answers in {@link NetworkingTest}).
 */
public final class ClientTestMod implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		TestMod.LOGGER.info("FABRICATED-TEST client entrypoint OK");
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> TestMod.LOGGER.info("FABRICATED-TEST client started OK"));
		ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloader(Identifier.fromNamespaceAndPath("fabricated_testmod", "client_listener"),
				(ResourceManagerReloadListener) manager -> TestMod.LOGGER.info("FABRICATED-TEST client resource reload listener OK"));

		ClientPlayNetworking.registerGlobalReceiver(NetworkingTest.Ping.TYPE, (payload, context) -> {
			if (payload.value() == 42) TestMod.LOGGER.info("FABRICATED-TEST client networking round trip OK");
		});
		ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> {
			TestMod.LOGGER.info("FABRICATED-TEST client join event OK");
			if (ClientPlayNetworking.canSend(NetworkingTest.Ping.TYPE)) {
				ClientPlayNetworking.send(new NetworkingTest.Ping(41));
			} else {
				TestMod.LOGGER.error("FABRICATED-TEST client cannot send the ping payload to the server");
			}
		});
	}
}
