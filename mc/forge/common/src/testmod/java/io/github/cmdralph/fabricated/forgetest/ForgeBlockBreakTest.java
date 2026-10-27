package io.github.cmdralph.fabricated.forgetest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * The forge test mod's part for fabric-events-interaction-v0's block break events (with Fabric API's jar only): a Fabric
 * fake player breaks two stone blocks through the game's own {@code destroyBlock}. A {@code BEFORE} listener refuses the
 * lower one, which must stay, with {@code CANCELED} fired for it; the upper one must go, with {@code AFTER} fired for it
 * as stone. Forge's {@code destroyBlock} doesn't make the calls upstream's hooks fire these at, so the in-game mod fires
 * them (BREAK-1).
 */
public final class ForgeBlockBreakTest {
	private static final Set<String> SEEN = ConcurrentHashMap.newKeySet();
	/** The lower block, which {@code BEFORE} refuses; the upper one is right above. Set by {@link #check}. */
	private static volatile BlockPos refused;

	private ForgeBlockBreakTest() {
	}

	/** From the main entrypoint, as Fabric mods register their listeners; only with Fabric API's interaction module. */
	public static void register() {
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (!pos.equals(refused)) return true;
			SEEN.add("before");
			return false;
		});
		PlayerBlockBreakEvents.CANCELED.register((level, player, pos, state, blockEntity) -> {
			if (pos.equals(refused)) SEEN.add("canceled");
		});
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (refused != null && pos.equals(refused.above()) && state.is(Blocks.STONE)) SEEN.add("after");
		});
	}

	/** At the first server tick, on the overworld. */
	public static void check(ServerLevel level) {
		BlockPos lower = new BlockPos(28, level.getMaxY() - 30, 20);
		BlockPos upper = lower.above();
		refused = lower;
		List<String> failed = new ArrayList<>();
		level.setBlockAndUpdate(lower, Blocks.STONE.defaultBlockState());
		level.setBlockAndUpdate(upper, Blocks.STONE.defaultBlockState());
		FakePlayer player = FakePlayer.get(level);
		player.setPos(lower.getX() + 2.5, lower.getY(), lower.getZ() + 0.5);
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		if (player.gameMode.destroyBlock(lower)) failed.add("destroyBlock broke the block BEFORE refused");
		if (!level.getBlockState(lower).is(Blocks.STONE)) failed.add("the block BEFORE refused is gone");
		if (!player.gameMode.destroyBlock(upper)) failed.add("destroyBlock didn't break the other block");
		if (!level.getBlockState(upper).isAir()) failed.add("the other block is still there");
		for (String event : List.of("before", "canceled", "after")) {
			if (!SEEN.contains(event)) failed.add("no " + event.toUpperCase(Locale.ROOT) + " event");
		}
		level.setBlockAndUpdate(lower, Blocks.AIR.defaultBlockState());
		level.setBlockAndUpdate(upper, Blocks.AIR.defaultBlockState());
		if (failed.isEmpty()) {
			ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST block break events OK");
		} else {
			ForgeTestMod.LOGGER.warn("FABRICATED-FORGE-TEST block break events: {}", String.join("; ", failed));
		}
	}
}
