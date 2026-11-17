package io.github.cmdralph.fabricated.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.serialization.Codec;

import io.github.cmdralph.fabricated.forge.recipe.FabricIngredientCodec;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * A Fabric mod's own ingredient written as JSON as upstream writes it, where Forge 52's codecs throw
 * ({@link FabricIngredientCodec#json}, CORPUS-115). Forge's patch makes {@code CODEC} and {@code CODEC_NONEMPTY} each
 * with {@code ForgeHooks.enhanceIngredientCodec} around vanilla's codec of the same kind, which upstream's hook of the
 * method making it gives Fabric API's custom form: both calls in the class initializer are wrapped, each with the
 * vanilla codec it is given.
 */
@Mixin(Ingredient.class)
abstract class IngredientJsonCodecMixin {
	@WrapOperation(method = "<clinit>", at = @At(value = "INVOKE",
			target = "Lnet/minecraftforge/common/ForgeHooks;enhanceIngredientCodec(Lcom/mojang/serialization/Codec;)Lcom/mojang/serialization/Codec;",
			remap = false))
	private static Codec<Ingredient> fabricated$writeAsUpstream(Codec<Ingredient> vanilla, Operation<Codec<Ingredient>> original) {
		return FabricIngredientCodec.json(original.call(vanilla), vanilla);
	}
}
