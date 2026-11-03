package io.github.cmdralph.fabricated.forge.config;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.io.ParsingException;
import com.electronwill.nightconfig.toml.TomlFormat;

import fuzs.forgeconfigapiport.api.config.v2.ForgeConfigPaths;
import fuzs.forgeconfigapiport.api.config.v2.ForgeConfigRegistry;
import fuzs.forgeconfigapiport.api.config.v2.ModConfigEvents;
import io.github.cmdralph.fabricated.core.metadata.ModIds;
import io.github.cmdralph.fabricated.forge.FabricConfigEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.IConfigEvent;
import net.minecraftforge.fml.config.IConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.loading.FMLConfig;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Forge Config API Port's Fabric API (version 8, for 1.20.1) on Forge 47. The port is a copy of Forge's config system
 * for Fabric, classes and packages included, so on Forge its jar is left out for this (CORPUS-81): with the jar loaded,
 * its copies of Forge's classes were left out for Forge's own, whose {@code ModConfig} has none of the port's
 * constructors by mod id, and the port's registry failed to link (Ponder's main entrypoint). Configs are made with the
 * Fabric mod's Forge container, under the file names the port uses ({@code <fabric id>-<type>.toml}), and Forge loads,
 * watches, syncs and unloads them as its mods' own; their events, which Forge hands the container
 * ({@link FabricConfigEvents}), fire the port's per-mod events.
 *
 * <p>With the port, a config but the server's (per world) is loaded as it is registered, and Fabric mods read its
 * values right after; Forge loads them later (its {@code CONFIG_LOAD}, after the registry events in which the Fabric
 * entrypoints run). Until then the spec reads the file as it is on disk (or the default configs' copy, which Forge's
 * load copies there; missing values at their defaults), without writing it or firing events: Forge's own load does
 * both. A config registered once Forge has loaded those of its type is loaded as Forge loads one, right away.
 */
public final class ForgeConfigApiPortBridge {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	/** By the mod's Forge id, which is how Forge's events name it. */
	private static final Map<String, Events> EVENTS = new ConcurrentHashMap<>();
	/** Where Forge keeps a world's server configs (its {@code ServerLifecycleHooks}). */
	private static final LevelResource SERVER_CONFIG = new LevelResource("serverconfig");
	private static volatile Method openConfig;

	public static final ForgeConfigRegistry REGISTRY = new ForgeConfigRegistry() {
		@Override
		public ModConfig register(String modId, ModConfig.Type type, IConfigSpec<?> spec) {
			return register(modId, type, spec, String.format(Locale.ROOT, "%s-%s.toml", modId, type.extension()));
		}

		@Override
		public ModConfig register(String modId, ModConfig.Type type, IConfigSpec<?> spec, String fileName) {
			Objects.requireNonNull(modId, "mod id is null");
			ModContainer container = ModList.get().getModContainerById(ModIds.toNeoForgeId(modId))
					.orElseThrow(() -> new IllegalArgumentException("No mod with mod id " + modId));
			// As Forge's ModLoadingContext.registerConfig: the config tracked (a file name taken twice throws, as with
			// the port), and the container's.
			ModConfig config = new ModConfig(type, spec, container, fileName);
			container.addConfig(config);
			if (type != ModConfig.Type.SERVER && (!loadedByForge(type, config) || !open(config))) preload(config, spec);
			return config;
		}
	};

	public static final ForgeConfigPaths PATHS = new ForgeConfigPaths() {
		@Override
		public Path getClientConfigDirectory() {
			return FMLPaths.CONFIGDIR.get();
		}

		@Override
		public Path getCommonConfigDirectory() {
			return FMLPaths.CONFIGDIR.get();
		}

		@Override
		public Path getServerConfigDirectory(MinecraftServer server) {
			Path directory = server.getWorldPath(SERVER_CONFIG);
			try {
				Files.createDirectories(directory);
			} catch (IOException e) {
				throw new UncheckedIOException("Problem creating the server config directory " + directory, e);
			}
			return directory;
		}

		/** Forge has no such setting: server configs are per world. */
		@Override
		public boolean forceGlobalServerConfigs() {
			return false;
		}

		@Override
		public Path getDefaultConfigsDirectory() {
			return FMLPaths.getOrCreateGameRelativePath(Path.of(FMLConfig.defaultConfigPath()));
		}
	};

