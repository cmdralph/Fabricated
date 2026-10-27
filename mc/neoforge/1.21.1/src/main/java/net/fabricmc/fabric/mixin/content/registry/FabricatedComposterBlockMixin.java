package net.fabricmc.fabric.mixin.content.registry;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import io.github.cmdralph.fabricated.neoforge.registry.FabricTableEntries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ComposterBlock;

/**
 * NeoForge (up to 26.2) reads composting chances from its data map only; Fabric's compostable registry writes vanilla's
 * table. An item the data map leaves out gets its chance from that table, so data packs still win. Not on 26.3,
 * where composting is data-driven in vanilla and Fabric API has no such registry.
 */
@Mixin(ComposterBlock.class)
abstract class FabricatedComposterBlockMixin {
	@ModifyReturnValue(method = "getValue(Lnet/minecraft/world/item/ItemStack;)F", at = @At("RETURN"))
	private static float fabricated$tableChance(float chance, ItemStack stack) {
		return chance >= 0 ? chance : FabricTableEntries.compostChance(stack);
	}
}
