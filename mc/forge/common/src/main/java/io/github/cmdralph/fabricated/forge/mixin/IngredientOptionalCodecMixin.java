package io.github.cmdralph.fabricated.forge.mixin;

import java.util.Optional;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.forge.recipe.OptionalIngredientCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * An empty optional ingredient goes over the network as vanilla sends it (see {@link OptionalIngredientCodec},
 * CORPUS-83). The codec Forge's patch builds is wrapped once the class has made it, before any other class's codec
 * (a shaped recipe's, Fabric API's recipe sync) is built from it.
 */
@Mixin(Ingredient.class)
abstract class IngredientOptionalCodecMixin {
	@Shadow
	@Final
	@Mutable
	public static StreamCodec<RegistryFriendlyByteBuf, Optional<Ingredient>> OPTIONAL_CONTENTS_STREAM_CODEC;

	@Inject(method = "<clinit>", at = @At("TAIL"))
	private static void fabricated$sendEmptyAsVanilla(CallbackInfo ci) {
		OPTIONAL_CONTENTS_STREAM_CODEC = OptionalIngredientCodec.codec(OPTIONAL_CONTENTS_STREAM_CODEC);
	}
}
