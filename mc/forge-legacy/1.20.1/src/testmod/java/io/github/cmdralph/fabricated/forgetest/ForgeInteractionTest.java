package io.github.cmdralph.fabricated.forgetest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.class_1268;
import net.minecraft.class_1269;
import net.minecraft.class_1271;
import net.minecraft.class_1531;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_2246;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_243;
import net.minecraft.class_2824;
import net.minecraft.class_2846;
import net.minecraft.class_3218;
import net.minecraft.class_3965;
import net.minecraft.class_4076;

/**
 * The forge-legacy test mod's part for fabric-events-interaction-v0's use and attack callbacks (with Fabric API's jar
 * only): a Fabric fake player goes through the game's own interaction code, which Forge 47's patches change where
 * upstream's hooks are, and each callback must be asked and its answer honoured. Using a stick ({@code method_14256} of
 * its {@code gameMode}) fails, using the stone block ({@code method_14262}) succeeds, attacking it
 * ({@code method_14263}) fails, and attacking ({@code method_7324}) and using (the server's handling of an interaction
 * packet, {@code method_12062} of its {@code connection}) an armor stand both fail. The game finds an entity a player
 * uses by its id among the entities of chunks that track them, so the armor stand's part waits for the test site's
 * chunk, forced ({@code method_17988}), to tick its entities ({@code method_37118}), as the forge test mod's does. It
 * passes a fake player where the game takes a server player, so verifying it loads Fabric API's class: the test mod
 * only touches it with the interaction module installed.
 */
public final class ForgeInteractionTest {
	private static final Set<String> SEEN = ConcurrentHashMap.newKeySet();
	/** How long the armor stand's part waits for the site's chunk to tick entities. */
	private static final int MAX_WAIT = 100;
	private static final List<String> FAILED = new ArrayList<>();
	/** The stone block the callbacks answer for. Set by {@link #check}. */
	private static volatile class_2338 site;
	/** Ticks waited for the armor stand's part, or -1 when none is pending. */
	private static int waited = -1;

	private ForgeInteractionTest() {
	}

	private static <T> T seen(String what, T result) {
		SEEN.add(what);
		return result;
	}

	/** From the main entrypoint, as Fabric mods register their listeners; only with Fabric API's interaction module. */
	static void register() {
		UseItemCallback.EVENT.register((player, level, hand) -> {
			class_1799 stack = player.method_5998(hand);
			return player instanceof FakePlayer && stack.method_31574(class_1802.field_8600)
					? seen("use item", class_1271.method_22431(stack)) : class_1271.method_22430(stack);
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> player instanceof FakePlayer && hit.method_17777().equals(site)
				? seen("use block", class_1269.field_5812) : class_1269.field_5811);
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> player instanceof FakePlayer && pos.equals(site)
				? seen("attack block", class_1269.field_5814) : class_1269.field_5811);
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> player instanceof FakePlayer && entity instanceof class_1531
				? seen("attack entity", class_1269.field_5814) : class_1269.field_5811);
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> player instanceof FakePlayer && entity instanceof class_1531
				? seen("use entity", class_1269.field_5814) : class_1269.field_5811);
	}

	/** At the first server tick, on the overworld: the item and block uses, then the site's chunk forced for the rest. */
	static void check(class_3218 level) {
		class_2338 pos = ForgeTestMod.testSite(level, 20, 284, 4);
		site = pos;
		level.method_8652(pos, class_2246.field_10340.method_9564(), 3);
		FakePlayer player = player(level, pos);
		class_1268 hand = class_1268.field_5808;
		class_1799 stick = new class_1799(class_1802.field_8600);
		player.method_6122(hand, stick);
		class_1269 result = player.field_13974.method_14256(player, level, stick, hand);
		if (result != class_1269.field_5814) FAILED.add("using the stick gave " + result);
		class_243 center = new class_243(pos.method_10263() + 0.5, pos.method_10264() + 0.5, pos.method_10260() + 0.5);
		result = player.field_13974.method_14262(player, level, stick, hand, new class_3965(center, class_2350.field_11036, pos, false));
		if (result != class_1269.field_5812) FAILED.add("using the block gave " + result);
		player.field_13974.method_14263(pos, class_2846.class_2847.field_12968, class_2350.field_11036, level.method_31600(), 0);
		player.method_6122(hand, class_1799.field_8037);
		level.method_17988(class_4076.method_18675(pos.method_10263()), class_4076.method_18675(pos.method_10260()), true);
		waited = 0;
	}

	/** At every later server tick: the armor stand's part, once the site's chunk ticks entities, and the result. */
	static void tick(class_3218 level) {
		if (waited < 0) return;
		class_2338 pos = site;
		if (!level.method_37118(pos) && ++waited < MAX_WAIT) return;
		waited = -1;
		if (level.method_37118(pos)) {
			FakePlayer player = player(level, pos);
			class_1531 stand = new class_1531(level, pos.method_10263() + 0.5, pos.method_10264() + 1, pos.method_10260() + 0.5);
			level.method_8649(stand);
			player.method_7324(stand);
			if (!stand.method_5805()) FAILED.add("the refused attack broke the armor stand");
			player.field_13987.method_12062(class_2824.method_34208(stand, false, class_1268.field_5808, new class_243(0, 1, 0)));
			stand.method_31472();
		} else {
			FAILED.add("the test site's chunk didn't tick entities within " + MAX_WAIT + " ticks");
		}
		level.method_17988(class_4076.method_18675(pos.method_10263()), class_4076.method_18675(pos.method_10260()), false);
		level.method_8652(pos, class_2246.field_10124.method_9564(), 3);
		for (String what : List.of("use item", "use block", "attack block", "attack entity", "use entity")) {
			if (!SEEN.contains(what)) FAILED.add("no " + what + " callback");
		}
		if (FAILED.isEmpty()) {
			ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST interaction events OK");
		} else {
			ForgeTestMod.LOGGER.warn("FABRICATED-FORGE-TEST interaction events: {}", String.join("; ", FAILED));
		}
	}

	/** The fake player next to the site, within reach of the block and the armor stand. */
	private static FakePlayer player(class_3218 level, class_2338 pos) {
		FakePlayer player = FakePlayer.get(level);
		player.method_5814(pos.method_10263() + 2.5, pos.method_10264(), pos.method_10260() + 0.5);
		return player;
	}
}
