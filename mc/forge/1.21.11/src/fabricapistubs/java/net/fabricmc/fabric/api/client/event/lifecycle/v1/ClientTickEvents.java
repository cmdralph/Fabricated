package net.fabricmc.fabric.api.client.event.lifecycle.v1;

import net.minecraft.client.Minecraft;

import net.fabricmc.fabric.api.event.Event;

/** Stand-in for fabric-lifecycle-events-v1's, which Fabric API's jar provides on Forge 61 (intermediary names): compile only. */
public final class ClientTickEvents {
	public static final Event<StartTick> START_CLIENT_TICK = null;
	public static final Event<EndTick> END_CLIENT_TICK = null;

	private ClientTickEvents() {
	}

	@FunctionalInterface
	public interface StartTick {
		void onStartTick(Minecraft client);
	}

	@FunctionalInterface
	public interface EndTick {
		void onEndTick(Minecraft client);
	}
}
