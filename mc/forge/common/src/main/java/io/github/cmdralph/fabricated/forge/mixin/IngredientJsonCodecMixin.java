package io.github.cmdralph.fabricated.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.serialization.Codec;

import io.github.cmdralph.fabricated.forge.recipe.FabricIngredientCodec;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * A Fabric mod's own ingredient written as JSON as upstream writes it, where Forge 61 to 66's codec throws
 * ({@link FabricIngredientCodec#json}, CORPUS-115). Forge's patch makes {@code CODEC} with
 * {@code ForgeHooks.ingredientBaseCodec} around vanilla's codec; the call is wrapped as the class initializer makes it,
 * before upstream's hook at its end wraps the field with Fabric API's custom form, which so still writes Fabric API's
 * custom ingredients itself.
 */
@Mixin(Ingredient.class)
abstract class IngredientJsonCodecMixin {
	@WrapOperation(method = "<clinit>", at = @At(value = "INVOKE",
			target = "Lnet/minecraftforge/common/ForgeHooks;ingredientBaseCodec(Lcom/mojang/serialization/Codec;)Lcom/mojang/serialization/Codec;",
			remap = false))
	private static Codec<Ingredient> fabricated$writeAsUpstream(Codec<Ingredient> vanilla, Operation<Codec<Ingredient>> original) {
		return FabricIngredientCodec.json(original.call(vanilla), vanilla);
	}
}
