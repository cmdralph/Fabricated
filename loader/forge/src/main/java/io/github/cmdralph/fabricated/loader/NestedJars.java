package io.github.cmdralph.fabricated.loader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.github.cmdralph.fabricated.core.cache.TranslationCache;
import io.github.cmdralph.fabricated.core.config.FabricatedConfig;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * The jars nested in the Fabricated jar that Forge loads as files of their own (docs/PORTING.md §4.1): the language
 * provider, for its plugin layer, and the in-game mod. Forge reads a mod file from a path, so each is copied into the
 * translation cache, content-addressed, and written once.
 */
final class NestedJars {
	/** The language provider ({@code FMLModType: LANGPROVIDER}). */
	static final String LANG_PROVIDER = "lang-provider.jar";
	/** Fabricated's in-game mod. */
	static final String GAME_MOD = "game-mod.jar";

	private NestedJars() {
	}

	/** The path of a nested jar in the cache, copied there if it isn't yet. */
	static Path extract(String name) throws IOException {
		FabricatedConfig config = FabricatedConfig.load(FMLPaths.CONFIGDIR.get());
		return extract(name, new TranslationCache(FMLPaths.GAMEDIR.get(), config.cacheEnabled(), config.verifyIntegrity()));
	}

	/** The path of a nested jar in {@code cache}, copied there if it isn't yet. */
	static Path extract(String name, TranslationCache cache) throws IOException {
		byte[] data = FabricatedInfo.readOptional(name);
		if (data == null) throw new IOException("The Fabricated jar is missing " + name);
		Path path = cache.storeNested(data);
		if (!Files.isRegularFile(path)) throw new IOException("Could not extract " + name);
		return path;
	}
}
