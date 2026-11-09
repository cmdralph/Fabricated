package io.github.cmdralph.fabricated.forgetest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import io.github.cmdralph.fabricated.api.FabricApiModule;
import io.github.cmdralph.fabricated.api.FabricMod;
import io.github.cmdralph.fabricated.api.Fabricated;
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
import net.minecraft.class_3442;
import net.minecraft.class_7923;
import net.minecraft.server.MinecraftServer;

/**
 * The forge-legacy branch's Fabric test mod (docs/PORTING.md §7, L3): its entrypoints and its mixin each log a line the
 * server smoke looks for (ci/smoke-server.sh). It uses Fabric Loader's API, and of Fabric API only the
 * modules Fabricated provides itself (fabric-biome-api-v1, fabric-dimensions-v1, fabric-loot-api-v2,
 * fabric-loot-tables-v1 and fabric-particles-v1), as the smoke runs without Fabric API's jar too. It registers fluids
 * as Fabric mods do, and names the game's classes as a Fabric mod for 1.20.1 does, in Intermediary's names (the
 * stand-ins in src/testmodstubs), which Fabricated remaps to Forge 47's. With Fabric API's jar it also tries modules that
 * jar provides: the command, networking, block break ({@link ForgeBlockBreakTest}), use and attack
 * ({@link ForgeInteractionTest}) and sleep ({@link ForgeSleepTest}) events, and storages reaching Forge's capabilities
 * ({@link ForgeTransferTest}).
 */
public final class ForgeTestMod implements ModInitializer, DedicatedServerModInitializer {
	public static final String MOD_ID = "fabricated-forge-testmod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	/** The test mod that fails on purpose, which only the server smoke installs (mc/shared/src/testmodbroken). */
	private static final String THROWS = "fabricated-testmod-throws";
	public static final String ITEM_PATH = "test_item";
	/** The item {@link #onInitialize} registers, as Fabric mods register theirs: straight into the registry. */
	public static class_1792 testItem;

