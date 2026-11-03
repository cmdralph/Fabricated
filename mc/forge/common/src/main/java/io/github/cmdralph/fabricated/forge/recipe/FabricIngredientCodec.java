package io.github.cmdralph.fabricated.forge.recipe;

import io.github.cmdralph.fabricated.loader.FabricatedState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * A Fabric mod's own ingredient on the network on Forge 61 to 66 (CORPUS-85). Forge's patch makes
 * {@code Ingredient.CONTENTS_STREAM_CODEC} write each ingredient through its {@code serializer()}, which throws for a
 * subclass that doesn't override it. A Fabric mod's subclass never does, because Fabric has no such method: Fabric
 * API's custom ingredients ({@code CustomIngredientImpl}) are one. So its recipe sync threw on any recipe with one, and
 * the player's connection to the world they joined dropped.
 *
 * <p>Such an ingredient is written as vanilla writes it: a marker, then vanilla's form through the codec Forge keeps
 * for vanilla's ingredients ({@code VANILLA_CONTENTS_STREAM_CODEC}). That codec is the one Fabric API wraps with its
 * custom ingredient form, as it wraps vanilla's {@code CONTENTS_STREAM_CODEC} on Fabric, so a Fabric API custom
 * ingredient goes as Fabric API sends it. Without the marker, Forge's reader would take the -1 that starts Fabric
 * API's form for its own. Every other ingredient is left to Forge's codec, and so are the bytes it writes.
 *
 * <p>Only a class of a Fabric mod's module that doesn't override {@code serializer()} is sent this way. A Forge mod's
 * subclass without one still gets Forge's error.
 */
public final class FabricIngredientCodec {
	/**
	 * The VarInt that starts a Fabric mod's ingredient in vanilla's form. Neither reader uses it: Forge's custom
	 * ingredients start with -1, Fabric API's custom form also starts with -1, and vanilla's holder set starts with 0 (a
	 * tag) or with its number of items plus 1.
	 */
	static final int VANILLA_FORM = -2;

	private static final ClassValue<Boolean> FABRIC_OWN = new ClassValue<>() {
		@Override
		protected Boolean computeValue(Class<?> type) {
			return fabricOwn(type);
		}
	};

	private FabricIngredientCodec() {
	}

	/** Whether Forge can't write the ingredient and upstream writes it in vanilla's form: a Fabric mod's subclass. */
	public static boolean sentAsVanilla(Ingredient ingredient) {
		return !ingredient.isVanilla() && FABRIC_OWN.get(ingredient.getClass());
	}

	/**
	 * Whether the class is in a Fabric mod's module, which Fabricated names after the mod's Forge id
	 * ({@code FabricJarMetadata}), and leaves Forge's {@code serializer()} as {@code Ingredient} has it.
	 */
	private static boolean fabricOwn(Class<?> type) {
		FabricatedState state = FabricatedState.get();
		String module = type.getModule().getName();
		if (state == null || module == null || !state.isFabricMod(module)) return false;
		try {
			return type.getMethod("serializer").getDeclaringClass() == Ingredient.class;
		} catch (NoSuchMethodException e) {
			return false;
		}
	}

	/**
	 * @param forge   Forge's {@code CONTENTS_STREAM_CODEC}
	 * @param vanilla Forge's {@code VANILLA_CONTENTS_STREAM_CODEC}, as Fabric API's mixin made it when it's installed
	 * @return Forge's codec, writing a Fabric mod's ingredient in vanilla's form after {@link #VANILLA_FORM} and reading
	 *         that back
	 */
	public static StreamCodec<RegistryFriendlyByteBuf, Ingredient> codec(StreamCodec<RegistryFriendlyByteBuf, Ingredient> forge,
			StreamCodec<RegistryFriendlyByteBuf, Ingredient> vanilla) {
		return StreamCodec.of((buf, ingredient) -> {
			if (sentAsVanilla(ingredient)) {
				buf.writeVarInt(VANILLA_FORM);
				vanilla.encode(buf, ingredient);
			} else {
				forge.encode(buf, ingredient);
			}
		}, buf -> {
			int start = buf.readerIndex();
			if (buf.readVarInt() == VANILLA_FORM) return vanilla.decode(buf);
			buf.readerIndex(start);
			return forge.decode(buf);
		});
	}
}
