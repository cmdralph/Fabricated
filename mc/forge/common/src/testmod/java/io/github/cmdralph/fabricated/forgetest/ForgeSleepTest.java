package io.github.cmdralph.fabricated.forgetest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;

/**
 * The forge test mod's part for fabric-entity-events-v1's sleep events (with Fabric API's jar only): an armor stand
 * sleeps on a hay block that an {@code ALLOW_BED} listener admits as a bed, as a Fabric mod's sleeping bag would be. The
 * hay block must stay hay as the stand lies down and wakes up (its {@code SET_BED_OCCUPATION_STATE} listener leaves the
 * block as it is, and the block has no occupied property to set), the occupation event must fire both times, and the
 * stand's bed orientation must be the one the {@code MODIFY_SLEEPING_DIRECTION} listener gives (SLEEP-1).
 */
public final class ForgeSleepTest {
	private static final Set<String> SEEN = ConcurrentHashMap.newKeySet();
	/** The hay block the listeners admit as a bed. Set by {@link #check}. */
	private static volatile BlockPos bed;

	private ForgeSleepTest() {
	}

	/** From the main entrypoint, as Fabric mods register their listeners; only with Fabric API's entity events module. */
	public static void register() {
		ForgeSleepBed.register(pos -> pos.equals(bed));
		EntitySleepEvents.SET_BED_OCCUPATION_STATE.register((entity, sleepingPos, bedState, occupied) -> {
			if (sleepingPos.equals(bed) && bedState.is(Blocks.HAY_BLOCK)) SEEN.add(occupied ? "occupied" : "unoccupied");
			return false;
		});
		EntitySleepEvents.MODIFY_SLEEPING_DIRECTION.register((entity, sleepingPos, sleepingDirection) ->
				sleepingPos.equals(bed) ? Direction.EAST : sleepingDirection);
		EntitySleepEvents.START_SLEEPING.register((entity, sleepingPos) -> {
			if (sleepingPos.equals(bed)) SEEN.add("start");
		});
		EntitySleepEvents.STOP_SLEEPING.register((entity, sleepingPos) -> {
			if (sleepingPos.equals(bed)) SEEN.add("stop");
		});
	}

	/** At the first server tick, on the overworld. */
	public static void check(ServerLevel level) {
		BlockPos pos = new BlockPos(24, level.getMaxY() - 30, 24);
		bed = pos;
		List<String> failed = new ArrayList<>();
		level.setBlockAndUpdate(pos, Blocks.HAY_BLOCK.defaultBlockState());
		ArmorStand sleeper = new ArmorStand(level, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
		sleeper.startSleeping(pos);
		if (!level.getBlockState(pos).is(Blocks.HAY_BLOCK)) failed.add("lying down turned the hay block into " + level.getBlockState(pos));
		Direction orientation = sleeper.getBedOrientation();
		if (orientation != Direction.EAST) failed.add("the bed orientation is " + orientation + ", not MODIFY_SLEEPING_DIRECTION's east");
		sleeper.stopSleeping();
		if (!level.getBlockState(pos).is(Blocks.HAY_BLOCK)) failed.add("waking up turned the hay block into " + level.getBlockState(pos));
		for (String event : List.of("start", "occupied", "stop", "unoccupied")) {
			if (!SEEN.contains(event)) failed.add("no " + event + " event");
		}
		level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
		if (failed.isEmpty()) {
			ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST sleep events OK");
		} else {
			ForgeTestMod.LOGGER.warn("FABRICATED-FORGE-TEST sleep events: {}", String.join("; ", failed));
		}
	}
}
