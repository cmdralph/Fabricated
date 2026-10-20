package io.github.cmdralph.fabricated.testmod;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.mojang.authlib.GameProfile;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;

/**
 * CI test mod part for {@code ServerConfigurationConnectionEvents.DISCONNECT}: on the dedicated server, a configuration
 * listener on a connection with no channel is told its connection closed, as a client leaving during configuration
 * would, and the event must fire once, for that listener.
 */
public final class ConfigurationDisconnectTest implements ModInitializer {
	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			if (!server.isDedicatedServer()) return;
			// Not a valid player name, so it can't be taken for a player of this server.
			GameProfile profile = new GameProfile(UUID.randomUUID(), "[Fabricated probe]");
			ServerConfigurationPacketListenerImpl probe = new ServerConfigurationPacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND),
					CommonListenerCookie.createInitial(profile, false));
			AtomicInteger fired = new AtomicInteger();
			ServerConfigurationConnectionEvents.DISCONNECT.register((listener, s) -> {
				if (listener == probe) fired.incrementAndGet();
			});
			probe.onDisconnect(new DisconnectionDetails(Component.literal("Fabricated test")));
			if (fired.get() == 1) {
				TestMod.LOGGER.info("FABRICATED-TEST configuration disconnect OK");
			} else {
				TestMod.LOGGER.error("FABRICATED-TEST configuration disconnect FAILED: the event fired {} time(s)", fired.get());
			}
		});
	}
}
