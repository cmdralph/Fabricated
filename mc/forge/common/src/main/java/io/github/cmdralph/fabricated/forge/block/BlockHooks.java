package io.github.cmdralph.fabricated.forge.block;

import net.fabricmc.fabric.api.block.v1.BlockFunctionalityTags;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fabric API's block hooks (fabric-block-api-v1, passed through) where Forge 61 to 66 ask their own. Forge decides
 * whether an open trapdoor is climbable through the block below's {@code makesOpenTrapdoorAboveClimbable}, from its own
 * ladder check, which never reaches vanilla's {@code trapdoorUsableAsLadder} that upstream's hook adds Fabric's
 * {@code fabric:can_climb_trapdoor_above} tag to; from 26.2 Forge's enchanting table sums each bookshelf's
 * {@code getEnchantPowerBonus}, where Fabric's blocks give theirs through {@code getProvidedEnchantmentPower}.
 * Fabricated's block mixins answer Forge's hooks from Fabric's; a block overriding them (a Forge mod's) keeps its own.
 * The mixins can't call Forge's defaults as their super (SUPER-1: Forge's Mixin rebinds such a call in a class whose
 * superclass is a mixin target, as {@code Block}'s is), so these answer with Forge's rule themselves.
 */
public final class BlockHooks {
	/** Fabric API's block module is installed. Its classes are only touched then. */
	public static final boolean ENABLED = FabricLoader.getInstance().isModLoaded("fabric-block-api-v1");

	private BlockHooks() {
	}

	/**
	 * Forge's {@code makesOpenTrapdoorAboveClimbable}: a ladder facing the trapdoor's way (Forge's rule, which upstream
	 * applies to a tagged ladder too), or any other block in Fabric's tag, as upstream's hook decides.
	 */
	public static boolean makesOpenTrapdoorAboveClimbable(BlockState state, BlockState trapdoorState) {
		if (state.getBlock() instanceof LadderBlock) return state.getValue(LadderBlock.FACING) == trapdoorState.getValue(TrapDoorBlock.FACING);
		return ENABLED && Upstream.tagged(state);
	}

	/** Everything that names Fabric's block module: loaded only once {@link #ENABLED} is known to be true. */
	private static final class Upstream {
		private Upstream() {
		}

		static boolean tagged(BlockState state) {
			return state.is(BlockFunctionalityTags.CAN_CLIMB_TRAPDOOR_ABOVE);
		}
	}
}
