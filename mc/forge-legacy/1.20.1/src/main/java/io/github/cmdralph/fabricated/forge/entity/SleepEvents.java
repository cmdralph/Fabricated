package io.github.cmdralph.fabricated.forge.entity;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fabric API's bed occupation and sleeping direction events (fabric-entity-events-v1, passed through) where Forge 47
 * decides them (SLEEP-1), as on Forge 61 to 66. Forge's {@code LivingEntity} asks the bed's state ({@code BlockState.setBedOccupied},
 * {@code getBedDirection}) where vanilla sets the occupied property and asks {@code BedBlock}, which upstream's hooks
 * look for, so the plan leaves those two hooks out and {@code LivingEntitySleepMixin} runs the events at Forge's calls.
 *
 * <p>Upstream's hook that lets {@code ALLOW_BED} admit any block still applies: it stands a red bed in for the block in
 * the state {@code startSleeping} and {@code stopSleeping} check, so their bed checks pass, and its own occupation hook
 * read the real block again before setting anything. Forge's {@code setBedOccupied} on that stand-in set a red bed in
 * the world in the admitted block's place; {@link #setBedOccupied} sets the real block's state instead.
 */
public final class SleepEvents {
	/** Fabric API's entity events module is installed. Its classes are only touched then. */
	private static final boolean ENABLED = FabricLoader.getInstance().isModLoaded("fabric-entity-events-v1");

	private SleepEvents() {
	}

	/**
	 * Forge's {@code setBedOccupied} call for {@code sleeper} at {@code pos}, on {@code state} (the state its caller read,
	 * or upstream's stand-in), with Fabric's {@code SET_BED_OCCUPATION_STATE} asked first.
	 *
	 * @param forge Forge's call, on {@code state}
	 */
	public static void setBedOccupied(BlockState state, Level level, BlockPos pos, LivingEntity sleeper, boolean occupied, Runnable forge) {
		if (ENABLED) {
			Upstream.setBedOccupied(state, level, pos, sleeper, occupied, forge);
		} else {
			forge.run();
		}
	}

	/** Forge's answer to {@code getBedOrientation} for {@code sleeper}, through Fabric's {@code MODIFY_SLEEPING_DIRECTION}. */
	public static Direction sleepingDirection(LivingEntity sleeper, Direction forge) {
		return ENABLED ? Upstream.sleepingDirection(sleeper, forge) : forge;
	}

	/** Everything that names Fabric's entity events module: loaded only once {@link #ENABLED} is known to be true. */
	private static final class Upstream {
		private Upstream() {
		}

		static void setBedOccupied(BlockState state, Level level, BlockPos pos, LivingEntity sleeper, boolean occupied, Runnable forge) {
			// As upstream: the block that is there, which upstream's stand-in may have taken the place of in the caller.
			BlockState bed = level.getBlockState(pos);
			if (EntitySleepEvents.SET_BED_OCCUPATION_STATE.invoker().setBedOccupationState(sleeper, pos, bed, occupied)) return;
			if (bed == state) {
				forge.run();
			} else if (bed.isBed(level, pos, sleeper)) {
				bed.setBedOccupied(level, pos, sleeper, occupied);
			} else if (bed.hasProperty(BedBlock.OCCUPIED)) {
				// Upstream's widening for modded beds that don't answer the event.
				level.setBlock(pos, bed.setValue(BedBlock.OCCUPIED, occupied), 3);
			}
		}

		static Direction sleepingDirection(LivingEntity sleeper, Direction forge) {
			BlockPos pos = sleeper.getSleepingPos().orElse(null);
			if (pos == null) return forge;
			// Vanilla's BedBlock.getBedOrientation, which upstream passes on, answers null for a block that isn't a bed;
			// Forge answers up. A listener's null keeps Forge's answer, which its callers rely on being there.
			Level level = sleeper.level();
			@Nullable Direction vanilla = level.getBlockState(pos).isBed(level, pos, sleeper) ? forge : null;
			@Nullable Direction modified = EntitySleepEvents.MODIFY_SLEEPING_DIRECTION.invoker().modifySleepDirection(sleeper, pos, vanilla);
			return modified != null ? modified : forge;
		}
	}
}
