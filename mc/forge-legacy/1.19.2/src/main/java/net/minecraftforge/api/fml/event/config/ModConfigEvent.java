package net.minecraftforge.api.fml.event.config;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraftforge.fml.config.ModConfig;

/**
 * Forge Config API Port's global config events (its version 4, for 1.19.2), for every mod's configs: fired with each
 * Fabric mod's own ({@link ModConfigEvents}), which listen to Forge's events once loaded.
 */
public final class ModConfigEvent {
	static {
		ModConfigEvents.listen();
	}

	public static final Event<Loading> LOADING = EventFactory.createArrayBacked(Loading.class, listeners -> config -> {
		for (Loading l : listeners) l.onModConfigLoading(config);
	});
	public static final Event<Reloading> RELOADING = EventFactory.createArrayBacked(Reloading.class, listeners -> config -> {
		for (Reloading l : listeners) l.onModConfigReloading(config);
	});

	private ModConfigEvent() {
	}

	@FunctionalInterface
	public interface Loading {
		void onModConfigLoading(ModConfig config);
	}

	@FunctionalInterface
	public interface Reloading {
		void onModConfigReloading(ModConfig config);
	}
}
