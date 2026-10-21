package io.github.cmdralph.fabricated.neoforge.biome;

import net.neoforged.bus.api.IEventBus;

/**
 * The version-specific part of fabric-biome-api-v1 (1.21.11): nothing, upstream's module loads as a Fabric
 * mod there (26.3's runs on NeoForge's biome modifiers, BIOME-1).
 */
public final class BiomeCompat {
	private BiomeCompat() {
	}

	public static void register(IEventBus modBus) {
	}
}
