package io.github.cmdralph.fabricated.forgetest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_1268;
import net.minecraft.class_1799;
import net.minecraft.class_2246;
import net.minecraft.class_2338;
import net.minecraft.class_3218;

/**
 * The forge-legacy test mod's part for fabric-events-interaction-v0's block break events (with Fabric API's jar only): a
 * Fabric fake player breaks two stone blocks through the game's own {@code destroyBlock} ({@code method_14266} of its
 * {@code gameMode}, {@code field_13974}). A {@code BEFORE} listener refuses the lower one, which must stay, with
 * {@code CANCELED} fired for it; the upper one must go, with {@code AFTER} fired for it as stone. Forge 47's
 * {@code destroyBlock} doesn't make the calls upstream's hooks fire these at, so the in-game mod fires them (BREAK-1).
 */
public final class ForgeBlockBreakTest {
	private static final Set<String> SEEN = ConcurrentHashMap.newKeySet();
	/** The lower block, which {@code BEFORE} refuses, and the upper one, right above. Set by {@link #check}. */
	private static volatile class_2338 refused;
	private static volatile class_2338 broken;

	private ForgeBlockBreakTest() {
	}

	/** From the main entrypoint; only with Fabric API's interaction module. */
	static void register() {
		if (!FabricLoader.getInstance().isModLoaded("fabric-events-interaction-v0")) return;
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (!pos.equals(refused)) return true;
			SEEN.add("before");
			return false;
		});
		PlayerBlockBreakEvents.CANCELED.register((level, player, pos, state, blockEntity) -> {
			if (pos.equals(refused)) SEEN.add("canceled");
		});
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (pos.equals(broken) && state.method_27852(class_2246.field_10340)) SEEN.add("after");
		});
	}

	/** At the first server tick, on the overworld. */
	public static void check(class_3218 level) {
		if (!FabricLoader.getInstance().isModLoaded("fabric-events-interaction-v0")) return;
		class_2338 lower = ForgeTestMod.testSite(level, 12, 284, 4);
		class_2338 upper = ForgeTestMod.testSite(level, 12, 285, 4);
		refused = lower;
		broken = upper;
		List<String> failed = new ArrayList<>();
		level.method_8652(lower, class_2246.field_10340.method_9564(), 3);
		level.method_8652(upper, class_2246.field_10340.method_9564(), 3);
		FakePlayer player = FakePlayer.get(level);
		player.method_6122(class_1268.field_5808, class_1799.field_8037);
		if (player.field_13974.method_14266(lower)) failed.add("destroyBlock broke the block BEFORE refused");
		if (!level.method_8320(lower).method_27852(class_2246.field_10340)) failed.add("the block BEFORE refused is gone");
		if (!player.field_13974.method_14266(upper)) failed.add("destroyBlock didn't break the other block");
		if (!level.method_8320(upper).method_26215()) failed.add("the other block is still there");
		for (String event : List.of("before", "canceled", "after")) {
			if (!SEEN.contains(event)) failed.add("no " + event.toUpperCase(Locale.ROOT) + " event");
		}
		level.method_8652(lower, class_2246.field_10124.method_9564(), 3);
		level.method_8652(upper, class_2246.field_10124.method_9564(), 3);
		if (failed.isEmpty()) {
			ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST block break events OK");
		} else {
			ForgeTestMod.LOGGER.warn("FABRICATED-FORGE-TEST block break events: {}", String.join("; ", failed));
		}
	}
}
