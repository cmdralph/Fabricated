package io.github.cmdralph.fabricated.forge.recipe;

import java.util.Optional;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Vanilla's network form of an empty optional ingredient, which Forge 61 to 66's {@code Ingredient} patch can neither
 * write nor read (CORPUS-83). Vanilla writes an empty one, a shaped recipe's empty slot, as an empty item list: the
 * holder set codec's {@code VarInt} 1 (0 is a tag, {@code n + 1} a list of {@code n} items, and Forge's custom
 * ingredients start with -1), and reads that back as empty. Forge's patch writes it as {@code Ingredient.of()}, which
 * throws "Ingredients can't be empty", and reads the empty list through the constructor that throws the same. Fabric
 * API's recipe sync writes every recipe's slots with this codec, so on Forge a Fabric mod's shaped recipe with an empty
 * slot dropped the player's connection to the world they joined. Every other ingredient is left to Forge's codec.
 */
public final class OptionalIngredientCodec {
	/** The holder set codec's length prefix of an empty item list. */
	private static final int EMPTY_LIST = 1;

	private OptionalIngredientCodec() {
	}

	/** Forge's codec, writing and reading an empty optional ingredient as vanilla does. */
	public static StreamCodec<RegistryFriendlyByteBuf, Optional<Ingredient>> codec(StreamCodec<RegistryFriendlyByteBuf, Optional<Ingredient>> forge) {
		return StreamCodec.of((buf, ingredient) -> {
			if (ingredient.isEmpty()) {
				buf.writeVarInt(EMPTY_LIST);
			} else {
				forge.encode(buf, ingredient);
			}
		}, buf -> {
			int start = buf.readerIndex();
			if (buf.readVarInt() == EMPTY_LIST) return Optional.empty();
			buf.readerIndex(start);
			return forge.decode(buf);
		});
	}
}