	@Override
	public void onInitialize() {
		String version = FabricLoader.getInstance().getModContainer(MOD_ID).map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("none");
		LOGGER.info("FABRICATED-TEST main entrypoint OK (version {}, Fabric Loader sees {} mods)", version, FabricLoader.getInstance().getAllMods().size());
		// Fabricated's API on Forge: what waits for the Fabric initializers runs once they have, the mod that fails on
		// purpose (in the server smoke) known as failed.
		Fabricated.get().ifPresent(api -> api.afterEntrypoints(() -> {
			boolean ok = api.entrypointsComplete() && api.isFabricModLoaded(MOD_ID)
					&& api.fabricMod(THROWS).map(m -> m.status() == FabricMod.Status.FAILED).orElse(true);
			if (ok) LOGGER.info("FABRICATED-TEST api OK");
			else LOGGER.error("FABRICATED-TEST api FAILED: complete {}, {}, {}", api.entrypointsComplete(), api.fabricMod(MOD_ID),
					api.fabricMod(THROWS));
		}));
		// Vanilla's resources are the minecraft container's too: Fabric API's resource loader reads vanilla's en_us.json
		// there as the game boots.
		if (FabricLoader.getInstance().getModContainer("minecraft").flatMap(c -> c.findPath("/assets/minecraft/lang/en_us.json")).isPresent()) {
			LOGGER.info("FABRICATED-TEST game resources OK");
		}
		ForgeRecordedFramesTest.check();
		try {
			class_2960 id = new class_2960(MOD_ID, ITEM_PATH);
			class_2378<class_1792> items = class_7923.field_41178;
			testItem = class_2378.method_10230(items, id, new class_1792(new class_1792.class_1793()));
			if (id.equals(items.method_10221(testItem))) {
				LOGGER.info("FABRICATED-TEST registry OK");
				// Item.maxStackSize is private: reading it fails to link unless the access widener reached Forge.
				if (testItem.field_8013 == 64) LOGGER.info("FABRICATED-TEST access widener OK");
			} else {
				LOGGER.error("FABRICATED-TEST registry gave the item another key: {}", items.method_10221(testItem));
			}
		} catch (RuntimeException | LinkageError e) {
			LOGGER.error("FABRICATED-TEST registry failed", e);
		}
		try {
			ForgeBootstrapContent.register();
		} catch (RuntimeException | LinkageError e) {
			LOGGER.error("FABRICATED-TEST content built at bootstrap failed", e);
		}
		try {
			ForgeBiomeTest.register();
		} catch (RuntimeException | LinkageError e) {
			LOGGER.error("FABRICATED-TEST biome modifications failed", e);
		}
		try {
			ForgeLootTest.register();
		} catch (RuntimeException | LinkageError e) {
			LOGGER.error("FABRICATED-TEST loot table events failed", e);
		}
		try {
			ForgeRecipeConditionTest.register();
		} catch (RuntimeException | LinkageError e) {
			LOGGER.error("FABRICATED-TEST recipe condition registration failed", e);
		}
		try {
			ForgeFluidTest.register();
		} catch (RuntimeException | LinkageError e) {
			LOGGER.error("FABRICATED-TEST fluid registration failed", e);
		}
		ForgeRegistrationOrderTest.register();
		ForgeRegistryAliasTest.register();
		ForgeRegistryEntryAddedTest.register();
		ForgeConfigPortTest.register();
		try {
			ForgeCommandTest.register();
		} catch (RuntimeException | LinkageError e) {
			LOGGER.error("FABRICATED-TEST command registration failed", e);
		}
		try {
			ForgeParticleTest.register();
		} catch (RuntimeException | LinkageError e) {
			LOGGER.error("FABRICATED-TEST particle type registration failed", e);
		}
		try {
			ForgeBlockBreakTest.register();
		} catch (RuntimeException | LinkageError e) {
			LOGGER.error("FABRICATED-TEST block break listeners not registered", e);
		}
		try {
			ForgeSleepTest.register();
		} catch (RuntimeException | LinkageError e) {
			LOGGER.error("FABRICATED-TEST sleep listeners not registered", e);
		}
		try {
			ForgeTransferTest.register();
		} catch (RuntimeException | LinkageError e) {
			LOGGER.error("FABRICATED-TEST storage blocks not registered", e);
		}
		if (FabricLoader.getInstance().isModLoaded("fabric-networking-api-v1")) {
			try {
				ForgeNetworkingTest.register();
			} catch (RuntimeException | LinkageError e) {
				LOGGER.error("FABRICATED-TEST networking receiver not registered", e);
			}
		}
		if (FabricLoader.getInstance().isModLoaded("fabric-events-interaction-v0")) {
			try {
				ForgeInteractionTest.register();
			} catch (RuntimeException | LinkageError e) {
				LOGGER.error("FABRICATED-TEST interaction listeners not registered", e);
			}
		}
		if (testItem != null && FabricLoader.getInstance().isModLoaded("fabric-content-registries-v0")) {
			try {
				ForgeFuelTest.register();
			} catch (RuntimeException | LinkageError e) {
				LOGGER.error("FABRICATED-TEST fuel not registered", e);
			}
			try {
				ForgeBrewingTest.register();
			} catch (RuntimeException | LinkageError e) {
				LOGGER.error("FABRICATED-TEST brewing recipe not registered", e);
			}
		}
		if (FabricLoader.getInstance().isModLoaded("fabric-content-registries-v0")) {
			try {
				ForgeOxidizableTest.check();
			} catch (RuntimeException | LinkageError e) {
				LOGGER.error("FABRICATED-TEST oxidizable blocks not checked", e);
			}
		}
	}

