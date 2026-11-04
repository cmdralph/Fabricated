package io.github.cmdralph.fabricated.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.item.crafting.ShapedRecipe;

/**
 * Porting Lib's crafting grid size reaches Forge 47's (CORPUS-99). Porting Lib ports Forge's
 * {@code ShapedRecipe.setCraftingSize} to Fabric as {@code ShapedRecipeUtil.setCraftingSize}, whose size its mixin of
 * vanilla's pattern reader puts in place of the 3 there; Create sets 9 by 9 for its mechanical crafting recipes. Forge
 * 47's reader checks its own size instead, where that mixin finds no 3, so Create's recipes over three rows or columns
 * failed. A size Porting Lib is given is given to Forge too, which keeps the larger of its own and that. Pseudo: Mixin
 * skips it without a word when there is no such class (without Porting Lib); not required, so that a Porting Lib build
 * whose method differs never stops the game.
 */
@Pseudo
@Mixin(targets = "io.github.fabricators_of_create.porting_lib.util.ShapedRecipeUtil", remap = false)
abstract class ShapedRecipeUtilCraftingSizeMixin {
	@Inject(method = "setCraftingSize", at = @At("TAIL"), require = 0)
	private static void fabricated$forgeCraftingSize(int width, int height, CallbackInfo ci) {
		ShapedRecipe.setCraftingSize(width, height);
	}
}
