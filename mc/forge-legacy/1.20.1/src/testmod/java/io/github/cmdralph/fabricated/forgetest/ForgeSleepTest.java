package io.github.cmdralph.fabricated.forgetest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_1269;
import net.minecraft.class_1531;
import net.minecraft.class_2246;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_3218;

/**
 * The forge-legacy test mod's part for fabric-entity-events-v1's sleep events (with Fabric API's jar only): an armor
 * stand sleeps on a hay block that an {@code ALLOW_BED} listener admits as a bed, as a Fabric mod's sleeping bag would
 * be. The hay block must stay hay as the stand lies down ({@code method_18403}) and wakes up ({@code method_18400}), the
 * occupation event must fire as it lies down, and the stand's bed orientation ({@code method_18401}) must be the one
 * the {@code MODIFY_SLEEPING_DIRECTION} listener gives (SLEEP-1). Not as it wakes up: upstream's hooks into
 * {@code stopSleeping}'s lambda, which stand a bed in for the admitted block there, name the lambda as vanilla's SRG
 * names it, and are left out on Forge 47, whose patched class names it otherwise (LAMBDA-6, open), so Forge's bed
 * check passes the hay block by without asking for its occupation.
 */
public final class ForgeSleepTest {
	private static final Set<String> SEEN = ConcurrentHashMap.newKeySet();
	/** The hay block the listeners admit as a bed. */
	private static final class_2338 BED = new class_2338(24, 284, 24);

	private ForgeSleepTest() {
	}

	/** From the main entrypoint; only with Fabric API's entity events module. */
	static void register() {
		if (!FabricLoader.getInstance().isModLoaded("fabric-entity-events-v1")) return;
		EntitySleepEvents.ALLOW_BED.register((entity, sleepingPos, state, vanillaResult) ->
				sleepingPos.equals(BED) ? class_1269.field_5812 : class_1269.field_5811);
		EntitySleepEvents.SET_BED_OCCUPATION_STATE.register((entity, sleepingPos, bedState, occupied) -> {
			if (sleepingPos.equals(BED) && bedState.method_27852(class_2246.field_10359)) SEEN.add(occupied ? "occupied" : "unoccupied");
			return false;
		});
		EntitySleepEvents.MODIFY_SLEEPING_DIRECTION.register((entity, sleepingPos, sleepingDirection) ->
				sleepingPos.equals(BED) ? class_2350.field_11034 : sleepingDirection);
		EntitySleepEvents.START_SLEEPING.register((entity, sleepingPos) -> {
			if (sleepingPos.equals(BED)) SEEN.add("start");
		});
		EntitySleepEvents.STOP_SLEEPING.register((entity, sleepingPos) -> {
			if (sleepingPos.equals(BED)) SEEN.add("stop");
		});
	}

	/** At the first server tick, on the overworld. */
	public static void check(class_3218 level) {
		if (!FabricLoader.getInstance().isModLoaded("fabric-entity-events-v1")) return;
		List<String> failed = new ArrayList<>();
		level.method_8652(BED, class_2246.field_10359.method_9564(), 3);
		class_1531 sleeper = new class_1531(level, BED.method_10263() + 0.5, BED.method_10264() + 1, BED.method_10260() + 0.5);
		sleeper.method_18403(BED);
		if (!level.method_8320(BED).method_27852(class_2246.field_10359)) failed.add("lying down turned the hay block into " + level.method_8320(BED));
		class_2350 orientation = sleeper.method_18401();
		if (orientation != class_2350.field_11034) failed.add("the bed orientation is " + orientation + ", not MODIFY_SLEEPING_DIRECTION's east");
		sleeper.method_18400();
		if (!level.method_8320(BED).method_27852(class_2246.field_10359)) failed.add("waking up turned the hay block into " + level.method_8320(BED));
		for (String event : List.of("start", "occupied", "stop")) {
			if (!SEEN.contains(event)) failed.add("no " + event + " event");
		}
		level.method_8652(BED, class_2246.field_10124.method_9564(), 3);
		if (failed.isEmpty()) {
			ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST sleep events OK");
		} else {
			ForgeTestMod.LOGGER.warn("FABRICATED-FORGE-TEST sleep events: {}", String.join("; ", failed));
		}
	}
}
