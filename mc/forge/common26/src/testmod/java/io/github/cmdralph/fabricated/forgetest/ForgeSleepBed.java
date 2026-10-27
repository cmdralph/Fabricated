package io.github.cmdralph.fabricated.forgetest;

import java.util.function.Predicate;

import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.util.EventResult;
import net.minecraft.core.BlockPos;

/** {@link ForgeSleepTest}'s {@code ALLOW_BED} listener, in 26.x's Fabric API, whose answer is an event result. */
final class ForgeSleepBed {
	private ForgeSleepBed() {
	}

	/** Admits the block at a position {@code bed} accepts as a bed, and leaves the others to the other listeners. */
	static void register(Predicate<BlockPos> bed) {
		EntitySleepEvents.ALLOW_BED.register((entity, sleepingPos, state, vanillaResult) ->
				bed.test(sleepingPos) ? EventResult.ALLOW : EventResult.PASS);
	}
}
