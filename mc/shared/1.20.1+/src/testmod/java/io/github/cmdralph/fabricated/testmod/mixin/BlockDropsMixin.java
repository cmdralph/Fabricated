package io.github.cmdralph.fabricated.testmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.testmod.VanillaHarvestTest;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * What a block is asked to drop ({@code Block.popResource}, which vanilla's {@code dropResources} calls for each stack, and
 * through which NeoForge captures the drops for its drops event), recorded for the harvest check while it runs
 * ({@link VanillaHarvestTest}): the item entities a drop makes may not show yet in a chunk the check has just loaded.
 */
@Mixin(Block.class)
public abstract class BlockDropsMixin {
	@Inject(method = "popResource(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"))
	private static void fabricatedTest$popped(Level level, BlockPos pos, ItemStack stack, CallbackInfo ci) {
		VanillaHarvestTest.popped(pos, stack);
	}
}