	@Override
	public void onInitializeServer() {
		LOGGER.info("FABRICATED-TEST server entrypoint OK");
		if (Arrays.stream(ForgeTestMod.class.getDeclaredMethods()).noneMatch(m -> m.getName().equals("clientOnly"))) {
			LOGGER.info("FABRICATED-TEST environment stripping OK");
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
	 * Whether another mod provides the Fabric API module (Forgified Fabric API), in place of Fabric API's jar or Fabricated:
	 * the module's answers are then that mod's. A module of Fabricated's plan says so in Fabricated's API; one Fabricated
	 * passes through from Fabric API's jar outside the plan is another mod's where it is loaded and isn't a Fabric mod
	 * Fabricated runs.
	 */
	static boolean providedByAnotherMod(String module) {
		Optional<FabricApiModule> planned = Fabricated.get().flatMap(api -> api.fabricApiModule(module));
		if (planned.isPresent()) return planned.get().status() == FabricApiModule.Status.PROVIDED_BY_MOD;
		return FabricLoader.getInstance().isModLoaded(module) && Fabricated.get().flatMap(api -> api.fabricMod(module)).isEmpty();
	}

	/**
	 * Logs a check's result: OK when nothing failed, FAILED otherwise, except where {@code module} is another mod's and
	 * every item that failed is one {@code providersAnswers} names: that mod answers it differently, as its source reads,
	 * and the check logs SKIPPED with the items and why, as a check of that mod's module (PROVIDER-15, as PROVIDER-8 on
	 * NeoForge).
	 */
	static void report(String check, List<String> failed, String module, Map<String, String> providersAnswers) {
		if (failed.isEmpty()) {
			LOGGER.info("FABRICATED-TEST {} OK", check);
		} else if (providedByAnotherMod(module) && providersAnswers.keySet().containsAll(failed)) {
			LOGGER.info("FABRICATED-TEST {} SKIPPED: {} is another mod's, which answers {} as its source reads: {} (PROVIDER-15)", check, module, failed,
					failed.stream().map(providersAnswers::get).distinct().collect(Collectors.joining("; ")));
		} else {
			LOGGER.error("FABRICATED-TEST {} FAILED: {}", check, String.join("; ", failed));
		}
	}

	/**
	 * Whether Fabric's use and attack callbacks are asked, and their answers honoured, as a player uses and attacks blocks,
	 * items and entities ({@link ForgeInteractionTest}; with Fabric API only), at the first server tick.
	 */
	public static void checkInteractions(class_3218 level) {
		if (!FabricLoader.getInstance().isModLoaded("fabric-events-interaction-v0")) return;
		try {
			ForgeInteractionTest.check(level);
		} catch (RuntimeException | LinkageError e) {
			LOGGER.warn("FABRICATED-TEST interaction check failed", e);
		}
	}

	/** The rest of {@link #checkInteractions}, at every later server tick until it has run. */
	public static void tickInteractions(class_3218 level) {
		if (!FabricLoader.getInstance().isModLoaded("fabric-events-interaction-v0")) return;
		try {
			ForgeInteractionTest.tick(level);
		} catch (RuntimeException | LinkageError e) {
			LOGGER.warn("FABRICATED-TEST interaction check failed", e);
		}
	}

	/**
	 * Whether vanilla reads a player's stats file ({@code class_3442}, {@code ServerStatsCounter}), as it does when a player
	 * joins: its parser is in Gson's {@code com.google.gson.internal}, a package Gson's module doesn't export, which the
	 * game reaches through the loader Fabricated's class path loader goes in front of (GSON-1, GSON-2).
	 */
	public static void checkVanillaStats(MinecraftServer server) {
		try {
			Path file = Files.createTempFile("fabricated-stats", ".json");
			Files.writeString(file, "{\"stats\":{}}");
			new class_3442(server, file.toFile());
			LOGGER.info("FABRICATED-TEST vanilla stats read OK");
		} catch (IOException | RuntimeException | LinkageError e) {
			LOGGER.error("FABRICATED-TEST vanilla stats read FAILED: {}", e.toString());
		}
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
			LOGGER.warn("FABRICATED-TEST colour provider check failed", e);
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
			LOGGER.warn("FABRICATED-TEST particle check failed", e);
		}
	}

	/** On a client, whether the test mod's key binding works as on Fabric ({@link ForgeKeyBindingClient}, KEYMAP-3). */
	public static void checkKeyBinding() {
		if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) return;
		try {
			ForgeKeyBindingClient.check();
		} catch (RuntimeException | LinkageError e) {
			LOGGER.warn("FABRICATED-TEST key binding check failed", e);
		}
	}

	/** On a client, whether the test mod's built-in pack enabled by default was switched on ({@link ForgeDefaultPackTest}, CORPUS-104). */
	public static void checkDefaultPack() {
		if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) return;
		try {
			ForgeDefaultPackTest.check();
		} catch (RuntimeException | LinkageError e) {
			LOGGER.warn("FABRICATED-TEST builtin resource pack check failed", e);
		}
	}

	/** Reserved for the client: Fabric's environment stripping takes it out of the class on a dedicated server. */
	@Environment(EnvType.CLIENT)
	static void clientOnly() {
	}
}
