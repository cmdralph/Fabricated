package io.github.cmdralph.fabricated.forge.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.forge.recipe.FabricIngredientCodec;
import io.github.cmdralph.fabricated.forge.recipe.VanillaIngredientCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * A Fabric mod's own ingredient on the network in vanilla's form, where Forge 52's codec can't write it
 * ({@link FabricIngredientCodec}, NET-13, as Forge 61 to 66's does: CORPUS-85), with Fabric API's custom ingredients in
 * their own form ({@link VanillaIngredientCodec}). The codec Forge's patch makes is wrapped once the class has made it,
 * before any other class's codec (a recipe's, Fabric API's recipe sync) is built from it.
 */
@Mixin(Ingredient.class)
abstract class IngredientNetworkCodecMixin {
	@Shadow
	@Final
	@Mutable
	public static StreamCodec<RegistryFriendlyByteBuf, Ingredient> CONTENTS_STREAM_CODEC;

	@Inject(method = "<clinit>", at = @At("TAIL"))
	private static void fabricated$sendAsUpstream(CallbackInfo ci) {
		CONTENTS_STREAM_CODEC = FabricIngredientCodec.codec(CONTENTS_STREAM_CODEC, VanillaIngredientCodec.CODEC);
	}
}
