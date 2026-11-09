package io.github.cmdralph.fabricated.forgetest;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;

import io.github.cmdralph.fabricated.api.FabricApiModule;
import io.github.cmdralph.fabricated.api.Fabricated;
import io.github.cmdralph.fabricated.testmod.TestMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;

/**
 * Forge 47's half of the CI test mod: the checks of Forge's own hooks and of the Fabric API modules Fabricated ships for
 * Forge 47, beside the shared ones (mc/shared), whose entrypoints mc/platform-forge-legacy.gradle adds to the test mod's
 * descriptor. Its main entrypoint registers what they need; the first server tick runs the checks of the server
 * ({@code mixin/MinecraftServerMixin}), and on a client those of Forge's client hooks ({@link #checkClient}).
 */
public final class ForgeTestMod implements ModInitializer {
	/** The namespace of the content and data of Forge 47's half. */
	public static final String NAMESPACE = "fabricated-forge-testmod";
	public static final Logger LOGGER = TestMod.LOGGER;
	public static final String ITEM_PATH = "test_item";
	/** The item {@link #onInitialize} registers, as Fabric mods register theirs: straight into the registry. */
	public static Item testItem;

	@Override
	public void onInitialize() {
		// Vanilla's resources are the minecraft container's too: Fabric API's resource loader reads vanilla's en_us.json
		// there as the game boots.
		if (FabricLoader.getInstance().getModContainer("minecraft").flatMap(c -> c.findPath("/assets/minecraft/lang/en_us.json")).isPresent()) {
			LOGGER.info("FABRICATED-TEST game resources OK");
		}
		ForgeRecordedFramesTest.check();
		testItem = Registry.register(BuiltInRegistries.ITEM, new ResourceLocation(NAMESPACE, ITEM_PATH), new Item(new Item.Properties()));
		run("recipe condition registration", ForgeRecipeConditionTest::register);
		run("fluid registration", ForgeFluidTest::register);
		ForgeRegistrationOrderTest.register();
		ForgeRegistryAliasTest.register();
		ForgeRegistryEntryAddedTest.register();
		ForgeConfigPortTest.register();
		run("command registration", ForgeCommandTest::register);
		run("block break listeners", ForgeBlockBreakTest::register);
		run("sleep listeners", ForgeSleepTest::register);
		run("storage blocks", ForgeTransferTest::register);
		run("interaction listeners", ForgeInteractionTest::register);
		run("fuel registration", ForgeFuelTest::register);
		run("brewing recipe registration", ForgeBrewingTest::register);
		run("oxidizable blocks", ForgeOxidizableTest::check);
	}

	/** Runs a part, logging what it throws as its failure. */
	public static void run(String what, Runnable part) {
		try {
			part.run();
		} catch (RuntimeException | LinkageError e) {
			LOGGER.error("FABRICATED-TEST {} FAILED", what, e);
		}
	}

	/** On a client, the checks of Forge 47's client hooks, once the integrated server has ticked. */
	public static void checkClient() {
		if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) return;
		if (FabricLoader.getInstance().isModLoaded("fabric-rendering-v1")) run("colour provider check", ForgeColorTest::check);
		run("particle providers check", ForgeParticleClient::check);
		run("key binding check", ForgeKeyBindingClient::check);
	}

	/**
	 * Where a check at the first server tick builds: {@code dx} and {@code dz} blocks from the overworld's spawn, at height
	 * {@code y}. The spawn's chunks are generated before that tick; a chunk elsewhere is generated in it, which on a client
	 * whose spawn lay far from the origin held the integrated server up for seconds, past the end of the run.
	 */
	static BlockPos testSite(ServerLevel level, int dx, int y, int dz) {
		BlockPos spawn = level.getSharedSpawnPos();
		return new BlockPos(spawn.getX() + dx, y, spawn.getZ() + dz);
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
}
