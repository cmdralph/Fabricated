package io.github.cmdralph.fabricated.forgetest;

import java.util.stream.Stream;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * A Fabric mod's own ingredient, as one written for vanilla is: a subclass of {@code Ingredient} without Forge's
 * {@code serializer()}, matching stone (CORPUS-85, {@link ForgeIngredientCodecTest}), made from 1.21.1's values. Fabric
 * API's custom ingredients are made the same way.
 */
final class ForgeTestIngredient extends Ingredient {
	ForgeTestIngredient() {
		super(Stream.of(new Ingredient.ItemValue(new ItemStack(Items.STONE))));
	}
}
