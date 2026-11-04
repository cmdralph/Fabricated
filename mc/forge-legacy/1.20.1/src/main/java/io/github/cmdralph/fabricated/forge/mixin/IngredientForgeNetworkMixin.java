package io.github.cmdralph.fabricated.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.cmdralph.fabricated.forge.content.FabricIngredients;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Forge's custom ingredients are read by Forge where Fabric API's hook of {@code Ingredient.fromNetwork} would take
 * their -1 for its own ({@link FabricIngredients}, NET-11). Its priority puts its handler before Fabric API's (1000):
 * Mixin finds every mixin's injection points in a class before it injects any, and puts each callback at the head
 * before the method's first instruction, so after those of the mixins it applied earlier, the lower priorities.
 */
@Mixin(value = Ingredient.class, priority = 900)
abstract class IngredientForgeNetworkMixin {
	@Inject(method = "fromNetwork", at = @At("HEAD"), cancellable = true)
	private static void fabricated$forgeIngredient(FriendlyByteBuf buf, CallbackInfoReturnable<Ingredient> cir) {
		Ingredient forge = FabricIngredients.forgeFromNetwork(buf);
		if (forge != null) cir.setReturnValue(forge);
	}
}
