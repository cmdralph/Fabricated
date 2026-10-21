package io.github.cmdralph.fabricated.testmod;

import net.fabricmc.api.ModInitializer;

/**
 * Nothing to test on this version (26.1.2 and 26.2): fabric-biome-api-v1 is one of upstream's modules here, which the test mod's
 * own run doesn't load (26.3's native one is tested, BIOME-1).
 */
public final class BiomeTest implements ModInitializer {
	@Override
	public void onInitialize() {
	}
}
