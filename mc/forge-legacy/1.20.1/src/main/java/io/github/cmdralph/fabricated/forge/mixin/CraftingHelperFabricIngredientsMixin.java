package io.github.cmdralph.fabricated.forge.mixin;

import com.google.gson.JsonElement;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.cmdralph.fabricated.forge.content.FabricIngredients;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.CraftingHelper;

/**
 * An ingredient object with Fabric API's {@code fabric:type} is that custom ingredient where Forge 47 reads an
 * ingredient's JSON, alone or in an array ({@link FabricIngredients}, CORPUS-99); every other ingredient is Forge's to
 * read, as before.
 */
@Mixin(value = CraftingHelper.class, remap = false)
abstract class CraftingHelperFabricIngredientsMixin {
	@Inject(method = "getIngredient(Lcom/google/gson/JsonElement;Z)Lnet/minecraft/world/item/crafting/Ingredient;", at = @At("HEAD"), cancellable = true)
	private static void fabricated$fabricIngredient(JsonElement json, boolean allowEmpty, CallbackInfoReturnable<Ingredient> cir) {
		Ingredient fabric = FabricIngredients.fromJson(json);
		if (fabric != null) cir.setReturnValue(fabric);
	}
}
