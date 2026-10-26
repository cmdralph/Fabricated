package io.github.cmdralph.fabricated.forgetest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_1792;
import net.minecraft.class_2378;
import net.minecraft.class_2960;
import net.minecraft.class_7923;

/**
 * The forge-legacy branch's Fabric test mod (docs/PORTING.md §7, L3): its entrypoints and its mixin each log a line the
 * server smoke looks for (ci/forge-legacy/smoke-server.sh). It uses Fabric Loader's API only, as Fabric API isn't on
 * Forge yet, and names the game's classes as a Fabric mod for 1.20.1 does, in Intermediary's names (the stand-ins in
 * src/testmodstubs), which Fabricated remaps to Forge 47's.
 */
public final class ForgeTestMod implements ModInitializer, DedicatedServerModInitializer {
	public static final String MOD_ID = "fabricated-forge-testmod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final String ITEM_PATH = "test_item";
	/** The item {@link #onInitialize} registers, as Fabric mods register theirs: straight into the registry. */
	public static class_1792 testItem;

	@Override
	public void onInitialize() {
		String version = FabricLoader.getInstance().getModContainer(MOD_ID).map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("none");
		LOGGER.info("FABRICATED-FORGE-TEST main entrypoint OK (version {}, Fabric Loader sees {} mods)", version, FabricLoader.getInstance().getAllMods().size());
		try {
			class_2960 id = new class_2960(MOD_ID, ITEM_PATH);
			class_2378<class_1792> items = class_7923.field_41178;
			testItem = class_2378.method_10230(items, id, new class_1792(new class_1792.class_1793()));
			if (id.equals(items.method_10221(testItem))) {
				LOGGER.info("FABRICATED-FORGE-TEST registry OK");
			} else {
				LOGGER.error("FABRICATED-FORGE-TEST registry gave the item another key: {}", items.method_10221(testItem));
			}
		} catch (RuntimeException | LinkageError e) {
			LOGGER.error("FABRICATED-FORGE-TEST registry failed", e);
		}
	}

	@Override
	public void onInitializeServer() {
		LOGGER.info("FABRICATED-FORGE-TEST server entrypoint OK");
		if (Arrays.stream(ForgeTestMod.class.getDeclaredMethods()).noneMatch(m -> m.getName().equals("clientOnly"))) {
			LOGGER.info("FABRICATED-FORGE-TEST environment strip OK");
		}
	}

	/** Reserved for the client: Fabric's environment stripping takes it out of the class on a dedicated server. */
	@Environment(EnvType.CLIENT)
	static void clientOnly() {
	}
}
