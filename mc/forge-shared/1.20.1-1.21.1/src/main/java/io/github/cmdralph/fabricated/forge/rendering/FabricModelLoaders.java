package io.github.cmdralph.fabricated.forge.rendering;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.cmdralph.fabricated.core.metadata.ModIds;
import io.github.cmdralph.fabricated.loader.FabricatedState;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.model.geometry.GeometryLoaderManager;
import net.minecraftforge.fml.ModList;

/**
 * Which block models' {@code loader} Forge 47 and 52 read (CORPUS-92, CORPUS-102, MODEL-5). Forge reads that key in
 * every block model, and fails the model when no loader of that name is in its own registry. On Fabric, vanilla ignores
 * the key, and a Fabric mod reads its own loaders through its hook into vanilla's model reader (Moonlight's for
 * Supplementaries' loaders, Porting Lib's for {@code porting_lib:obj} and the rest). Forge's reader calls vanilla's
 * first, so those hooks have run when Forge looks the loader up.
 *
 * <p>So Forge reads a loader it has, and one named in a Forge mod's namespace (or Forge's, or the game's), whose
 * failure is that mod's, as without Fabricated. Any other loader, in a Fabric mod's namespace or no loaded mod's, is
 * left to the Fabric mods' hooks, and the model is what vanilla's reader and those hooks made of it, as on Fabric.
 */
public final class FabricModelLoaders {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	private static final Set<ResourceLocation> LEFT = ConcurrentHashMap.newKeySet();

	private FabricModelLoaders() {
	}

	/** Whether Forge reads the model's geometry: its own reading, with its own error for a loader it lacks. */
	public static boolean forgeReads(JsonObject model) {
		JsonElement loader = model.get("loader");
		// No loader, or one Forge's own reading refuses (not a string, not a valid name): Forge's, as before.
		if (loader == null || !loader.isJsonPrimitive() || !loader.getAsJsonPrimitive().isString()) return true;
		ResourceLocation name = ResourceLocation.tryParse(loader.getAsString());
		if (name == null || GeometryLoaderManager.get(name) != null || isPlatformMods(name.getNamespace())) return true;
		if (LEFT.add(name)) {
			LOGGER.info("Model loader {} is neither Forge's nor a Forge mod's: its models are read as on Fabric, where vanilla "
					+ "ignores the loader and a Fabric mod's own hook reads it (CORPUS-92)", name);
		}
		return false;
	}

	/** Whether the namespace is the game's, Forge's or a Forge mod's: a mod the platform loads that isn't a Fabric mod. */
	private static boolean isPlatformMods(String namespace) {
		return ModList.get().isLoaded(namespace) && !FabricatedState.get().isFabricMod(ModIds.toNeoForgeId(namespace));
	}
}
