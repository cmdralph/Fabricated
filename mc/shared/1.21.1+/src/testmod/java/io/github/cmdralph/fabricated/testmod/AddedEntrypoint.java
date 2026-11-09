package io.github.cmdralph.fabricated.testmod;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.ModContainerImpl;
import net.fabricmc.loader.impl.entrypoint.EntrypointStorage;
import net.fabricmc.loader.impl.metadata.EntrypointMetadata;

/**
 * An entrypoint the test mod declares at run time, as FrozenLib declares those of its {@code frozenlib.json} files
 * (LOADER-4): the mod's mixin plugin adds it to Fabric Loader's internal storage, taken from {@code FabricLoaderImpl}'s
 * private fields, and the main entrypoint asks Fabric's API for it. Loads no game class: the plugin runs before the game.
 */
public final class AddedEntrypoint implements Runnable {
	static final String KEY = "fabricated-test:added";
	private static final Logger LOGGER = LoggerFactory.getLogger(AddedEntrypoint.class);

	@Override
	public void run() {
		LOGGER.info("FABRICATED-TEST added entrypoint OK");
	}

	/** Adds this class under {@link #KEY} for the test mod, as FrozenLib's injector does. */
	@SuppressWarnings("unchecked")
	static void add() {
		try {
			Field storageField = FabricLoaderImpl.class.getDeclaredField("entrypointStorage");
			storageField.setAccessible(true);
			Field adapterField = FabricLoaderImpl.class.getDeclaredField("adapterMap");
			adapterField.setAccessible(true);
			EntrypointStorage storage = (EntrypointStorage) storageField.get(FabricLoaderImpl.INSTANCE);
			Map<String, LanguageAdapter> adapters = (Map<String, LanguageAdapter>) adapterField.get(FabricLoaderImpl.INSTANCE);
			Optional<ModContainer> self = FabricLoader.getInstance().getModContainer("fabricated-testmod");
			if (!(self.orElse(null) instanceof ModContainerImpl container)) {
				LOGGER.error("FABRICATED-TEST added entrypoint FAILED: the mod's container {} isn't Fabric Loader's ModContainerImpl", self);
				return;
			}
			storage.add(container, KEY, new EntrypointMetadata() {
				@Override
				public String getAdapter() {
					return "default";
				}

				@Override
				public String getValue() {
					return AddedEntrypoint.class.getName();
				}
			}, adapters);
		} catch (Exception e) {
			LOGGER.error("FABRICATED-TEST added entrypoint FAILED: Fabric Loader's entrypoint storage took nothing", e);
		}
	}
}
