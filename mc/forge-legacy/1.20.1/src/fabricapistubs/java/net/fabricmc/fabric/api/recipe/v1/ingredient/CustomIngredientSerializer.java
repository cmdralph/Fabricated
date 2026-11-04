package net.fabricmc.fabric.api.recipe.v1.ingredient;

import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;

/**
 * Compile-time stand-in: the members of Fabric API's 1.20.1 interface that Fabricated's in-game mod calls, in Mojang's
 * names.
 */
public interface CustomIngredientSerializer<T extends CustomIngredient> {
	/** The serializer registered under this id, or null. */
	static CustomIngredientSerializer<?> get(ResourceLocation identifier) {
		throw new AssertionError("stand-in");
	}

	/** The custom ingredient a JSON object holding this serializer's {@code fabric:type} describes. */
	T read(JsonObject json);
}
