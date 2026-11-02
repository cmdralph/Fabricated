package io.github.cmdralph.fabricated.forgetest;

import net.fabricmc.fabric.api.registry.FabricBrewingRecipeRegistry;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1842;
import net.minecraft.class_1844;
import net.minecraft.class_1845;
import net.minecraft.class_1856;
import net.minecraft.class_2960;
import net.minecraft.class_7923;

/**
 * The forge-legacy test mod's part for fabric-content-registries-v0's brewing recipes (BREW-1; with Fabric API's jar
 * only): an awkward potion brewed with the test item, through a recipe {@code FabricBrewingRecipeRegistry} registered,
 * must give a potion of luck. Upstream builds the recipe with vanilla's constructor of a brewing mix, which Forge 47
 * replaced, so registering it threw; Fabricated's in-game mod builds it with Forge's, and the brewing stand's result
 * ({@code mix}) is asked as Forge's own vanilla brewing recipe asks it. The potions and the potion item are looked up by
 * id, as their fields have no names here.
 */
public final class ForgeBrewingTest {
	private static boolean registered;

	private ForgeBrewingTest() {
	}

	/** From the main entrypoint, once the test item is registered; only with Fabric API's content registries module. */
	static void register() {
		FabricBrewingRecipeRegistry.registerPotionRecipe(potion("awkward"), class_1856.method_8091(ForgeTestMod.testItem), potion("luck"));
		registered = true;
	}

	/** At the first server tick. */
	public static void check() {
		if (!registered) {
			ForgeTestMod.LOGGER.error("FABRICATED-FORGE-TEST brewing recipe FAILED: FabricBrewingRecipeRegistry didn't take the recipe (see the error above)");
			return;
		}
		class_1792 potionItem = class_7923.field_41178.method_10223(new class_2960("minecraft", "potion"));
		class_1799 awkward = class_1844.method_8061(new class_1799(potionItem), potion("awkward"));
		class_1842 brewed = class_1844.method_8063(class_1845.method_8078(new class_1799(ForgeTestMod.testItem), awkward));
		if (brewed == potion("luck")) {
			ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST brewing recipe OK");
		} else {
			ForgeTestMod.LOGGER.error("FABRICATED-FORGE-TEST brewing recipe FAILED: an awkward potion brewed with the test item gave {}",
					class_7923.field_41179.method_10221(brewed));
		}
	}

	private static class_1842 potion(String path) {
		return class_7923.field_41179.method_10223(new class_2960("minecraft", path));
	}
}
