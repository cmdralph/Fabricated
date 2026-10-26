package io.github.cmdralph.fabricated.forgetest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The forge-legacy branch's Fabric test mod (docs/PORTING.md §7, L3): its entrypoints and its mixin each log a line the
 * server smoke looks for (ci/forge-legacy/smoke-server.sh). It uses Fabric Loader's API only, as Fabric API isn't on
 * Forge yet.
 */
public final class ForgeTestMod implements ModInitializer, DedicatedServerModInitializer {
	public static final String MOD_ID = "fabricated-forge-testmod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		String version = FabricLoader.getInstance().getModContainer(MOD_ID).map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("none");
		LOGGER.info("FABRICATED-FORGE-TEST main entrypoint OK (version {}, Fabric Loader sees {} mods)", version, FabricLoader.getInstance().getAllMods().size());
	}

	@Override
	public void onInitializeServer() {
		LOGGER.info("FABRICATED-FORGE-TEST server entrypoint OK");
	}
}
