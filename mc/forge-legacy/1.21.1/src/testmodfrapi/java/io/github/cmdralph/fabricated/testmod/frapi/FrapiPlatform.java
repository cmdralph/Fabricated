package io.github.cmdralph.fabricated.testmod.frapi;

import java.util.List;

/**
 * What the renderer test mod's shared checks (mc/shared) ask of the platform they run on, Forge 52 here (1.21.1). Every
 * platform's renderer test mod for 1.21.1 has this class with these members, so the checks compile against each
 * platform's.
 */
final class FrapiPlatform {
	/** The class through which Fabricated sends a block to Fabric's renderer on this platform. */
	static final String FABRICATED_SEAM = "io.github.cmdralph.fabricated.forge.rendering.FabricBlockRendering";
	/** The platform's namespace, where a model naming a loader the platform doesn't know fails. */
	static final String NAMESPACE = "forge";

	private FrapiPlatform() {
	}

	/** The forms in which a model may name this loader as its {@code loader}: Forge reads a name. */
	static List<String> loaderKeys(String loader) {
		return List.of("\"" + loader + "\"");
	}
}
