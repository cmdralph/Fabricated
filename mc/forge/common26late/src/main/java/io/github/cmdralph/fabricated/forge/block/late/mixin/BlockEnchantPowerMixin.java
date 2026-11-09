package io.github.cmdralph.fabricated.forge.block.late.mixin;

import org.spongepowered.asm.mixin.Mixin;

import io.github.cmdralph.fabricated.forge.block.BlockApiCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Forge's enchanting power hook (26.2+), answered from Fabric's {@code FabricBlock.getProvidedEnchantmentPower}
 * ({@link BlockApiCompat}). Only adds a method to {@code Block}; a block overriding it keeps its own.
 */
@Mixin(Block.class)
abstract class BlockEnchantPowerMixin {
	public float getEnchantPowerBonus(BlockState state, LevelReader level, BlockPos pos) {
		return BlockApiCompat.enchantPowerBonus((Block) (Object) this, state, level, pos);
	}
}
