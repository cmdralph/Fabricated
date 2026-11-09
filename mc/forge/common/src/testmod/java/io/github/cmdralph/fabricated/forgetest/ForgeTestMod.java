package io.github.cmdralph.fabricated.forgetest;

import org.slf4j.Logger;

import io.github.cmdralph.fabricated.testmod.TestMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Forge's half of the CI test mod: the checks of Forge's own hooks, beside the shared ones (mc/shared), whose
 * entrypoints mc/platform-forge.gradle adds to the test mod's descriptor. Its main entrypoint registers what they need;
 * the first server tick runs the checks of the server: the interface the test mod's class tweaker injects, Fabricated's
 * loot hooks standing down for another mod's loot module ({@link ForgeLootProviderTest}), the object builder module's default attribute event
 * ({@link ForgeAttributeTest}), the command module's registration event ({@link ForgeCommandTest}), the interaction
 * module's block break events
 * ({@link ForgeBlockBreakTest}), the entity events module's sleep ({@link ForgeSleepTest}) and damage
 * ({@link ForgeDamageTest}) events, the resource conditions module's load conditions in data files and registry entries
 * ({@link ForgeConditionsTest}), a Fabric mod's stable registry entries ({@link ForgePackTest}), ingredients on the
 * network ({@link ForgeIngredientCodecTest}), the registry sync module's aliases ({@link ForgeRegistryAliasTest}) and
 * entry added events ({@link ForgeRegistryEntryAddedTest}), and on a client the colour providers
 * ({@link ForgeColorTest}) and the key mapping ({@link ForgeKeyMappingTest}).
 */
public final class ForgeTestMod implements ModInitializer {
	/** The namespace of the content and data of Forge's half. */
	static final String NAMESPACE = "fabricated-forge-testmod";
	public static final Logger LOGGER = TestMod.LOGGER;

	@Override
	public void onInitialize() {
		ForgeLootProviderTest.register();
		run("default attribute listener", "fabric-object-builder-api-v1", ForgeAttributeTest::register);
		run("command listener", "fabric-command-api-v2", ForgeCommandTest::register);
		run("registry aliases", "fabric-registry-sync-v0", ForgeRegistryAliasTest::register);
		run("registry entry added events", "fabric-registry-sync-v0", ForgeRegistryEntryAddedTest::register);
		run("block break listeners", "fabric-events-interaction-v0", ForgeBlockBreakTest::register);
		run("sleep listeners", "fabric-entity-events-v1", ForgeSleepTest::register);
		run("damage listeners", "fabric-entity-events-v1", ForgeDamageTest::register);
		ServerTickEvents.END_SERVER_TICK.register(new ServerTickEvents.EndTick() {
			private boolean checked;

			@Override
			public void onEndTick(MinecraftServer server) {
				if (checked) return;
				checked = true;
				checkFirstTick(server);
			}
		});
	}

	/** The checks of the first server tick, each logging its own result. */
	private static void checkFirstTick(MinecraftServer server) {
		if ((Object) server instanceof ForgeTestMarker) LOGGER.info("FABRICATED-TEST injected interface OK");
		else LOGGER.error("FABRICATED-TEST injected interface FAILED: the server has no ForgeTestMarker");
		ServerLevel level = server.overworld();
		ForgeLootProviderTest.check();
		run("default attribute check", "fabric-object-builder-api-v1", ForgeAttributeTest::check);
		run("load conditions check", "fabric-resource-conditions-api-v1", () -> ForgeConditionsTest.check(server));
		run("mod pack data check", null, () -> ForgePackTest.check(server));
		run("ingredient check", null, () -> ForgeIngredientCodecTest.check(server));
		run("command check", "fabric-command-api-v2", () -> ForgeCommandTest.check(server));
		run("registry alias check", "fabric-registry-sync-v0", ForgeRegistryAliasTest::check);
		run("block break check", "fabric-events-interaction-v0", () -> ForgeBlockBreakTest.check(level));
		run("sleep check", "fabric-entity-events-v1", () -> ForgeSleepTest.check(level));
		run("damage check", "fabric-entity-events-v1", () -> ForgeDamageTest.check(level));
		if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
			run("colour provider check", "fabric-rendering-v1", ForgeColorTest::check);
			run("key mapping check", null, ForgeKeyMappingTest::check);
		}
	}

	/** Runs a part where its Fabric API module is loaded (any for null), logging what it throws as its failure. */
	private static void run(String what, String module, Runnable part) {
		if (module != null && !FabricLoader.getInstance().isModLoaded(module)) return;
		try {
			part.run();
		} catch (RuntimeException | LinkageError e) {
			LOGGER.error("FABRICATED-TEST {} FAILED", what, e);
		}
	}

	/**
	 * Where a check at the first server tick builds: {@code dx} and {@code dz} blocks from the overworld's spawn, 30 below
	 * the top of the world. The spawn's chunks are generated before that tick; a chunk elsewhere is generated in it, which
	 * on a client whose spawn lay far from the origin held the integrated server up for seconds, past the end of the run.
	 */
	public static BlockPos testSite(ServerLevel level, int dx, int dz) {
		BlockPos spawn = level.getRespawnData().pos();
		return new BlockPos(spawn.getX() + dx, level.getMaxY() - 30, spawn.getZ() + dz);
	}
}
