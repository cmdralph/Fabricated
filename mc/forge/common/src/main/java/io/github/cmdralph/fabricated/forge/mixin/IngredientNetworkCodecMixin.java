package io.github.cmdralph.fabricated.forge.mixin;

import java.util.Optional;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.forge.recipe.FabricIngredientCodec;
import io.github.cmdralph.fabricated.forge.recipe.OptionalIngredientCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Ingredients on the network as upstream sends them, where Forge 61 to 66's codecs can't: an empty optional ingredient
 * as vanilla's empty item list ({@link OptionalIngredientCodec}, CORPUS-83), and a Fabric mod's own ingredient in
 * vanilla's form ({@link FabricIngredientCodec}, CORPUS-85). The codecs Forge's patch builds are wrapped once the class
 * has made them, before any other class's codec (a recipe's, Fabric API's recipe sync) is built from them. By then the
 * codec Forge keeps for vanilla's ingredients is the one Fabric API's mixin wraps with its custom ingredient form.
 */
@Mixin(Ingredient.class)
abstract class IngredientNetworkCodecMixin {
	@Shadow
	@Final
	@Mutable
	public static StreamCodec<RegistryFriendlyByteBuf, Ingredient> CONTENTS_STREAM_CODEC;

	@Shadow
	@Final
	@Mutable
	public static StreamCodec<RegistryFriendlyByteBuf, Optional<Ingredient>> OPTIONAL_CONTENTS_STREAM_CODEC;

	/** Forge's codec for vanilla's ingredients, its patch's {@code VANILLA_SERIALIZER}'s. */
	@Shadow
	@Final
	private static StreamCodec<RegistryFriendlyByteBuf, Ingredient> VANILLA_CONTENTS_STREAM_CODEC;

	@Inject(method = "<clinit>", at = @At("TAIL"))
	private static void fabricated$sendAsUpstream(CallbackInfo ci) {
		CONTENTS_STREAM_CODEC = FabricIngredientCodec.codec(CONTENTS_STREAM_CODEC, VANILLA_CONTENTS_STREAM_CODEC);
		OPTIONAL_CONTENTS_STREAM_CODEC = OptionalIngredientCodec.codec(OPTIONAL_CONTENTS_STREAM_CODEC, CONTENTS_STREAM_CODEC);
	}
}
