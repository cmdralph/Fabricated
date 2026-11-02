package io.github.cmdralph.fabricated.forge.content.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Fabric API's brewing recipes ({@code FabricBrewingRecipeRegistry}, fabric-content-registries-v0, passed through) on
 * Forge 47 (BREW-1). Upstream builds a brewing mix with vanilla's constructor, which Forge 47's patch replaced with one
 * that also takes the registry the mix's ends are in (Forge keeps them as registry holders), so every call threw
 * {@code NoSuchMethodError}: a Fabric mod registering a potion or a potion item recipe failed its initializer. Each
 * mix is built with Forge's constructor instead, as vanilla's own recipes are there, from the potion or the item
 * registry. Pseudo: Mixin skips it without a word when there is no such class (without Fabric API); not required, so
 * that a Fabric API build whose method differs never stops the game.
 */
@Pseudo
@Mixin(targets = "net.fabricmc.fabric.api.registry.FabricBrewingRecipeRegistry", remap = false)
abstract class FabricBrewingRecipeRegistryMixin {
	@Redirect(method = "registerPotionRecipe", at = @At(value = "NEW", target = "net/minecraft/world/item/alchemy/PotionBrewing$Mix"), require = 0)
	private static PotionBrewing.Mix<Potion> fabricated$potionMix(Object input, Ingredient ingredient, Object output) {
		return new PotionBrewing.Mix<>(ForgeRegistries.POTIONS, (Potion) input, ingredient, (Potion) output);
	}

	@Redirect(method = "registerItemRecipe", at = @At(value = "NEW", target = "net/minecraft/world/item/alchemy/PotionBrewing$Mix"), require = 0)
	private static PotionBrewing.Mix<Item> fabricated$itemMix(Object input, Ingredient ingredient, Object output) {
		return new PotionBrewing.Mix<>(ForgeRegistries.ITEMS, (Item) input, ingredient, (Item) output);
	}
}
