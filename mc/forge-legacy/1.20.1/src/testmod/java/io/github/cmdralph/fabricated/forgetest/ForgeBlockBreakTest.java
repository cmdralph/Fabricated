package io.github.cmdralph.fabricated.forgetest;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The forge-legacy test mod's part for fabric-events-interaction-v0's block break events (with Fabric API's jar only): a
 * Fabric fake player breaks two stone blocks through the game's own {@code destroyBlock} ({@code destroyBlock} of its
 * {@code gameMode}, {@code gameMode}). A {@code BEFORE} listener refuses the lower one, which must stay, with
 * {@code CANCELED} fired for it; the upper one must go, with {@code AFTER} fired for it as stone. Forge 47's
 * {@code destroyBlock} doesn't make the calls upstream's hooks fire these at, so the in-game mod fires them (BREAK-1).
 */
public final class ForgeBlockBreakTest {
	private static final Set<String> SEEN = ConcurrentHashMap.newKeySet();
	/** The lower block, which {@code BEFORE} refuses, and the upper one, right above. Set by {@link #check}. */
	private static volatile BlockPos refused;
	private static volatile BlockPos broken;

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
			if (pos.equals(broken) && state.is(Blocks.STONE)) SEEN.add("after");
		});
	}

	/** At the first server tick, on the overworld. */
	public static void check(ServerLevel level) {
		if (!FabricLoader.getInstance().isModLoaded("fabric-events-interaction-v0")) return;
		BlockPos lower = ForgeTestMod.testSite(level, 12, 284, 4);
		BlockPos upper = ForgeTestMod.testSite(level, 12, 285, 4);
		refused = lower;
		broken = upper;
		List<String> failed = new ArrayList<>();
		level.setBlock(lower, Blocks.STONE.defaultBlockState(), 3);
		level.setBlock(upper, Blocks.STONE.defaultBlockState(), 3);
		FakePlayer player = FakePlayer.get(level);
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		if (player.gameMode.destroyBlock(lower)) failed.add("destroyBlock broke the block BEFORE refused");
		if (!level.getBlockState(lower).is(Blocks.STONE)) failed.add("the block BEFORE refused is gone");
		if (!player.gameMode.destroyBlock(upper)) failed.add("destroyBlock didn't break the other block");
		if (!level.getBlockState(upper).isAir()) failed.add("the other block is still there");
		for (String event : List.of("before", "canceled", "after")) {
			if (!SEEN.contains(event)) failed.add("no " + event.toUpperCase(Locale.ROOT) + " event");
		}
		level.setBlock(lower, Blocks.AIR.defaultBlockState(), 3);
		level.setBlock(upper, Blocks.AIR.defaultBlockState(), 3);
		if (failed.isEmpty()) {
			ForgeTestMod.LOGGER.info("FABRICATED-TEST block break events OK");
		} else {
			ForgeTestMod.LOGGER.warn("FABRICATED-TEST block break events: {}", String.join("; ", failed));
		}
	}
}
