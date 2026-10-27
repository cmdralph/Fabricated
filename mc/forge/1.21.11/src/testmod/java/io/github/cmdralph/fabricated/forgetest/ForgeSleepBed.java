package io.github.cmdralph.fabricated.forgetest;

import java.util.function.Predicate;

import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;

/** {@link ForgeSleepTest}'s {@code ALLOW_BED} listener, in 1.21.11's Fabric API, whose answer is an interaction result. */
final class ForgeSleepBed {
	private ForgeSleepBed() {
	}

	/** Admits the block at a position {@code bed} accepts as a bed, and leaves the others to the other listeners. */
	static void register(Predicate<BlockPos> bed) {
		EntitySleepEvents.ALLOW_BED.register((entity, sleepingPos, state, vanillaResult) ->
				bed.test(sleepingPos) ? InteractionResult.SUCCESS : InteractionResult.PASS);
	}
}
