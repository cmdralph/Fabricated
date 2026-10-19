package io.github.cmdralph.fabricated.neoforge.config;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fuzs.forgeconfigapiport.fabric.api.v5.ConfigRegistry;
import fuzs.forgeconfigapiport.fabric.api.v5.ModConfigEvents;
import io.github.cmdralph.fabricated.core.metadata.ModIds;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;

/**
 * Forge Config API Port's Fabric API on NeoForge. The port is a copy of NeoForge's config system for Fabric, so on
 * NeoForge its jar is replaced by this: configs go to the Fabric mod's NeoForge container (with the port's file names,
 * {@code <fabric id>-<type>.toml}) and its events come from NeoForge's config events; its screens are
 * {@link ForgeConfigApiPortScreens}.
 */
public final class ForgeConfigApiPortBridge {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	private static final Map<String, Events> EVENTS = new ConcurrentHashMap<>();

	public static final ConfigRegistry REGISTRY = new ConfigRegistry() {
		@Override
		public void register(String modId, ModConfig.Type type, IConfigSpec spec) {
			register(modId, type, spec, String.format(Locale.ROOT, "%s-%s.toml", modId, type.extension()));
		}

		@Override
		public void register(String modId, ModConfig.Type type, IConfigSpec spec, String fileName) {
			if (spec.isEmpty()) return; // as the port: nothing to write
			container(modId).ifPresentOrElse(container -> container.registerConfig(type, spec, fileName),
					() -> LOGGER.warn("Config {} of {} is not registered: no mod has that id", fileName, modId));
		}
	};

	/** A mod's config events; NeoForge's are forwarded to them from the mod's event bus. */
	public record Events(Event<ModConfigEvents.Loading> loading, Event<ModConfigEvents.Reloading> reloading,
			Event<ModConfigEvents.Unloading> unloading) {
	}

	private ForgeConfigApiPortBridge() {
	}

	public static Events events(String modId) {
		return EVENTS.computeIfAbsent(modId, id -> {
			Events events = new Events(
					EventFactory.createArrayBacked(ModConfigEvents.Loading.class, listeners -> config -> {
						for (ModConfigEvents.Loading l : listeners) l.onModConfigLoading(config);
					}),
					EventFactory.createArrayBacked(ModConfigEvents.Reloading.class, listeners -> config -> {
						for (ModConfigEvents.Reloading l : listeners) l.onModConfigReloading(config);
					}),
					EventFactory.createArrayBacked(ModConfigEvents.Unloading.class, listeners -> config -> {
						for (ModConfigEvents.Unloading l : listeners) l.onModConfigUnloading(config);
					}));
			IEventBus bus = container(id).map(ModContainer::getEventBus).orElse(null);
			if (bus == null) {
				LOGGER.warn("Config events of {} never fire: no mod has that id", id);
			} else {
				bus.addListener(ModConfigEvent.Loading.class, e -> events.loading().invoker().onModConfigLoading(e.getConfig()));
				bus.addListener(ModConfigEvent.Reloading.class, e -> events.reloading().invoker().onModConfigReloading(e.getConfig()));
				bus.addListener(ModConfigEvent.Unloading.class, e -> events.unloading().invoker().onModConfigUnloading(e.getConfig()));
			}
			return events;
		});
	}


	static Optional<? extends ModContainer> container(String fabricModId) {
		return ModList.get().getModContainerById(ModIds.toNeoForgeId(fabricModId));
	}
}
