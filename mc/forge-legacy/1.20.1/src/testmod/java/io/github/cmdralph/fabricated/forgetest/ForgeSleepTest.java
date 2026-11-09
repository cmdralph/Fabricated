package io.github.cmdralph.fabricated.forgetest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.datafixers.util.Either;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The forge-legacy test mod's part for fabric-entity-events-v1's sleep events (with Fabric API's jar only): an armor
 * stand sleeps on a hay block that an {@code ALLOW_BED} listener admits as a bed, as a Fabric mod's sleeping bag would
 * be. The hay block must stay hay as the stand lies down ({@code startSleeping}) and wakes up ({@code stopSleeping}), the
 * occupation event must fire both times, and the stand's bed orientation ({@code getBedOrientation}) must be the one the
 * {@code MODIFY_SLEEPING_DIRECTION} listener gives (SLEEP-1). As it wakes up, upstream's hooks into {@code stopSleeping}'s
 * lambda stand a bed in for the hay block, which Forge 47's patched class names otherwise than vanilla's SRG name
 * (LAMBDA-6). Then a fake player goes to sleep on the same block ({@code startSleepInBed}) in the daytime, which an
 * {@code ALLOW_SLEEP_TIME} listener allows: it must be asked, where Forge 47 asks its own sleep time event, and the
 * game must let the player sleep (SLEEP-2). Fabric's fake players never lie down, so the game's answer is the check.
 */
public final class ForgeSleepTest {
	private static final Set<String> SEEN = ConcurrentHashMap.newKeySet();
	/** The hay block the listeners admit as a bed. Set by {@link #check}. */
	private static volatile BlockPos bed;

	private ForgeSleepTest() {
	}

	/** From the main entrypoint; only with Fabric API's entity events module. */
	static void register() {
		if (!FabricLoader.getInstance().isModLoaded("fabric-entity-events-v1")) return;
		EntitySleepEvents.ALLOW_BED.register((entity, sleepingPos, state, vanillaResult) ->
				sleepingPos.equals(bed) ? InteractionResult.SUCCESS : InteractionResult.PASS);
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
		EntitySleepEvents.ALLOW_SLEEP_TIME.register((player, sleepingPos, vanillaResult) -> {
			if (!(player instanceof FakePlayer) || !sleepingPos.equals(bed)) return InteractionResult.PASS;
			SEEN.add("sleep time");
			return InteractionResult.SUCCESS;
		});
	}

	/** At the first server tick, on the overworld. */
	public static void check(ServerLevel level) {
		if (!FabricLoader.getInstance().isModLoaded("fabric-entity-events-v1")) return;
		BlockPos pos = ForgeTestMod.testSite(level, 8, 284, 8);
		bed = pos;
		List<String> failed = new ArrayList<>();
		level.setBlock(pos, Blocks.HAY_BLOCK.defaultBlockState(), 3);
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
		FakePlayer player = FakePlayer.get(level);
		player.moveTo(pos.getX() + 1.5, pos.getY() + 1, pos.getZ() + 0.5, 0, 0);
		Either<?, ?> slept = player.startSleepInBed(pos);
		if (slept.right().isEmpty()) failed.add("the game didn't let the fake player sleep at the time ALLOW_SLEEP_TIME allows: " + slept);
		if (!SEEN.contains("sleep time")) failed.add("ALLOW_SLEEP_TIME wasn't asked as the fake player tried to sleep");
		level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
		if (failed.isEmpty()) {
			ForgeTestMod.LOGGER.info("FABRICATED-TEST sleep events OK");
		} else {
			ForgeTestMod.LOGGER.warn("FABRICATED-TEST sleep events: {}", String.join("; ", failed));
		}
	}
}
