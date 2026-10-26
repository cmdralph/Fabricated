package net.fabricmc.loader.impl.entrypoint;

import java.util.Map;

import io.github.cmdralph.fabricated.fabricloader.FabricLoaderImpl;
import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.impl.ModContainerImpl;
import net.fabricmc.loader.impl.metadata.EntrypointMetadata;

/**
 * Fabric Loader's internal entrypoint storage, which some mods take from {@code FabricLoaderImpl}'s private field to add
 * entrypoints of their own (FrozenLib, for those its {@code frozenlib.json} files declare). What they add goes to
 * Fabricated's loader, so {@code FabricLoader.getEntrypoints} returns it as on Fabric (LOADER-4).
 */
public final class EntrypointStorage {
	public EntrypointStorage() {
	}

	/**
	 * Adds an entrypoint {@code modContainer} declares under {@code key}, after those the mods' metadata declares. As in
	 * Fabric Loader, the adapter it names must be in {@code adapterMap}; Fabricated's loader creates the entrypoint with
	 * its own adapter of that id.
	 *
	 * @throws Exception if {@code adapterMap} has no adapter of that id
	 */
	public void add(ModContainerImpl modContainer, String key, EntrypointMetadata metadata, Map<String, LanguageAdapter> adapterMap) throws Exception {
		if (!adapterMap.containsKey(metadata.getAdapter())) {
			throw new Exception("Could not find adapter '" + metadata.getAdapter() + "' (mod " + modContainer.getMetadata().getId() + "!)");
		}
		FabricLoaderImpl.internals().addEntrypoint(modContainer, key, metadata.getAdapter(), metadata.getValue());
	}
}
