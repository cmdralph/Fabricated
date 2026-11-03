package io.github.cmdralph.fabricated.forgetest;

import net.minecraft.core.HolderSet;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * A Fabric mod's own ingredient, as one written for vanilla is: a subclass of {@code Ingredient} without Forge's
 * {@code serializer()}, matching stone (CORPUS-85, {@link ForgeIngredientCodecTest}). Fabric API's custom ingredients
 * are made the same way.
 */
final class ForgeTestIngredient extends Ingredient {
	ForgeTestIngredient() {
		super(HolderSet.direct(Items.STONE.builtInRegistryHolder()));
	}
}