	static {
		FabricConfigEvents.listen(ForgeConfigApiPortBridge::dispatch);
	}

	/** A mod's config events, which Forge's for its configs fire. */
	public record Events(Event<ModConfigEvents.Loading> loading, Event<ModConfigEvents.Reloading> reloading,
			Event<ModConfigEvents.Unloading> unloading) {
	}

	private ForgeConfigApiPortBridge() {
	}

	public static Events events(String modId) {
		return EVENTS.computeIfAbsent(ModIds.toNeoForgeId(modId), id -> new Events(
				EventFactory.createArrayBacked(ModConfigEvents.Loading.class, listeners -> config -> {
					for (ModConfigEvents.Loading l : listeners) l.onModConfigLoading(config);
				}),
				EventFactory.createArrayBacked(ModConfigEvents.Reloading.class, listeners -> config -> {
					for (ModConfigEvents.Reloading l : listeners) l.onModConfigReloading(config);
				}),
				EventFactory.createArrayBacked(ModConfigEvents.Unloading.class, listeners -> config -> {
					for (ModConfigEvents.Unloading l : listeners) l.onModConfigUnloading(config);
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

	/** Whether Forge has loaded the configs of this type (its own among them). */
	private static boolean loadedByForge(ModConfig.Type type, ModConfig registering) {
		Set<ModConfig> configs = ConfigTracker.INSTANCE.configSets().get(type);
		synchronized (configs) {
			for (ModConfig config : configs) {
				if (config != registering && config.getConfigData() != null) return true;
			}
		}
		return false;
	}

	/** Loads the config as Forge loads each one ({@code ConfigTracker.openConfig}: file, events, watcher). */
	private static boolean open(ModConfig config) {
		try {
			Method method = openConfig;
			if (method == null) {
				method = ConfigTracker.class.getDeclaredMethod("openConfig", ModConfig.class, Path.class);
				method.setAccessible(true);
				openConfig = method;
			}
			method.invoke(ConfigTracker.INSTANCE, config, FMLPaths.CONFIGDIR.get());
			return true;
		} catch (ReflectiveOperationException | RuntimeException e) {
			LOGGER.warn("Config {} of {} is read from its file but not loaded as Forge loads configs: {}", config.getFileName(), config.getModId(), e.toString());
			return false;
		}
	}

	private static void preload(ModConfig config, IConfigSpec<?> spec) {
		Path file = FMLPaths.CONFIGDIR.get().resolve(config.getFileName());
		// Forge's load copies the default configs' file when the config has none (ConfigFileTypeHandler).
		if (!Files.exists(file)) file = FMLPaths.GAMEDIR.get().resolve(FMLConfig.defaultConfigPath()).resolve(config.getFileName());
		CommentedConfig values;
		try (Reader reader = Files.newBufferedReader(file)) {
			values = TomlFormat.instance().createParser().parse(reader);
		} catch (NoSuchFileException e) {
			values = CommentedConfig.inMemory();
		} catch (IOException | ParsingException e) {
			LOGGER.warn("Config {} of {} is read at its defaults until Forge loads it: {}", config.getFileName(), config.getModId(), e.toString());
			values = CommentedConfig.inMemory();
		}
		try {
			if (!spec.isCorrect(values)) spec.correct(values);
			// In memory: the spec never writes it (Forge's load writes the file).
			spec.acceptConfig(values);
		} catch (RuntimeException e) {
			LOGGER.warn("Config {} of {} can't be read until Forge loads it: {}", config.getFileName(), config.getModId(), e.toString());
		}
	}
}
