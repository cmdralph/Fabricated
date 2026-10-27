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
	/** The lower block, which {@code BEFORE} refuses; the upper one is right above. */
	private static final class_2338 REFUSED = new class_2338(28, 284, 20);
	private static final class_2338 BROKEN = new class_2338(28, 285, 20);

	private ForgeBlockBreakTest() {
	}

	/** From the main entrypoint; only with Fabric API's interaction module. */
	static void register() {
		if (!FabricLoader.getInstance().isModLoaded("fabric-events-interaction-v0")) return;
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (!pos.equals(REFUSED)) return true;
			SEEN.add("before");
			return false;
		});
		PlayerBlockBreakEvents.CANCELED.register((level, player, pos, state, blockEntity) -> {
			if (pos.equals(REFUSED)) SEEN.add("canceled");
		});
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (pos.equals(BROKEN) && state.method_27852(class_2246.field_10340)) SEEN.add("after");
		});
	}

	/** At the first server tick, on the overworld. */
	public static void check(class_3218 level) {
		if (!FabricLoader.getInstance().isModLoaded("fabric-events-interaction-v0")) return;
		List<String> failed = new ArrayList<>();
		level.method_8652(REFUSED, class_2246.field_10340.method_9564(), 3);
		level.method_8652(BROKEN, class_2246.field_10340.method_9564(), 3);
		FakePlayer player = FakePlayer.get(level);
		player.method_6122(class_1268.field_5808, class_1799.field_8037);
		if (player.field_13974.method_14266(REFUSED)) failed.add("destroyBlock broke the block BEFORE refused");
		if (!level.method_8320(REFUSED).method_27852(class_2246.field_10340)) failed.add("the block BEFORE refused is gone");
		if (!player.field_13974.method_14266(BROKEN)) failed.add("destroyBlock didn't break the other block");
		if (!level.method_8320(BROKEN).method_26215()) failed.add("the other block is still there");
		for (String event : List.of("before", "canceled", "after")) {
			if (!SEEN.contains(event)) failed.add("no " + event.toUpperCase(Locale.ROOT) + " event");
		}
		level.method_8652(REFUSED, class_2246.field_10124.method_9564(), 3);
		level.method_8652(BROKEN, class_2246.field_10124.method_9564(), 3);
		if (failed.isEmpty()) {
			ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST block break events OK");
		} else {
			ForgeTestMod.LOGGER.warn("FABRICATED-FORGE-TEST block break events: {}", String.join("; ", failed));
		}
	}
}
