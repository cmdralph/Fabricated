package io.github.cmdralph.fabricated.neoforge.block;

import net.fabricmc.fabric.api.block.v1.FluidFlowEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidInteractionRegistry;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Native {@code fabric-block-api-v1}, the parts of the API only 26.3 has: enchanting power and fluid flow events. */
public final class BlockApiCompat {
	/** {@link FluidInteractionRegistry#canInteract} tries each direction in turn; Fabric's event is asked once, on the first. */
	private static final Direction FIRST_SOURCE_SIDE = LiquidBlock.POSSIBLE_FLOW_DIRECTIONS.getFirst().getOpposite();

	private BlockApiCompat() {
	}

	public static float enchantPowerBonus(BlockState state, BlockGetter level, BlockPos pos) {
		return state.is(BlockTags.ENCHANTMENT_POWER_PROVIDER) ? state.getProvidedEnchantmentPower(level, pos) : 0;
	}

	/**
	 * Fabric's {@code FluidFlowEvents.ALLOW}, asked where NeoForge lets fluids interact with their surroundings: when a
	 * neighbour of a liquid block changes (where vanilla makes obsidian or basalt). Returning false there stops the
	 * liquid spreading from that update, as upstream. NeoForge has no hook where a liquid block is placed or a fluid
	 * flows into a new position, so the event isn't asked there.
	 */
	public static void register(IEventBus modBus) {
		modBus.addListener(FMLCommonSetupEvent.class, event -> event.enqueueWork(() -> {
			for (FluidType type : NeoForgeRegistries.FLUID_TYPES) {
				if (type == NeoForgeMod.EMPTY_TYPE.value()) continue;
				FluidInteractionRegistry.addInteraction(type, new FluidInteractionRegistry.InteractionInformation(
						(level, pos, relativePos, fluid) -> relativePos.equals(pos.relative(FIRST_SOURCE_SIDE))
								&& !FluidFlowEvents.ALLOW.invoker().allowFlow(fluid, level, pos),
						(level, pos, relativePos, fluid) -> {
						}));
			}
		}));
	}
}
