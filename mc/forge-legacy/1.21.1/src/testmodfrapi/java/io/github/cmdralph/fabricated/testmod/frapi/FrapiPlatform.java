package io.github.cmdralph.fabricated.testmod.frapi;

/**
 * What the renderer test mod's shared checks (mc/shared) ask of the platform they run on, Forge 52 here (1.21.1). Every
 * platform's renderer test mod for 1.21.1 has this class with these members, so the checks compile against each
 * platform's.
 */
final class FrapiPlatform {
	/**
	 * The class through which Fabricated would send a block to Fabric's renderer on this platform, as on Forge 61 to 66:
	 * Forge 52 has no such seam yet, so the checks that ask for it fail there.
	 */
	static final String FABRICATED_SEAM = "io.github.cmdralph.fabricated.forge.rendering.FabricBlockRendering";

	private FrapiPlatform() {
	}
}
