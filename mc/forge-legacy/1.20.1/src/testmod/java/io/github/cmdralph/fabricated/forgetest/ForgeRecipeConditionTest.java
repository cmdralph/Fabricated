package io.github.cmdralph.fabricated.forgetest;

import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_1863;
import net.minecraft.class_2960;

/**
 * The forge-legacy test mod's part for Fabric API's resource conditions in Forge 47's own recipe {@code conditions}
 * (CORPUS-97, with Fabric API). Supplementaries names its own condition there and registers it with Fabric API, which
 * Forge 47 didn't ask: it refused the type and the recipe. The test mod registers {@code fabricated-forge-testmod:enabled}
 * (its {@code value}) as a Fabric resource condition, and ships two recipes whose {@code conditions} name it after
 * {@code forge:mod_loaded} of the module (so that without Fabric API Forge reads no further): {@code condition_kept},
 * true, must load, and {@code condition_dropped}, false, must not, with no parsing error. The condition logs each answer
 * it gives, and the first server tick looks both recipes up in the recipe manager ({@link #check}).
 */
public final class ForgeRecipeConditionTest {
	private ForgeRecipeConditionTest() {
	}

	/** From the main entrypoint, as a Fabric mod registers its resource conditions. */
	public static void register() {
		if (FabricLoader.getInstance().isModLoaded("fabric-resource-conditions-api-v1")) Upstream.register();
	}

	/**
	 * At the first server tick, with Fabric API: the true condition's recipe is in the recipe manager, the false one's
	 * isn't ({@code method_8130} is {@code byKey}).
	 */
	public static void check(class_1863 recipes) {
		if (!FabricLoader.getInstance().isModLoaded("fabric-resource-conditions-api-v1")) return;
		boolean kept = recipes.method_8130(new class_2960(ForgeTestMod.MOD_ID, "condition_kept")).isPresent();
		boolean dropped = recipes.method_8130(new class_2960(ForgeTestMod.MOD_ID, "condition_dropped")).isPresent();
		if (kept && !dropped) {
			ForgeTestMod.LOGGER.info("FABRICATED-TEST recipe conditions OK");
		} else {
			ForgeTestMod.LOGGER.warn("FABRICATED-TEST recipe conditions: the true condition's recipe loaded {}, the false one's {}", kept, dropped);
		}
	}

	/** Everything that names Fabric's resource conditions module: loaded only where it is installed. */
	private static final class Upstream {
		private Upstream() {
		}

		static void register() {
			ResourceConditions.register(new class_2960(ForgeTestMod.MOD_ID, "enabled"), json -> {
				boolean value = json.has("value") && json.get("value").getAsBoolean();
				ForgeTestMod.LOGGER.info("FABRICATED-TEST recipe condition asked: {}", value);
				return value;
			});
		}
	}
}
