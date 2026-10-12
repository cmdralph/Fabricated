package io.github.cmdralph.fabricated.neoforge.block;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;

/** Native {@code fabric-block-api-v1} on 26.1.2, whose API has no enchanting power or fluid flow events. */
public final class BlockApiCompat {
	private BlockApiCompat() {
	}

	/** NeoForge's own default. */
	public static float enchantPowerBonus(BlockState state, BlockGetter level, BlockPos pos) {
		return state.is(BlockTags.ENCHANTMENT_POWER_PROVIDER) ? 1 : 0;
	}

	public static void register(IEventBus modBus) {
	}
}
