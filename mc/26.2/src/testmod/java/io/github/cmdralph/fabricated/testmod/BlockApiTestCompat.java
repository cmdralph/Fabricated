package io.github.cmdralph.fabricated.testmod;

import net.fabricmc.fabric.api.block.v1.FluidFlowEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** The 26.2+ parts of the block API test: enchanting power and fluid flow events. */
final class BlockApiTestCompat {
	private static final BlockPos FLUID_POS = new BlockPos(0, 250, 0);
	private static Block powerBlock;
	private static boolean fluidEventSeen;

	private BlockApiTestCompat() {
	}

	/** Two and a half bookshelves (in the enchantment_power_provider tag through the test mod's data). */
	private static final class PowerBlock extends Block {
		PowerBlock(BlockBehaviour.Properties properties) {
			super(properties);
		}

		@Override
		public float getProvidedEnchantmentPower(BlockState state, BlockGetter level, BlockPos pos) {
			return 2.5f;
		}
	}

	static void init() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, BlockApiTest.id("power_block"));
		powerBlock = Registry.register(BuiltInRegistries.BLOCK, key, new PowerBlock(BlockBehaviour.Properties.of().setId(key)));
		FluidFlowEvents.ALLOW.register((fluid, level, pos) -> {
			if (pos.equals(FLUID_POS)) fluidEventSeen = true;
			return true;
		});
	}

	static String check(ServerLevel level) {
		if (powerBlock.defaultBlockState().getEnchantPowerBonus(level, BlockPos.ZERO) != 2.5f) return "enchant_power";
		BlockState water = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("water")).defaultBlockState();
		BlockState air = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("air")).defaultBlockState();
		level.setBlockAndUpdate(FLUID_POS, water);
		level.setBlockAndUpdate(FLUID_POS, air);
		return fluidEventSeen ? null : "fluid_flow_event";
	}
}
