package io.github.cmdralph.fabricated.forgetest;

import java.util.function.BiConsumer;

import org.slf4j.Logger;

import io.github.cmdralph.fabricated.testmod.TestMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/**
 * Forge 52's half of the CI test mod: the checks of Forge's own hooks, beside the shared ones (mc/shared), whose
 * entrypoints mc/platform-forge.gradle adds to the test mod's descriptor. Its main entrypoint registers what they need;
 * the first server tick runs the checks of the server: the interface the test mod's class tweaker injects, Fabricated's
 * loot hooks standing down for another mod's loot module ({@link ForgeLootProviderTest}), the command module's
 * registration event ({@link ForgeCommandTest}), the interaction module's block break events ({@link ForgeBlockBreakTest}), the entity
 * events module's sleep events ({@link ForgeSleepTest}), the registry sync module's aliases
 * ({@link ForgeRegistryAliasTest}) and entry added events ({@link ForgeRegistryEntryAddedTest}), and Forge Config API
 * Port's Fabric API ({@link ForgeConfigPortTest}).
 */
public final class ForgeTestMod implements ModInitializer {
	/** The namespace of the content and data of Forge's half. */
	static final String NAMESPACE = "fabricated-forge-testmod";
	public static final Logger LOGGER = TestMod.LOGGER;

	@Override
	public void onInitialize() {
		ForgeLootProviderTest.register();
		run("command listener", "fabric-command-api-v2", ForgeCommandTest::register);
		run("registry aliases", "fabric-registry-sync-v0", ForgeRegistryAliasTest::register);
		run("registry entry added events", "fabric-registry-sync-v0", ForgeRegistryEntryAddedTest::register);
		run("block break listeners", "fabric-events-interaction-v0", ForgeBlockBreakTest::register);
		run("sleep listeners", "fabric-entity-events-v1", ForgeSleepTest::register);
		run("config port", null, ForgeConfigPortTest::register);
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
		run("command check", "fabric-command-api-v2", () -> ForgeCommandTest.check(server));
		run("registry alias check", "fabric-registry-sync-v0", ForgeRegistryAliasTest::check);
		run("block break check", "fabric-events-interaction-v0", () -> ForgeBlockBreakTest.check(level));
		run("sleep check", "fabric-entity-events-v1", () -> ForgeSleepTest.check(level));
		run("config port check", null, ForgeConfigPortTest::check);
		if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) run("colour provider check", "fabric-rendering-v1", ForgeColorTest::check);
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

	/** As a Forge mod gives its items' stacks capabilities, by name: on Forge's attach event (EventBus 6's, generic). */
	public static void attachToStacks(BiConsumer<ItemStack, BiConsumer<ResourceLocation, ICapabilityProvider>> attach) {
		MinecraftForge.EVENT_BUS.addGenericListener(ItemStack.class, EventPriority.NORMAL, false, AttachCapabilitiesEvent.class,
				event -> attach.accept((ItemStack) event.getObject(), event::addCapability));
	}

	/**
	 * Where a check at the first server tick builds: {@code dx} and {@code dz} blocks from the overworld's spawn, 30 below
	 * the top of the world, in the spawn's chunks, which are generated before that tick.
	 */
	public static BlockPos testSite(ServerLevel level, int dx, int dz) {
		BlockPos spawn = level.getSharedSpawnPos();
		return new BlockPos(spawn.getX() + dx, level.getMaxBuildHeight() - 30, spawn.getZ() + dz);
	}
}
