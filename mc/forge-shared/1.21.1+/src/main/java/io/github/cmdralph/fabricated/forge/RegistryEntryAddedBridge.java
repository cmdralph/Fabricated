package io.github.cmdralph.fabricated.forge;

import net.fabricmc.fabric.api.event.registry.RegistryEntryAddedCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;

/**
 * Fabric API's {@code RegistryEntryAddedCallback} ({@code fabric-registry-sync-v0}, passed through) for the registries
 * Forge manages (CORPUS-108). Upstream fires it at the return of vanilla's {@code MappedRegistry.register}. Forge's
 * {@code NamespacedWrapper}, the vanilla registry of blocks, items, entity types and the others Forge manages, replaces
 * that method with its own, which adds the entry to Forge's registry and never calls vanilla's, and a Forge mod's
 * entries go to Forge's registry alone: the event never fired for those registries (read in Forge 52's, 61 to 66's and
 * Fabric API's sources). Architectury waits on it for an entry registered later than the code that needs it: on Forge
 * 47, Artifacts' spawn egg, made before its entity type, kept a null type, and EMI's, JEI's and REI's listings of its
 * creative tab failed on it. {@code NamespacedWrapperEntryAddedMixin} and {@code ForgeRegistryEntryAddedMixin} call
 * {@link #added} once an entry new to one of them has been added through vanilla's API or Forge's.
 */
public final class RegistryEntryAddedBridge {
	/** Fabric API's registry sync module is installed. Its classes are only touched then. */
	private static final boolean ENABLED = FabricLoader.getInstance().isModLoaded("fabric-registry-sync-v0");

	private RegistryEntryAddedBridge() {
	}

	/**
	 * Fires the event of {@code registry}, the vanilla registry mods hold, for an entry new to it.
	 *
	 * @param rawId the entry's id in Forge's registry, which the vanilla one answers with
	 */
	public static <T> void added(Registry<T> registry, int rawId, Identifier id, T value) {
		if (ENABLED) Upstream.added(registry, rawId, id, value);
	}

	/** Everything that names Fabric's registry sync module: loaded only once {@link #ENABLED} is known to be true. */
	private static final class Upstream {
		private Upstream() {
		}

		static <T> void added(Registry<T> registry, int rawId, Identifier id, T value) {
			RegistryEntryAddedCallback.event(registry).invoker().onEntryAdded(rawId, id, value);
		}
	}
}
