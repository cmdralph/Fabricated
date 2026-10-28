package io.github.cmdralph.fabricated.forgetest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The forge branch's Fabric test mod (docs/PORTING.md §4.1, F3 step 3): its entrypoints and its mixin each log a line
 * the server smoke looks for (ci/forge/smoke-server.sh). It uses Fabric Loader's API and, of Fabric API, only the loot
 * module Fabricated ships itself ({@link ForgeLootTest}), so it runs without Fabric API's jar; with it, it also tries the
 * object builder module's default attribute event ({@link ForgeAttributeTest}), the advancement module's modify event
 * ({@link ForgeAdvancementTest}), the command module's registration event ({@link ForgeCommandTest}), a networking
 * round trip ({@link ForgeNetworkingTest}), the interaction module's block break events ({@link ForgeBlockBreakTest})
 * and use and attack callbacks ({@link ForgeInteractionTest}), the entity events module's sleep ({@link ForgeSleepTest})
 * and damage ({@link ForgeDamageTest}) events, and storages reaching Forge's capabilities ({@link ForgeTransferTest}).
 * Its fluids are written as Fabric mods write theirs ({@link ForgeFluidTest}).
 */
public final class ForgeTestMod implements ModInitializer, DedicatedServerModInitializer {
	public static final String MOD_ID = "fabricated-forge-testmod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final Identifier ITEM_ID = Identifier.fromNamespaceAndPath(MOD_ID, "test_item");
	/** The item {@link #onInitialize} registers, as Fabric mods register theirs: straight into the registry. */
	public static Item testItem;
	/** Lists stone, dirt and gravel, and removes dirt in Fabric's {@code fabric:remove} and gravel in Forge's {@code remove}. */
	private static final TagKey<Item> REMOVAL = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MOD_ID, "removal"));

	@Override
	public void onInitialize() {
		String version = FabricLoader.getInstance().getModContainer(MOD_ID).map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("none");
		LOGGER.info("FABRICATED-FORGE-TEST main entrypoint OK (version {}, Fabric Loader sees {} mods)", version, FabricLoader.getInstance().getAllMods().size());
		try {
			testItem = Registry.register(BuiltInRegistries.ITEM, ITEM_ID, new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, ITEM_ID))));
			if (ITEM_ID.equals(BuiltInRegistries.ITEM.getKey(testItem))) {
				LOGGER.info("FABRICATED-FORGE-TEST registry OK");
			} else {
				LOGGER.error("FABRICATED-FORGE-TEST registry gave the item another key: {}", BuiltInRegistries.ITEM.getKey(testItem));
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
			ForgeLootTest.register();
		} catch (RuntimeException | LinkageError e) {
			LOGGER.warn("FABRICATED-FORGE-TEST loot listeners not registered", e);
		}
		try {
			ForgeFluidTest.register();
		} catch (RuntimeException | LinkageError e) {
			LOGGER.warn("FABRICATED-FORGE-TEST fluids not registered", e);
		}
		if (FabricLoader.getInstance().isModLoaded("fabric-object-builder-api-v1")) {
			try {
				ForgeAttributeTest.register();
			} catch (RuntimeException | LinkageError e) {
				LOGGER.warn("FABRICATED-FORGE-TEST default attribute listener not registered", e);
			}
		}
		if (FabricLoader.getInstance().isModLoaded("fabric-advancement-api-v1")) {
			try {
				ForgeAdvancementTest.register();
			} catch (RuntimeException | LinkageError e) {
				LOGGER.warn("FABRICATED-FORGE-TEST advancement listener not registered", e);
			}
		}
		if (FabricLoader.getInstance().isModLoaded("fabric-command-api-v2")) {
			try {
				ForgeCommandTest.register();
			} catch (RuntimeException | LinkageError e) {
				LOGGER.warn("FABRICATED-FORGE-TEST command listener not registered", e);
			}
		}
		if (FabricLoader.getInstance().isModLoaded("fabric-networking-api-v1")) {
			try {
				ForgeNetworkingTest.register();
			} catch (RuntimeException | LinkageError e) {
				LOGGER.warn("FABRICATED-FORGE-TEST networking receiver not registered", e);
			}
		}
		if (FabricLoader.getInstance().isModLoaded("fabric-events-interaction-v0")) {
			try {
				ForgeBlockBreakTest.register();
				ForgeInteractionTest.register();
			} catch (RuntimeException | LinkageError e) {
				LOGGER.warn("FABRICATED-FORGE-TEST interaction listeners not registered", e);
			}
		}
		if (FabricLoader.getInstance().isModLoaded("fabric-entity-events-v1")) {
			try {
				ForgeSleepTest.register();
				ForgeDamageTest.register();
			} catch (RuntimeException | LinkageError e) {
				LOGGER.warn("FABRICATED-FORGE-TEST entity event listeners not registered", e);
			}
		}
		try {
			ForgeTransferTest.register();
		} catch (RuntimeException | LinkageError e) {
			LOGGER.warn("FABRICATED-FORGE-TEST storage blocks not registered", e);
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
	 * Whether both removals in the test mod's item tag took their entry out (once tags are loaded): Fabric's, which the
	 * in-game mod reads into Forge's list, and Forge's own, which Fabric API's tag module, passed through, must leave alone.
	 */
	public static void checkTagRemoval() {
		boolean stone = new ItemStack(Items.STONE).is(REMOVAL);
		boolean dirt = new ItemStack(Items.DIRT).is(REMOVAL);
		boolean gravel = new ItemStack(Items.GRAVEL).is(REMOVAL);
		if (stone && !dirt && !gravel) {
			LOGGER.info("FABRICATED-FORGE-TEST tag removal OK");
		} else {
			LOGGER.warn("FABRICATED-FORGE-TEST tag removal not applied: stone is in the tag {}, dirt (fabric:remove) {}, gravel (remove) {}", stone, dirt, gravel);
		}
	}

	/**
	 * Where a check at the first server tick builds: {@code dx} and {@code dz} blocks from the overworld's spawn, 30 below
	 * the top of the world. The spawn's chunks are generated before that tick; a chunk elsewhere is generated in it, which
	 * on a client whose spawn lay far from the origin held the integrated server up for seconds, past the end of the run.
	 */
	static BlockPos testSite(ServerLevel level, int dx, int dz) {
		BlockPos spawn = level.getRespawnData().pos();
		return new BlockPos(spawn.getX() + dx, level.getMaxY() - 30, spawn.getZ() + dz);
	}

	/** Whether Fabric's loot table events changed dirt's loot table and drops ({@link ForgeLootTest}), once the server ticks. */
	public static void checkLoot(MinecraftServer server) {
		try {
			ForgeLootTest.check(server.overworld());
		} catch (RuntimeException | LinkageError e) {
			LOGGER.warn("FABRICATED-FORGE-TEST loot check failed", e);
		}
	}

	/** Whether the test mod's fluids have Forge fluid types and leave entities alone ({@link ForgeFluidTest}). */
	public static void checkFluids(MinecraftServer server) {
		try {
			ForgeFluidTest.check(server.overworld());
		} catch (RuntimeException | LinkageError e) {
			LOGGER.warn("FABRICATED-FORGE-TEST fluid check failed", e);
		}
	}

	/** Whether Fabric's default attribute event changed a cow's defaults ({@link ForgeAttributeTest}; with Fabric API only). */
	public static void checkDefaultAttributes() {
		try {
			ForgeAttributeTest.check();
		} catch (RuntimeException | LinkageError e) {
			LOGGER.warn("FABRICATED-FORGE-TEST default attribute check failed", e);
		}
	}

	/** Whether Fabric's advancement modify event changed a vanilla advancement ({@link ForgeAdvancementTest}; with Fabric API only). */
	public static void checkAdvancements(MinecraftServer server) {
		try {
			ForgeAdvancementTest.check(server);
		} catch (RuntimeException | LinkageError e) {
			LOGGER.warn("FABRICATED-FORGE-TEST advancement check failed", e);
		}
	}

	/** Whether a command registered through Fabric's command event is in the server's dispatcher ({@link ForgeCommandTest}; with Fabric API only). */
	public static void checkCommands(MinecraftServer server) {
		if (!FabricLoader.getInstance().isModLoaded("fabric-command-api-v2")) return;
		try {
			ForgeCommandTest.check(server);
		} catch (RuntimeException | LinkageError e) {
			LOGGER.warn("FABRICATED-FORGE-TEST command check failed", e);
		}
	}

	/**
	 * Whether Fabric's block break events fire, and a refusal holds, as a player breaks blocks ({@link ForgeBlockBreakTest};
	 * with Fabric API only).
	 */
	public static void checkBlockBreaks(MinecraftServer server) {
		if (!FabricLoader.getInstance().isModLoaded("fabric-events-interaction-v0")) return;
		try {
			ForgeBlockBreakTest.check(server.overworld());
		} catch (RuntimeException | LinkageError e) {
			LOGGER.warn("FABRICATED-FORGE-TEST block break check failed", e);
		}
	}

	/**
	 * Whether Fabric's use and attack callbacks are asked, and their answers honoured, as a player uses and attacks blocks,
	 * items and entities ({@link ForgeInteractionTest}; with Fabric API only).
	 */
	public static void checkInteractions(MinecraftServer server) {
		if (!FabricLoader.getInstance().isModLoaded("fabric-events-interaction-v0")) return;
		try {
			ForgeInteractionTest.check(server.overworld());
		} catch (RuntimeException | LinkageError e) {
			LOGGER.warn("FABRICATED-FORGE-TEST interaction check failed", e);
		}
	}

	/** The rest of {@link #checkInteractions}, at every later server tick until it has run. */
	public static void tickInteractions(MinecraftServer server) {
		if (!FabricLoader.getInstance().isModLoaded("fabric-events-interaction-v0")) return;
		try {
			ForgeInteractionTest.tick(server.overworld());
		} catch (RuntimeException | LinkageError e) {
			LOGGER.warn("FABRICATED-FORGE-TEST interaction check failed", e);
		}
	}

	/**
	 * Whether Fabric's damage events are asked as an entity is hurt, and a refusal holds ({@link ForgeDamageTest}; with
	 * Fabric API only).
	 */
	public static void checkDamage(MinecraftServer server) {
		if (!FabricLoader.getInstance().isModLoaded("fabric-entity-events-v1")) return;
		try {
			ForgeDamageTest.check(server.overworld());
		} catch (RuntimeException | LinkageError e) {
			LOGGER.warn("FABRICATED-FORGE-TEST damage check failed", e);
		}
	}

	/**
	 * Whether a block Fabric's sleep events admit as a bed survives an entity sleeping on it, and the occupation and
	 * orientation events run ({@link ForgeSleepTest}; with Fabric API only).
	 */
	public static void checkSleeping(MinecraftServer server) {
		if (!FabricLoader.getInstance().isModLoaded("fabric-entity-events-v1")) return;
		try {
			ForgeSleepTest.check(server.overworld());
		} catch (RuntimeException | LinkageError e) {
			LOGGER.warn("FABRICATED-FORGE-TEST sleep check failed", e);
		}
	}

	/**
	 * Whether a Fabric mod's storages are Forge's capabilities and a Forge mod's handlers Fabric's storages
	 * ({@link ForgeTransferTest}; with Fabric API only).
	 */
	public static void checkStorages(MinecraftServer server) {
		try {
			ForgeTransferTest.check(server.overworld());
		} catch (RuntimeException | LinkageError e) {
			LOGGER.warn("FABRICATED-FORGE-TEST storage check failed", e);
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
			LOGGER.warn("FABRICATED-FORGE-TEST colour provider check failed", e);
		}
	}

	/** Reserved for the client: Fabric's environment stripping takes it out of the class on a dedicated server. */
	@Environment(EnvType.CLIENT)
	static void clientOnly() {
	}
}
