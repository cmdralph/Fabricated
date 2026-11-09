package io.github.cmdralph.fabricated.forge.config;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.io.ParsingException;
import com.electronwill.nightconfig.toml.TomlFormat;

import io.github.cmdralph.fabricated.core.metadata.ModIds;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.IConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLConfig;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Forge Config API Port's Fabric API on Forge 47's and 52's own config system: its registry's configs, which the version's
 * copy of the port's API makes here (version 8's on Forge 47, version 21's Forge half on Forge 52), and whose events the
 * version's copy of the port's events gets from Forge's ({@code FabricConfigEvents}). The port's versions for 1.20.1 and
 * 1.21.1 copy Forge's config system, classes and packages included, so their copies of Forge's classes were left out for
 * Forge's own, whose {@code ModConfig} has none of the port's constructors by mod id, and the port's registry failed to
 * link (CORPUS-81, CFG-4). Configs are made with the Fabric mod's Forge container, under the file names the port uses
 * ({@code <fabric id>-<type>.toml}), and Forge loads, watches, syncs and unloads them as its mods' own.
 *
 * <p>With the port, a config but the server's (per world) is loaded as it is registered, and Fabric mods read its
 * values right after; Forge loads them later (its {@code CONFIG_LOAD}, after the registry events in which the Fabric
 * entrypoints run). Until then the spec reads the file as it is on disk (or the default configs' copy, which Forge's
 * load copies there; missing values at their defaults), without writing it or firing events: Forge's own load does
 * both. A config registered once Forge has loaded those of its type is loaded as Forge loads one, right away.
 */
public final class ForgeConfigApiPortBridge {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	private static volatile Method openConfig;

	private ForgeConfigApiPortBridge() {
	}

	/** The port's registry: a config under the port's file name. */
	public static ModConfig register(String modId, ModConfig.Type type, IConfigSpec<?> spec) {
		return register(modId, type, spec, String.format(Locale.ROOT, "%s-%s.toml", modId, type.extension()));
	}

	/** @throws IllegalArgumentException when no mod has the id, as with the port */
	public static ModConfig register(String modId, ModConfig.Type type, IConfigSpec<?> spec, String fileName) {
		Objects.requireNonNull(modId, "mod id is null");
		ModContainer container = ModList.get().getModContainerById(ModIds.toNeoForgeId(modId))
				.orElseThrow(() -> new IllegalArgumentException("No mod with mod id " + modId));
		// As Forge's ModLoadingContext.registerConfig: the config tracked (a file name taken twice throws, as with the
		// port), and the container's.
		ModConfig config = new ModConfig(type, spec, container, fileName);
		container.addConfig(config);
		if (type != ModConfig.Type.SERVER && (!loadedByForge(type, config) || !open(config))) preload(config, spec);
		return config;
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

	/**
	 * Loads the config as Forge loads each one ({@code ConfigTracker.openConfig}: file, events, watcher), an instance
	 * method on Forge 47 and a static one on Forge 52.
	 */
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
