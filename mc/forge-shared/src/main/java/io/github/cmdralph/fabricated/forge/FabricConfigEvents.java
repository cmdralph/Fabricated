package io.github.cmdralph.fabricated.forge;

import java.util.Objects;
import java.util.function.BiConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraftforge.fml.config.IConfigEvent;

/**
 * Forge's config events for the configs made with a Fabric mod's container ({@link FabricModContainer}). Forge hands a
 * config's events to its mod's container, whose own containers post them on the mod's event bus; Fabric mods have none,
 * so the events go to the listener the version's in-game mod sets: on Forge 47, Forge Config API Port's per-mod events,
 * whose API the in-game mod provides on Forge's config system (CORPUS-81). With no listener they're dropped, as Forge's
 * own containers drop those of a mod with no config handler.
 */
public final class FabricConfigEvents {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	private static volatile BiConsumer<String, IConfigEvent> listener;

	private FabricConfigEvents() {
	}

	/** Sets the listener, which gets each event with the Forge id of its config's mod. Set once. */
	public static synchronized void listen(BiConsumer<String, IConfigEvent> to) {
		Objects.requireNonNull(to, "listener");
		if (listener != null && listener != to) throw new IllegalStateException("Fabric mods' config events already have a listener");
		listener = to;
	}

	/**
	 * Hands the event to the listener. One mod's failing listener doesn't stop the loading of the other mods' configs,
	 * which Forge does in one pass: it is logged as that mod's, as a failing Fabric entrypoint is.
	 */
	static void dispatch(String modId, IConfigEvent event) {
		BiConsumer<String, IConfigEvent> to = listener;
		if (to == null || event == null) return;
		try {
			to.accept(modId, event);
		} catch (RuntimeException | LinkageError e) {
			LOGGER.error("Config event {} of {} ({}) failed", event.getClass().getSimpleName(), modId, event.getConfig().getFileName(), e);
		}
	}
}
