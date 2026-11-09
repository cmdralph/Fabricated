package fuzs.forgeconfigapiport.fabric.api.forge.v4;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import io.github.cmdralph.fabricated.core.metadata.ModIds;
import io.github.cmdralph.fabricated.forge.FabricConfigEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraftforge.fml.config.IConfigEvent;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * Forge Config API Port's per-mod events for Forge's config system (its version 21, for 1.21.1), fired from Forge 52's
 * {@code ModConfigEvent}s for the mod's configs, which Forge hands the mod's container ({@link FabricConfigEvents}).
 */
public final class ForgeModConfigEvents {
	/** By the mod's Forge id, which is how Forge's events name it. */
	private static final Map<String, Events> EVENTS = new ConcurrentHashMap<>();

	static {
		FabricConfigEvents.listen(ForgeModConfigEvents::dispatch);
	}

	private ForgeModConfigEvents() {
	}

	public static Event<Loading> loading(String modId) {
		return events(modId).loading();
	}

	public static Event<Reloading> reloading(String modId) {
		return events(modId).reloading();
	}

	public static Event<Unloading> unloading(String modId) {
		return events(modId).unloading();
	}

	private static Events events(String modId) {
		return EVENTS.computeIfAbsent(ModIds.toNeoForgeId(Objects.requireNonNull(modId, "mod id is null")), id -> new Events(
				EventFactory.createArrayBacked(Loading.class, listeners -> config -> {
					for (Loading l : listeners) l.onModConfigLoading(config);
				}),
				EventFactory.createArrayBacked(Reloading.class, listeners -> config -> {
					for (Reloading l : listeners) l.onModConfigReloading(config);
				}),
				EventFactory.createArrayBacked(Unloading.class, listeners -> config -> {
					for (Unloading l : listeners) l.onModConfigUnloading(config);
				})));
	}

	/** Forge's event for a config of a Fabric mod (by its Forge id), as the port's. */
	private static void dispatch(String forgeId, IConfigEvent event) {
		Events events = EVENTS.get(forgeId);
		if (events == null) return;
		ModConfig config = event.getConfig();
		if (event instanceof ModConfigEvent.Loading) {
			events.loading().invoker().onModConfigLoading(config);
		} else if (event instanceof ModConfigEvent.Reloading) {
			events.reloading().invoker().onModConfigReloading(config);
		} else if (event instanceof ModConfigEvent.Unloading) {
			events.unloading().invoker().onModConfigUnloading(config);
		}
	}

	/** A mod's config events, which Forge's for its configs fire. */
	private record Events(Event<Loading> loading, Event<Reloading> reloading, Event<Unloading> unloading) {
	}

	@FunctionalInterface
	public interface Loading {
		void onModConfigLoading(ModConfig config);
	}

	@FunctionalInterface
	public interface Reloading {
		void onModConfigReloading(ModConfig config);
	}

	@FunctionalInterface
	public interface Unloading {
		void onModConfigUnloading(ModConfig config);
	}
}
