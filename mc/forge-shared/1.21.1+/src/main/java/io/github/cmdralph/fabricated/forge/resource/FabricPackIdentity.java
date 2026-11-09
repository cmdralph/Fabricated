package io.github.cmdralph.fabricated.forge.resource;

import java.util.Optional;

import io.github.cmdralph.fabricated.loader.FabricatedState;
import net.minecraft.server.packs.repository.KnownPack;

/**
 * The known-pack identity of a Fabric mod's own pack on Forge 52 and 61 to 66 (PACK-5, PACK-10). Forge makes a pack of
 * each mod file
 * ({@code ResourcePackLoader.findPacks}) with no known-pack identity, and vanilla marks the registry entries of a pack
 * without one experimental: a world holding a Fabric mod's worldgen, damage types or other registry data then asked the
 * player to confirm experimental settings before it was created, which a Fabric mod's data never asks on Fabric. Fabric
 * API gives every mod pack {@code KnownPack("vanilla", <pack id>, <mod version>)}; a Fabric mod's pack takes the same
 * here, with Forge's pack id. Forge mods' packs keep what Forge gives them.
 */
public final class FabricPackIdentity {
	/** The namespace upstream's mod packs take in their known-pack identity, as on NeoForge (PACK-3). */
	private static final String KNOWN_PACK_NAMESPACE = "vanilla";
	/** The prefix of the id Forge gives a mod file's pack, before its first mod's id. */
	private static final String MOD_PACK_PREFIX = "mod:";

	private FabricPackIdentity() {
	}

	/**
	 * @param name  the pack's id, as Forge names it
	 * @param forge the known-pack identity Forge gives it
	 * @return upstream's identity for the pack of a Fabric mod, otherwise Forge's
	 */
	public static Optional<KnownPack> of(String name, Optional<KnownPack> forge) {
		FabricatedState state = FabricatedState.get();
		if (state == null || forge.isPresent() || !name.startsWith(MOD_PACK_PREFIX)) return forge;
		return state.fabricContainer(name.substring(MOD_PACK_PREFIX.length()))
				.map(container -> new KnownPack(KNOWN_PACK_NAMESPACE, name, container.getMetadata().getVersion().getFriendlyString()))
				.or(() -> forge);
	}
}
