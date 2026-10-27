package net.fabricmc.fabric.api.entity.event.v1;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;

import net.fabricmc.fabric.api.event.Event;

/**
 * Compile-time stand-in: the members of Fabric API's 1.21.11 class that Fabricated's in-game mod and the forge test mod
 * call, in Mojang's names.
 */
public final class EntitySleepEvents {
	public static final Event<StartSleeping> START_SLEEPING = null;
	public static final Event<StopSleeping> STOP_SLEEPING = null;
	public static final Event<AllowBed> ALLOW_BED = null;
	public static final Event<ModifySleepingDirection> MODIFY_SLEEPING_DIRECTION = null;
	public static final Event<SetBedOccupationState> SET_BED_OCCUPATION_STATE = null;

	private EntitySleepEvents() {
	}

	@FunctionalInterface
	public interface StartSleeping {
		void onStartSleeping(LivingEntity entity, BlockPos sleepingPos);
	}

	@FunctionalInterface
	public interface StopSleeping {
		void onStopSleeping(LivingEntity entity, BlockPos sleepingPos);
	}

	@FunctionalInterface
	public interface AllowBed {
		InteractionResult allowBed(LivingEntity entity, BlockPos sleepingPos, BlockState state, boolean vanillaResult);
	}

	@FunctionalInterface
	public interface ModifySleepingDirection {
		@Nullable
		Direction modifySleepDirection(LivingEntity entity, BlockPos sleepingPos, @Nullable Direction sleepingDirection);
	}

	@FunctionalInterface
	public interface SetBedOccupationState {
		boolean setBedOccupationState(LivingEntity entity, BlockPos sleepingPos, BlockState bedState, boolean occupied);
	}
}
