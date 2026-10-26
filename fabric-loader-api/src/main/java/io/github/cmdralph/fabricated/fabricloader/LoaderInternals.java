package io.github.cmdralph.fabricated.fabricloader;

import java.util.Map;

import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.ModContainer;

/**
 * What Fabricated's loader does for the parts of Fabric Loader's internals mods use, beyond its public API: its
 * entrypoint storage and its language adapters ({@code net.fabricmc.loader.impl.FabricLoaderImpl}'s
 * {@code entrypointStorage} and {@code adapterMap}, which mods read by reflection to add entrypoints of their own).
 * The installed {@link net.fabricmc.loader.api.FabricLoader} implements it.
 */
public interface LoaderInternals {
	/**
	 * Adds an entrypoint {@code mod} declares at run time under {@code key}, created by the language adapter
	 * {@code adapter} from {@code value}: it is then among the loader's entrypoints for the key, after those the mods'
	 * metadata declares.
	 *
	 * @throws IllegalArgumentException if {@code mod} isn't a mod this loader loaded
	 */
	void addEntrypoint(ModContainer mod, String key, String adapter, String value);

	/** The language adapters created so far, by id ({@code default} among them); read-only. */
	Map<String, LanguageAdapter> languageAdapters();
}
