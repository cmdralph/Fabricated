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
import net.minecraft.class_2338;
import net.minecraft.class_2378;
import net.minecraft.class_2960;
import net.minecraft.class_3218;
import net.minecraft.class_7923;

/**
 * The forge-legacy branch's Fabric test mod (docs/PORTING.md §7, L3): its entrypoints and its mixin each log a line the
 * server smoke looks for (ci/forge-legacy/smoke-server.sh). It uses Fabric Loader's API, and of Fabric API only the
 * modules Fabricated provides itself (fabric-biome-api-v1, fabric-dimensions-v1, fabric-loot-api-v2,
 * fabric-loot-tables-v1 and fabric-particles-v1), as the smoke runs without Fabric API's jar too. It registers fluids
 * as Fabric mods do, and names the game's classes as a Fabric mod for 1.20.1 does, in Intermediary's names (the
 * stand-ins in src/testmodstubs), which Fabricated remaps to Forge 47's. With Fabric API's jar it also tries modules that
 * jar provides: the command, networking, block break ({@link ForgeBlockBreakTest}) and sleep ({@link ForgeSleepTest})
 * events, and storages reaching Forge's capabilities ({@link ForgeTransferTest}).
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
		// Vanilla's resources are the minecraft container's too: Fabric API's resource loader reads vanilla's en_us.json
		// there as the game boots.
		if (FabricLoader.getInstance().getModContainer("minecraft").flatMap(c -> c.findPath("/assets/minecraft/lang/en_us.json")).isPresent()) {
			LOGGER.info("FABRICATED-FORGE-TEST game resources OK");
		}
		try {
			class_2960 id = new class_2960(MOD_ID, ITEM_PATH);
			class_2378<class_1792> items = class_7923.field_41178;
			testItem = class_2378.method_10230(items, id, new class_1792(new class_1792.class_1793()));
			if (id.equals(items.method_10221(testItem))) {
				LOGGER.info("FABRICATED-FORGE-TEST registry OK");
				// Item.maxStackSize is private: reading it fails to link unless the access widener reached Forge.
				if (testItem.field_8013 == 64) LOGGER.info("FABRICATED-FORGE-TEST access widener OK");
			} else {
				LOGGER.error("FABRICATED-FORGE-TEST registry gave the item another key: {}", items.method_10221(testItem));
			}
		} catch (RuntimeException | LinkageError e) {
			LOGGER.error("FABRICATED-FORGE-TEST registry failed", e);
		}
		try {
			ForgeBootstrapContent.register();
		} catch (RuntimeException | LinkageError e) {
			LOGGER.error("FABRICATED-FORGE-TEST content built at bootstrap failed", e);
		}
		try {
			ForgeBiomeTest.register();
		} catch (RuntimeException | LinkageError e) {
			LOGGER.error("FABRICATED-FORGE-TEST biome modifications failed", e);
		}
		try {
			ForgeLootTest.register();
		} catch (RuntimeException | LinkageError e) {
			LOGGER.error("FABRICATED-FORGE-TEST loot table events failed", e);
		}
		try {
			ForgeFluidTest.register();
		} catch (RuntimeException | LinkageError e) {
			LOGGER.error("FABRICATED-FORGE-TEST fluid registration failed", e);
		}
		try {
			ForgeCommandTest.register();
		} catch (RuntimeException | LinkageError e) {
			LOGGER.error("FABRICATED-FORGE-TEST command registration failed", e);
		}
		try {
			ForgeParticleTest.register();
		} catch (RuntimeException | LinkageError e) {
			LOGGER.error("FABRICATED-FORGE-TEST particle type registration failed", e);
		}
		try {
			ForgeBlockBreakTest.register();
		} catch (RuntimeException | LinkageError e) {
			LOGGER.error("FABRICATED-FORGE-TEST block break listeners not registered", e);
		}
		try {
			ForgeSleepTest.register();
		} catch (RuntimeException | LinkageError e) {
			LOGGER.error("FABRICATED-FORGE-TEST sleep listeners not registered", e);
		}
		try {
			ForgeTransferTest.register();
		} catch (RuntimeException | LinkageError e) {
			LOGGER.error("FABRICATED-FORGE-TEST storage blocks not registered", e);
		}
		if (FabricLoader.getInstance().isModLoaded("fabric-networking-api-v1")) {
			try {
				ForgeNetworkingTest.register();
			} catch (RuntimeException | LinkageError e) {
				LOGGER.error("FABRICATED-FORGE-TEST networking receiver not registered", e);
			}
		}
	}

	@Override
	public void onInitializeServer() {
		LOGGER.info("FABRICATED-FORGE-TEST server entrypoint OK");
		if (Arrays.stream(ForgeTestMod.class.getDeclaredMethods()).noneMatch(m -> m.getName().equals("clientOnly"))) {
			LOGGER.info("FABRICATED-FORGE-TEST environment strip OK");
		}
	}

	/**
	 * Where a check at the first server tick builds: {@code dx} and {@code dz} blocks from the overworld's spawn
	 * ({@code method_43126}), at height {@code y}. The spawn's chunks are generated before that tick; a chunk elsewhere is
	 * generated in it, which on a client whose spawn lay far from the origin held the integrated server up for seconds,
	 * past the end of the run.
	 */
	static class_2338 testSite(class_3218 level, int dx, int y, int dz) {
		class_2338 spawn = level.method_43126();
		return new class_2338(spawn.method_10263() + dx, y, spawn.method_10260() + dz);
	}

	/**
	 * Whether Fabric's colour provider registry answers from the game's block colours ({@link ForgeColorTest}), on a client
	 * with Fabric API's rendering module.
	 */
	public static void checkColors() {
		if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT || !FabricLoader.getInstance().isModLoaded("fabric-rendering-v1")) return;
		try {
			ForgeColorTest.check();
		} catch (RuntimeException | LinkageError e) {
			LOGGER.warn("FABRICATED-FORGE-TEST colour provider check failed", e);
		}
	}

	/**
	 * Whether the particle engine finds the test mod's particle factory and loads its sprites
	 * ({@link ForgeParticleClient}), on a client.
	 */
	public static void checkParticles() {
		if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT || ForgeParticleTest.sparkle == null) return;
		try {
			ForgeParticleClient.check();
		} catch (RuntimeException | LinkageError e) {
			LOGGER.warn("FABRICATED-FORGE-TEST particle check failed", e);
		}
	}

	/** Reserved for the client: Fabric's environment stripping takes it out of the class on a dedicated server. */
	@Environment(EnvType.CLIENT)
	static void clientOnly() {
	}
}
