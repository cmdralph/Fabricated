package io.github.cmdralph.fabricated.forge.mixin;

import java.util.Map;

import com.google.gson.JsonObject;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.cmdralph.fabricated.forge.content.FabricRecipeConditions;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.IConditionSerializer;

/**
 * A condition type Forge 47 has no serializer of is Fabric API's condition of that id, where Fabric API has one
 * ({@link FabricRecipeConditions}, CORPUS-97); Forge's own types, and a type neither knows, go on as before.
 */
@Mixin(value = CraftingHelper.class, remap = false)
abstract class CraftingHelperFabricConditionsMixin {
	@Shadow
	@Final
	private static Map<ResourceLocation, IConditionSerializer<?>> conditions;

	@Inject(method = "getCondition", at = @At("HEAD"), cancellable = true)
	private static void fabricated$fabricCondition(JsonObject json, CallbackInfoReturnable<ICondition> cir) {
		ICondition fabric = FabricRecipeConditions.of(json, conditions::containsKey);
		if (fabric != null) cir.setReturnValue(fabric);
	}
}
