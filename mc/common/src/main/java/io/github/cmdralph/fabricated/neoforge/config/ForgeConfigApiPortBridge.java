package io.github.cmdralph.fabricated.neoforge.config;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.io.ParsingException;
import com.electronwill.nightconfig.toml.TomlFormat;

import fuzs.forgeconfigapiport.fabric.api.v5.ConfigRegistry;
import fuzs.forgeconfigapiport.fabric.api.v5.ModConfigEvents;
import io.github.cmdralph.fabricated.core.metadata.ModIds;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.config.ConfigTracker;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Forge Config API Port's Fabric API on NeoForge. The port is a copy of NeoForge's config system for Fabric, so on
 * NeoForge its jar is replaced by this: configs go to the Fabric mod's NeoForge container (with the port's file names,
 * {@code <fabric id>-<type>.toml}) and its events come from NeoForge's config events; its screens are
 * {@link ForgeConfigApiPortScreens}.
 *
 * <p>On Fabric the port loads every config but the per-world one as it is registered, and Fabric mods read their
 * values right after; NeoForge loads them later in mod loading. Until then the spec reads the file as it is on disk
 * (missing values at their defaults), without writing it or firing events: NeoForge's own load does both. A config
 * registered once NeoForge has loaded those of its type (on a client, Fabric initializers run in {@code Minecraft}'s
 * constructor, after that: CORPUS-16) is loaded as NeoForge loads one, right away.
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
			Optional<? extends ModContainer> container = container(modId);
			if (container.isEmpty()) {
				LOGGER.warn("Config {} of {} is not registered: no mod has that id", fileName, modId);
				return;
			}
			ModConfig config = ConfigTracker.INSTANCE.registerConfig(type, spec, container.get(), fileName);
			if (type == ModConfig.Type.STARTUP || perWorld(type)) return;
			if (!loadedByNeoForge(type) || !open(config)) preload(config);
		}
	};

	private static volatile Constructor<?> loadedConfig;
	private static volatile Method openConfig;

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


	/** The server config (FML 12 calls it synced): loaded with each world, also by the port. */
	private static boolean perWorld(ModConfig.Type type) {
		return type.name().equals("SERVER") || type.name().equals("SYNCED");
	}

	/** Whether NeoForge has loaded the configs of this type (its own among them). */
	private static boolean loadedByNeoForge(ModConfig.Type type) {
		return ModConfigs.getConfigSet(type).stream().anyMatch(config -> config.getLoadedConfig() != null);
	}

	/** Loads the config as NeoForge loads each one ({@code ConfigTracker.openConfig}: file, events, watcher). */
	private static boolean open(ModConfig config) {
		try {
			Method method = openConfig;
			if (method == null) {
				method = ConfigTracker.class.getDeclaredMethod("openConfig", ModConfig.class, Path.class, Path.class);
				method.setAccessible(true);
				openConfig = method;
			}
			method.invoke(null, config, FMLPaths.CONFIGDIR.get(), null);
			return true;
		} catch (ReflectiveOperationException | RuntimeException e) {
			LOGGER.warn("Config {} of {} is read from its file but not loaded as NeoForge loads configs: {}", config.getFileName(), config.getModId(), e.toString());
			return false;
		}
	}

	private static void preload(ModConfig config) {
		Path file = FMLPaths.CONFIGDIR.get().resolve(config.getFileName());
		CommentedConfig values;
		try (Reader reader = Files.newBufferedReader(file)) {
			values = TomlFormat.instance().createParser().parse(reader);
		} catch (NoSuchFileException e) {
			values = CommentedConfig.inMemory();
		} catch (IOException | ParsingException e) {
			LOGGER.warn("Config {} of {} is read at its defaults until NeoForge loads it: {}", config.getFileName(), config.getModId(), e.toString());
			values = CommentedConfig.inMemory();
		}
		if (!config.getSpec().isCorrect(values)) config.getSpec().correct(values);
		try {
			Constructor<?> ctor = loadedConfig;
			if (ctor == null) {
				// FML's only ILoadedConfig; with no path it never writes the file.
				ctor = Class.forName("net.neoforged.fml.config.LoadedConfig").getDeclaredConstructor(CommentedConfig.class, Path.class, ModConfig.class);
				ctor.setAccessible(true);
				loadedConfig = ctor;
			}
			config.getSpec().acceptConfig((IConfigSpec.ILoadedConfig) ctor.newInstance(values, null, config));
		} catch (ReflectiveOperationException | RuntimeException e) {
			LOGGER.warn("Config {} of {} can't be read until NeoForge loads it: {}", config.getFileName(), config.getModId(), e.toString());
		}
	}

	/**
	 * The NeoForge container of a Fabric mod id, for Fabric mods that open the port's {@code ConfigurationScreen},
	 * which takes the id where NeoForge's takes the container (translation passes the container instead).
	 */
	public static ModContainer modContainer(String fabricModId) {
		return container(fabricModId).orElseThrow(() -> new IllegalArgumentException("No mod has the id " + fabricModId));
	}

	static Optional<? extends ModContainer> container(String fabricModId) {
		return ModList.get().getModContainerById(ModIds.toNeoForgeId(fabricModId));
	}
}
