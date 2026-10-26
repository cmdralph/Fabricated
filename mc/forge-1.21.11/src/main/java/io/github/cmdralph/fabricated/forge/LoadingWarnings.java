package io.github.cmdralph.fabricated.forge;

import net.minecraftforge.fml.ModLoader;
import net.minecraftforge.fml.ModLoadingWarning;

/** Forge 61's loading warnings, which its mod loader's instance keeps (Forge 64 on keep them statically: mc/forge-common26). */
final class LoadingWarnings {
	private LoadingWarnings() {
	}

	static void add(ModLoadingWarning warning) {
		ModLoader.get().addWarning(warning);
	}
}
