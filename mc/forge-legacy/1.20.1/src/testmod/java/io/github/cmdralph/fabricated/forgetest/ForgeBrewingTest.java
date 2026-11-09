package io.github.cmdralph.fabricated.forgetest;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.crafting.Ingredient;
import net.fabricmc.fabric.api.registry.FabricBrewingRecipeRegistry;

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
		FabricBrewingRecipeRegistry.registerPotionRecipe(potion("awkward"), Ingredient.of(ForgeTestMod.testItem), potion("luck"));
		registered = true;
	}

	/** At the first server tick. */
	public static void check() {
		if (!registered) {
			ForgeTestMod.LOGGER.error("FABRICATED-TEST brewing recipe FAILED: FabricBrewingRecipeRegistry didn't take the recipe (see the error above)");
			return;
		}
		Item potionItem = BuiltInRegistries.ITEM.get(new ResourceLocation("minecraft", "potion"));
		ItemStack awkward = PotionUtils.setPotion(new ItemStack(potionItem), potion("awkward"));
		Potion brewed = PotionUtils.getPotion(PotionBrewing.mix(new ItemStack(ForgeTestMod.testItem), awkward));
		if (brewed == potion("luck")) {
			ForgeTestMod.LOGGER.info("FABRICATED-TEST brewing recipe OK");
		} else {
			ForgeTestMod.LOGGER.error("FABRICATED-TEST brewing recipe FAILED: an awkward potion brewed with the test item gave {}",
					BuiltInRegistries.POTION.getKey(brewed));
		}
	}

	private static Potion potion(String path) {
		return BuiltInRegistries.POTION.get(new ResourceLocation("minecraft", path));
	}
}
