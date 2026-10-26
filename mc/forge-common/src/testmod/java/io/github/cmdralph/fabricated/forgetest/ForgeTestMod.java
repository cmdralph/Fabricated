package io.github.cmdralph.fabricated.forgetest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The forge branch's Fabric test mod (docs/PORTING.md §4.1, F3 step 3): its entrypoints and its mixin each log a line
 * the server smoke looks for (ci/forge/smoke-server.sh). It uses Fabric Loader's API only, as Fabric API isn't on Forge
 * yet.
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

	/** Reserved for the client: Fabric's environment stripping takes it out of the class on a dedicated server. */
	@Environment(EnvType.CLIENT)
	static void clientOnly() {
	}
}
