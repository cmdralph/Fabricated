package io.github.cmdralph.fabricated.testmod;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.mojang.authlib.GameProfile;

import io.github.cmdralph.fabricated.api.FabricApiModule;
import io.github.cmdralph.fabricated.api.Fabricated;
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
 *
 * <p>Where {@code fabric-networking-api-v1} isn't Fabricated's (Forgified Fabric API's, or on Forge Fabric API's own,
 * from its jar), the check is skipped (PROVIDER-9): that module fires the event as upstream does, as the connection
 * closes ({@code Connection.channelInactive} and {@code handleDisconnection}: upstream's {@code ConnectionMixin}, also
 * read in Forgified Fabric API's 1.21.1, 26.1.2 and 26.2 sources), which a listener on a connection without a channel
 * never reaches.
 */
public final class ConfigurationDisconnectTest implements ModInitializer {
	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			if (!server.isDedicatedServer()) return;
			boolean own = Fabricated.get().flatMap(api -> api.fabricApiModule("fabric-networking-api-v1"))
					.map(module -> module.status() == FabricApiModule.Status.NATIVE).orElse(false);
			if (!own) {
				TestMod.LOGGER.info("FABRICATED-TEST configuration disconnect SKIPPED: fabric-networking-api-v1 isn't Fabricated's here (it is Fabric API's"
						+ " own, or another mod's), which fires DISCONNECT as the connection closes, as upstream does, never for a listener on a"
						+ " connection without a channel (PROVIDER-9)");
				return;
			}
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
