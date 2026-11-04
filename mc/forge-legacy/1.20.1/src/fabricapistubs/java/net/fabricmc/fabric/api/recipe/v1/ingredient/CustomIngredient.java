package net.fabricmc.fabric.api.recipe.v1.ingredient;

import net.minecraft.world.item.crafting.Ingredient;

/**
 * Compile-time stand-in: the members of Fabric API's 1.20.1 interface that Fabricated's in-game mod calls, in Mojang's
 * names.
 */
public interface CustomIngredient {
	/** The ingredient the game uses for this custom ingredient. */
	Ingredient toVanilla();
}
