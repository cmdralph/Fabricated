package io.github.cmdralph.fabricated.forge.block;

import net.fabricmc.fabric.api.block.v1.FabricBlock;
import net.fabricmc.fabric.api.block.v1.FluidFlowEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The parts of Fabric API's block hooks only 26.2 and later have ({@link BlockHooks}): enchanting power and fluid flow
 * events. Fabricated's mixins into blocks call these, as Forge's Mixin reads every type a mixin's code names in a class
 * whose superclass is a mixin target, which Fabric API's types may not be.
 */
public final class BlockApiCompat {
	private BlockApiCompat() {
	}

	/**
	 * Forge's {@code getEnchantPowerBonus}: Fabric's {@code getProvidedEnchantmentPower} for a block in vanilla's power
	 * provider tag, as upstream's hook in the enchanting table counts it; Fabric's default is a bookshelf's power, Forge's
	 * rule.
	 */
	public static float enchantPowerBonus(Block block, BlockState state, LevelReader level, BlockPos pos) {
		if (!state.is(BlockTags.ENCHANTMENT_POWER_PROVIDER)) return 0;
		return BlockHooks.ENABLED ? Upstream.providedEnchantmentPower(block, state, level, pos) : 1;
	}

	/** Fabric's {@code FluidFlowEvents.ALLOW} for the liquid at {@code pos}: true without Fabric API's block module. */
	public static boolean allowFlow(Level level, BlockPos pos) {
		return !BlockHooks.ENABLED || Upstream.allowFlow(level, pos);
	}

	/** Everything that names Fabric's block module: loaded only once {@link BlockHooks#ENABLED} is known to be true. */
	private static final class Upstream {
		private Upstream() {
		}

		static float providedEnchantmentPower(Block block, BlockState state, LevelReader level, BlockPos pos) {
			return ((FabricBlock) block).getProvidedEnchantmentPower(state, level, pos);
		}

		static boolean allowFlow(Level level, BlockPos pos) {
			return FluidFlowEvents.ALLOW.invoker().allowFlow(level.getFluidState(pos), level, pos);
		}
	}
}
