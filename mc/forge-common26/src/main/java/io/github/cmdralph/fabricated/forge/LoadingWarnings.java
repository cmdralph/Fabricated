package io.github.cmdralph.fabricated.forge;

import net.minecraftforge.fml.ModLoader;
import net.minecraftforge.fml.ModLoadingWarning;

/** Forge's loading warnings from Forge 64 on, which its mod loader keeps statically (Forge 61's: mc/forge-1.21.11). */
final class LoadingWarnings {
	private LoadingWarnings() {
	}

	static void add(ModLoadingWarning warning) {
		ModLoader.addWarning(warning);
	}
}
