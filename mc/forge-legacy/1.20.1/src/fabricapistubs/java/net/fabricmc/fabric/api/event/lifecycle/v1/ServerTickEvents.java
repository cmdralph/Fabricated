package net.fabricmc.fabric.api.event.lifecycle.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.MinecraftServer;

/**
 * Compile-time stand-in: the members of Fabric API 0.92's class (1.20.1) that the renderer test mod calls, in Mojang's
 * names.
 */
public final class ServerTickEvents {
	public static final Event<EndTick> END_SERVER_TICK = null;

	private ServerTickEvents() {
	}

	@FunctionalInterface
	public interface EndTick {
		void onEndTick(MinecraftServer server);
	}
}
