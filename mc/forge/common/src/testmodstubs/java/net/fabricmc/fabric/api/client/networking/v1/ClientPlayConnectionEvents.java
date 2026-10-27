package net.fabricmc.fabric.api.client.networking.v1;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

/**
 * Stand-in for fabric-networking-api-v1's {@code ClientPlayConnectionEvents}, the part the forge test mod uses
 * (signatures as upstream's on 1.21.11, 26.1.2, 26.2 and 26.3). Never packaged.
 */
public final class ClientPlayConnectionEvents {
	public static final Event<Join> JOIN = null;

	private ClientPlayConnectionEvents() {
	}

	@FunctionalInterface
	public interface Join {
		void onPlayReady(ClientPacketListener listener, PacketSender sender, Minecraft client);
	}
}
