package io.github.cmdralph.fabricated.testmod;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.cmdralph.fabricated.api.Fabricated;
import io.github.cmdralph.fabricated.api.FabricatedApi;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * CI test mod part for vanilla's harvest check, {@code hasCorrectToolForDrops}, which NeoForge and Forge make through the
 * block state's {@code canHarvestBlock} in the server's {@code destroyBlock} and through a hook of theirs in
 * {@code BlockBehaviour.getDestroyProgress}, and Fabricated puts back where the platform's code makes it (SEAM-4): the
 * test mod's {@code mixin.HarvestCheckMixin} wraps the first, letting a bare hand harvest stone, and
 * {@code mixin.DestroyProgressMixin} the second, letting it mine stone as fast as the right tool while asked to. Once the
 * server has started, a fake player with a bare hand breaks stone, which must drop its cobblestone through the hook;
 * deepslate, which the hook passes on and which must drop nothing; andesite, which the hook passes on and the platform's
 * harvest check event lets the player harvest (a listener of the platform's half), so it must drop: the event still fires
 * where vanilla's call is made, and decides; and, where the platform's half provides one, the block overriding the
 * platform's extension ({@code fabricated_testmod:break_override}), whose own check must answer without the hook. Then
 * stone's destroy progress must be the right tool's with the hook's yes (30, not 100, apart). With Fabric API's fake
 * player, so on Forge only with Fabric API. Where a native mod's mixin hooks the platform's code there, Fabricated keeps
 * the class as the platform has it, as its report says and the class shows (no helper of Fabricated's in it): the check
 * is then SKIPPED, naming the mixin.
 */
public final class VanillaHarvestTest implements ModInitializer {
	private static final Logger LOGGER = LoggerFactory.getLogger("fabricated_testmod");
	private static final AtomicInteger HARVESTED = new AtomicInteger();
	private static final AtomicInteger MINED = new AtomicInteger();
	private static final AtomicInteger OVERRIDE_ASKED = new AtomicInteger();
	/** The report's line where a native mod's mixin keeps a class as the platform has it (SeamConflicts). */
	private static final Pattern KEPT = Pattern.compile("Vanilla's calls not put back in (ServerPlayerGameMode|BlockBehaviour):\\s*((?:NeoForge|Forge) mod [^\\n]*)");
	/** Whether the mining hook lets a bare hand mine stone as fast as the right tool: only while the check asks it to. */
	private static volatile boolean answering;
	/** Whether the platform's harvest check event lets the player harvest andesite: only while the check asks it to. */
	private static volatile boolean granting;

	/** The harvest hook of destroyBlock: counts it, and lets a bare hand harvest stone. */
	public static boolean harvesting(BlockState state, boolean vanilla) {
		HARVESTED.incrementAndGet();
		return vanilla || state.is(Blocks.STONE);
	}

	/** The harvest hook of getDestroyProgress: counts it, and lets a bare hand mine stone as the right tool does while asked to. */
	public static boolean mining(BlockState state, boolean vanilla) {
		MINED.incrementAndGet();
		return vanilla || answering && state.is(Blocks.STONE);
	}

	/** What the block overriding the platform's harvest extension answers ({@code BreakOverride}): counted, no. */
	public static boolean overridingAnswer() {
		OVERRIDE_ASKED.incrementAndGet();
		return false;
	}

	@Override
	public void onInitialize() {
		if (!FabricLoader.getInstance().isModLoaded("fabric-events-interaction-v0")) return;
		TestPlatform.grantHarvest(state -> granting && state.is(Blocks.ANDESITE));
		ServerLifecycleEvents.SERVER_STARTED.register(Harvesting::check);
	}

	/**
	 * Why Fabricated keeps ServerPlayerGameMode or BlockBehaviour as the platform has it, where it does for a native mod's
	 * mixin: the class has none of the helpers its rewrite adds, and the report names the mixin. Null otherwise.
	 */
	private static String keptByANativeMod() {
		boolean server = declares(ServerPlayerGameMode.class, "fabricated$keepsHarvestDefault", BlockState.class);
		// NeoForge's getDestroyProgress posts the event through a helper of Fabricated's, Forge's asks the player's class first.
		boolean progress = declares(BlockBehaviour.class, "fabricated$harvestCheck", boolean.class, Player.class, BlockState.class, BlockGetter.class, BlockPos.class)
				|| declares(BlockBehaviour.class, "fabricated$keepsPlayerHarvest", Player.class);
		if (server && progress) return null;
		Matcher kept = KEPT.matcher(Fabricated.get().map(FabricatedApi::renderReport).orElse(""));
		while (kept.find()) {
			if (kept.group(1).equals("ServerPlayerGameMode") ? !server : !progress) return kept.group(1) + ": " + kept.group(2).trim();
		}
		return null;
	}

	private static boolean declares(Class<?> owner, String name, Class<?>... params) {
		try {
			owner.getDeclaredMethod(name, params);
			return true;
		} catch (NoSuchMethodException e) {
			return false;
		}
	}

	/** The checks, apart: only this class names Fabric API's fake player. */
	private static final class Harvesting {
		static void check(MinecraftServer server) {
			String kept = keptByANativeMod();
			if (kept != null) {
				LOGGER.info("FABRICATED-TEST vanilla harvest check SKIPPED: Fabricated keeps {} as the platform has it", kept);
				return;
			}
			ServerLevel level = server.overworld();
			BlockPos stone = new BlockPos(-96, 250, -76), deepslate = stone.east(2), andesite = stone.east(4), overriding = stone.east(6);
			List<BlockPos> placed = List.of(stone, deepslate, andesite, overriding);
			try {
				List<String> failures = new ArrayList<>();
				FakePlayer player = FakePlayer.get(level);
				player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
				// Stone with a bare hand: vanilla's check refuses it, the hook lets it be harvested.
				level.setBlock(stone, Blocks.STONE.defaultBlockState(), 2);
				int harvested = HARVESTED.get();
				if (!player.gameMode.destroyBlock(stone) || !level.getBlockState(stone).isAir()) failures.add("stone broke to " + level.getBlockState(stone));
				if (HARVESTED.get() != harvested + 1) failures.add("the hook of vanilla's harvest check ran " + (HARVESTED.get() - harvested) + " times for stone's break");
				if (!dropped(level, stone, Items.COBBLESTONE)) failures.add("stone broken with a bare hand dropped no cobblestone, though the hook let it be harvested");
				// Deepslate: the hook passes vanilla's refusal on.
				level.setBlock(deepslate, Blocks.DEEPSLATE.defaultBlockState(), 2);
				harvested = HARVESTED.get();
				player.gameMode.destroyBlock(deepslate);
				if (HARVESTED.get() != harvested + 1) failures.add("the hook of vanilla's harvest check ran " + (HARVESTED.get() - harvested) + " times for deepslate's break");
				if (dropped(level, deepslate, Items.COBBLED_DEEPSLATE)) failures.add("deepslate broken with a bare hand dropped its cobbled deepslate");
				// Andesite: the hook passes vanilla's refusal on, and the platform's harvest check event lets it be harvested.
				level.setBlock(andesite, Blocks.ANDESITE.defaultBlockState(), 2);
				harvested = HARVESTED.get();
				granting = true;
				player.gameMode.destroyBlock(andesite);
				granting = false;
				if (HARVESTED.get() != harvested + 1) failures.add("the hook of vanilla's harvest check ran " + (HARVESTED.get() - harvested) + " times for andesite's break");
				if (!dropped(level, andesite, Items.ANDESITE)) failures.add("andesite, which the platform's harvest check event lets the player harvest, dropped nothing");
				Block override = VanillaBreakTest.override();
				if (override != null) {
					// The platform's block overriding canHarvestBlock: its own check answers, without the hook.
					level.setBlock(overriding, override.defaultBlockState(), 2);
					harvested = HARVESTED.get();
					int asked = OVERRIDE_ASKED.get();
					player.gameMode.destroyBlock(overriding);
					if (HARVESTED.get() != harvested) failures.add("the hook of vanilla's harvest check ran for the block overriding the platform's extension");
					if (OVERRIDE_ASKED.get() != asked + 1) failures.add("the block overriding the platform's extension was asked " + (OVERRIDE_ASKED.get() - asked) + " times, not once");
				}
				// Stone's destroy progress with a bare hand: the right tool's (30) with the hook's yes, a bare hand's (100) without.
				BlockState state = Blocks.STONE.defaultBlockState();
				level.setBlock(stone, state, 2);
				int mined = MINED.get();
				float slow = state.getDestroyProgress(player, level, stone);
				answering = true;
				float fast = state.getDestroyProgress(player, level, stone);
				answering = false;
				if (MINED.get() != mined + 2) failures.add("the hook of vanilla's harvest check in getDestroyProgress ran " + (MINED.get() - mined) + " times for 2 calls");
				if (slow <= 0 || Math.abs(fast / slow - 100f / 30f) > 0.01f) {
					failures.add("stone's destroy progress with a bare hand was " + fast + " with the hook's yes and " + slow + " without, not 100 to 30");
				}
				if (failures.isEmpty()) {
					LOGGER.info("FABRICATED-TEST vanilla harvest check OK{}", override != null ? " (with a block overriding the platform's extension)" : "");
				} else {
					LOGGER.error("FABRICATED-TEST vanilla harvest check FAILED: {}", String.join("; ", failures));
				}
			} catch (RuntimeException | LinkageError e) {
				LOGGER.error("FABRICATED-TEST vanilla harvest check FAILED", e);
			} finally {
				granting = false;
				answering = false;
				for (BlockPos pos : placed) {
					level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
					dropped(level, pos, Items.AIR);
				}
			}
		}

		/** Whether an item of the kind lies at the position; every item there is taken away. */
		private static boolean dropped(ServerLevel level, BlockPos pos, Item item) {
			boolean found = false;
			for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1.5))) {
				found |= entity.getItem().is(item);
				entity.discard();
			}
			return found;
		}
	}
}
