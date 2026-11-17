package io.github.cmdralph.fabricated.forge.recipe;

import java.util.Arrays;

import net.fabricmc.fabric.impl.recipe.ingredient.CustomIngredientPacketCodec;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Vanilla 1.21.1's network form of an ingredient, its items, which Forge 52's patch of {@code Ingredient} replaced with
 * its own codec and keeps no copy of (NET-13): {@link FabricIngredientCodec} writes a Fabric mod's own ingredient so. On
 * Fabric, Fabric API's recipe module (fabric-recipe-api-v1, passed through) wraps vanilla's codec with its custom
 * ingredient form as the class makes it, where Forge 52's makes none, so pre-flight leaves that hook out: the codec here
 * is wrapped as the hook would wrap it, where the module is installed. Which it is is asked at the first ingredient
 * written or read, long after the class was made.
 */
public final class VanillaIngredientCodec {
	/** Vanilla's form, with Fabric API's custom form around it where its recipe module is installed. */
	public static final StreamCodec<RegistryFriendlyByteBuf, Ingredient> CODEC = StreamCodec.of(
			(buf, ingredient) -> Chosen.CODEC.encode(buf, ingredient), buf -> Chosen.CODEC.decode(buf));

	private VanillaIngredientCodec() {
	}

	/** The codec, once it is first used. */
	private static final class Chosen {
		/** As vanilla 1.21.1's {@code Ingredient.CONTENTS_STREAM_CODEC} writes it: the items it matches. */
		private static final StreamCodec<RegistryFriendlyByteBuf, Ingredient> ITEMS = ItemStack.LIST_STREAM_CODEC.map(
				items -> Ingredient.fromValues(items.stream().map(Ingredient.ItemValue::new)), ingredient -> Arrays.asList(ingredient.getItems()));
		static final StreamCodec<RegistryFriendlyByteBuf, Ingredient> CODEC = FabricLoader.getInstance().isModLoaded("fabric-recipe-api-v1")
				? Upstream.wrap(ITEMS) : ITEMS;

		private Chosen() {
		}
	}

	/** Everything that names Fabric's recipe module: loaded only once it is known to be installed. */
	private static final class Upstream {
		private Upstream() {
		}

		static StreamCodec<RegistryFriendlyByteBuf, Ingredient> wrap(StreamCodec<RegistryFriendlyByteBuf, Ingredient> vanilla) {
			return new CustomIngredientPacketCodec(vanilla);
		}
	}
}
