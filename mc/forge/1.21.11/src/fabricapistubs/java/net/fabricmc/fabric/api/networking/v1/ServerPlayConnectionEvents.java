package net.fabricmc.fabric.api.networking.v1;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import net.fabricmc.fabric.api.event.Event;

/** Stand-in for fabric-networking-api-v1's, which Fabric API's jar provides on Forge 61 (intermediary names): compile only. */
public final class ServerPlayConnectionEvents {
	public static final Event<Join> JOIN = null;

	private ServerPlayConnectionEvents() {
	}

	@FunctionalInterface
	public interface Join {
		void onPlayReady(ServerGamePacketListenerImpl listener, PacketSender sender, MinecraftServer server);
	}
}
