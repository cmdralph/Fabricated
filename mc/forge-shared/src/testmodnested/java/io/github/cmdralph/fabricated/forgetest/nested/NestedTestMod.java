package io.github.cmdralph.fabricated.forgetest.nested;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;

/**
 * A Fabric mod the Forge test mod nests as a jar built for Fabric and NeoForge at once nests its libraries: in
 * {@code META-INF/jars}, listed by {@code fabric.mod.json} and by a NeoForge jar-in-jar list
 * ({@code META-INF/jarjar/metadata.json}). Fabricated translates it; Forge's jar-in-jar locator, which reads that list
 * in every mod file, must not add it again untranslated (JIJ-1). Then its class is in Fabricated's module of it, the
 * one module of the game's layer with its package.
 */
public final class NestedTestMod implements ModInitializer {
	private static final Logger LOGGER = LoggerFactory.getLogger("fabricated_forge_nested");

	@Override
	public void onInitialize() {
		Module module = NestedTestMod.class.getModule();
		String pkg = NestedTestMod.class.getPackageName();
		long copies = module.getLayer() == null ? 0 : module.getLayer().modules().stream().filter(m -> m.getPackages().contains(pkg)).count();
		if ("fabricated_forge_nested".equals(module.getName()) && copies == 1) {
			LOGGER.info("FABRICATED-FORGE-TEST nested jar loaded once OK");
		} else {
			LOGGER.error("FABRICATED-FORGE-TEST nested jar: its class is in module {}, and {} module(s) of its layer have its package",
					module.getName(), copies);
		}
	}
}
