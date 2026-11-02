package net.fabricmc.fabric.api.event.lifecycle.v1;

import net.minecraft.server.MinecraftServer;

import net.fabricmc.fabric.api.event.Event;

/** Stand-in for fabric-lifecycle-events-v1's, which Fabric API's jar provides on Forge 61 (intermediary names): compile only. */
public final class ServerTickEvents {
	public static final Event<EndTick> END_SERVER_TICK = null;

	private ServerTickEvents() {
	}

	@FunctionalInterface
	public interface EndTick {
		void onEndTick(MinecraftServer server);
	}
}
