package net.fabricmc.fabric.api.entity.event.v1;

import net.minecraft.class_1269;
import net.minecraft.class_1309;
import net.minecraft.class_1657;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_2680;

import net.fabricmc.fabric.api.event.Event;

public final class EntitySleepEvents {
	public static final Event<StartSleeping> START_SLEEPING = null;
	public static final Event<StopSleeping> STOP_SLEEPING = null;
	public static final Event<AllowBed> ALLOW_BED = null;
	public static final Event<AllowSleepTime> ALLOW_SLEEP_TIME = null;
	public static final Event<ModifySleepingDirection> MODIFY_SLEEPING_DIRECTION = null;
	public static final Event<SetBedOccupationState> SET_BED_OCCUPATION_STATE = null;

	private EntitySleepEvents() {
	}

	@FunctionalInterface
	public interface StartSleeping {
		void onStartSleeping(class_1309 entity, class_2338 sleepingPos);
	}

	@FunctionalInterface
	public interface StopSleeping {
		void onStopSleeping(class_1309 entity, class_2338 sleepingPos);
	}

	@FunctionalInterface
	public interface AllowBed {
		class_1269 allowBed(class_1309 entity, class_2338 sleepingPos, class_2680 state, boolean vanillaResult);
	}

	@FunctionalInterface
	public interface AllowSleepTime {
		class_1269 allowSleepTime(class_1657 player, class_2338 sleepingPos, boolean vanillaResult);
	}

	@FunctionalInterface
	public interface ModifySleepingDirection {
		class_2350 modifySleepDirection(class_1309 entity, class_2338 sleepingPos, class_2350 sleepingDirection);
	}

	@FunctionalInterface
	public interface SetBedOccupationState {
		boolean setBedOccupationState(class_1309 entity, class_2338 sleepingPos, class_2680 bedState, boolean occupied);
	}
}
