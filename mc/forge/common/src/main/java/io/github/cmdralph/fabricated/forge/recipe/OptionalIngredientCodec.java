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
 * slot dropped the player's connection to the world they joined.
 *
 * <p>A Fabric mod's ingredient in a slot goes in vanilla's form after its marker, as through
 * {@link FabricIngredientCodec} (CORPUS-85). Forge's custom ingredients, which start with -1, are read by Forge's codec
 * directly. With Fabric API installed, its own wrapper of this codec would otherwise read that -1 as the start of its
 * custom form, and fail on Forge's serializer name. It writes that -1 only for its own custom ingredients, which go in
 * vanilla's form here instead. Every other ingredient is written and read by the codec as Forge and Fabric API made
 * it.
 */
public final class OptionalIngredientCodec {
	/** The holder set codec's length prefix of an empty item list. */
	private static final int EMPTY_LIST = 1;
	/** The marker of Forge's custom ingredients ({@code ForgeHooks.ingredientStreamCodec}). */
	private static final int FORGE_CUSTOM = -1;

	private OptionalIngredientCodec() {
	}

	/**
	 * @param forge    the codec as Forge's patch made it, and Fabric API's mixin when it's installed
	 * @param contents Fabricated's {@code CONTENTS_STREAM_CODEC} ({@link FabricIngredientCodec#codec})
	 * @return the codec writing and reading an empty optional ingredient as vanilla does, and a Fabric mod's in
	 *         vanilla's form
	 */
	public static StreamCodec<RegistryFriendlyByteBuf, Optional<Ingredient>> codec(StreamCodec<RegistryFriendlyByteBuf, Optional<Ingredient>> forge,
			StreamCodec<RegistryFriendlyByteBuf, Ingredient> contents) {
		return StreamCodec.of((buf, ingredient) -> {
			if (ingredient.isEmpty()) {
				buf.writeVarInt(EMPTY_LIST);
			} else if (FabricIngredientCodec.sentAsVanilla(ingredient.get())) {
				contents.encode(buf, ingredient.get());
			} else {
				forge.encode(buf, ingredient);
			}
		}, buf -> {
			int start = buf.readerIndex();
			int marker = buf.readVarInt();
			if (marker == EMPTY_LIST) return Optional.empty();
			buf.readerIndex(start);
			if (marker == FabricIngredientCodec.VANILLA_FORM) {
				// Upstream's reading: a custom ingredient is there, and an item list or tag with no items is empty.
				Ingredient read = contents.decode(buf);
				return read.isVanilla() && read.items().findAny().isEmpty() ? Optional.empty() : Optional.of(read);
			}
			if (marker == FORGE_CUSTOM) {
				// Forge's reading: an ingredient matching no items is empty.
				Ingredient read = contents.decode(buf);
				return read.items().findAny().isEmpty() ? Optional.empty() : Optional.of(read);
			}
			return forge.decode(buf);
		});
	}
}
