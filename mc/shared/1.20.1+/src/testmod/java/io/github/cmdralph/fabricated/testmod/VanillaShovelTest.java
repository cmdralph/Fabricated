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
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * CI test mod part for the shovel's path and campfire dousing, which NeoForge and Forge make through the block state's
 * {@code getToolModifiedState} of their actions, and Forge's air check above the path, which it makes through the
 * level's {@code isEmptyBlock}, all of which Fabricated puts back for the blocks and levels keeping the platform's
 * defaults (SEAM-13): the test mod's {@code tools.ShovelBehaviourMixin} hooks {@code ShovelItem.useOn} as FrozenLib's
 * shovel behaviours (Wilder Wild's) hook it, giving calcite a behaviour of its own (it becomes tuff), and counts vanilla's
 * path lookups and campfire checks. Once the server has started, a fake player's wooden shovel is used on calcite, grass,
 * a lit campfire and the platform's block overriding the extension ({@code fabricated_testmod:break_override}): calcite
 * must become tuff, neither flattened nor doused, grass a path, the campfire unlit, each looked up once, and the
 * platform's block must answer as it does itself, without vanilla's lookup. With Fabric API's fake player, so on Forge
 * only with Fabric API. 26.3's shovels flatten through their item's data, with no {@code ShovelItem}: nothing is checked
 * there. Where a native mod's mixin hooks the platform's code there, Fabricated keeps the class as the platform has it, as
 * its report says and the class shows (no helper of Fabricated's in it): the check is then SKIPPED, naming the mixin.
 */
public final class VanillaShovelTest implements ModInitializer {
	private static final Logger LOGGER = LoggerFactory.getLogger("fabricated_testmod");
	private static final String SHOVEL = "net.minecraft.world.item.ShovelItem";
	private static final AtomicInteger LOOKUPS = new AtomicInteger(), CAMPFIRE_CHECKS = new AtomicInteger();
	/** The report's line where a native mod's mixin keeps the class as the platform has it (SeamConflicts). */
	private static final Pattern KEPT = Pattern.compile("Vanilla's calls not put back in ShovelItem:\\s*((?:NeoForge|Forge) mod [^\\n]*)");
	/** Whether the hooks give calcite its behaviour: only while the check asks them to. */
	private static volatile boolean answering;

	/** Whether the shovel's use on the state runs the test mod's own behaviour, as FrozenLib's registered ones do. */
	public static boolean behaviour(BlockState state) {
		return answering && state.is(Blocks.CALCITE);
	}

	/** The state the behaviour leaves. */
	public static BlockState behaviourState() {
		return Blocks.TUFF.defaultBlockState();
	}

	/** The hook of vanilla's lookup of the path: counts it. */
	public static void lookedUp() {
		LOOKUPS.incrementAndGet();
	}

	/** The hook of vanilla's check for a campfire: counts it. */
	public static void campfireChecked() {
		CAMPFIRE_CHECKS.incrementAndGet();
	}

	@Override
	public void onInitialize() {
		if (!FabricLoader.getInstance().isModLoaded("fabric-events-interaction-v0") || shovel() == null) return;
		ServerLifecycleEvents.SERVER_STARTED.register(Shoveling::check);
	}

	/** The game's ShovelItem, or null where it has none (26.3). */
	private static Class<?> shovel() {
		try {
			return Class.forName(SHOVEL, false, VanillaShovelTest.class.getClassLoader());
		} catch (ClassNotFoundException e) {
			return null;
		}
	}

	/**
	 * Why Fabricated keeps ShovelItem as the platform has it, where it does for a native mod's mixin: the class has none of
	 * the helpers its rewrite adds, and the report names the mixin. Null otherwise.
	 */
	private static String keptByANativeMod() {
		try {
			shovel().getDeclaredMethod("fabricated$keepsToolDefault", BlockState.class);
			return null;
		} catch (NoSuchMethodException e) {
			Matcher kept = KEPT.matcher(Fabricated.get().map(FabricatedApi::renderReport).orElse(""));
			return kept.find() ? kept.group(1).trim() : null;
		}
	}

	/** The checks, apart: only this class names Fabric API's fake player. */
	private static final class Shoveling {
		static void check(MinecraftServer server) {
			String kept = keptByANativeMod();
			if (kept != null) {
				LOGGER.info("FABRICATED-TEST vanilla shovel check SKIPPED: Fabricated keeps ShovelItem as the platform has it: {}", kept);
				return;
			}
			ServerLevel level = server.overworld();
			BlockPos pos = new BlockPos(-96, 250, -80);
			FakePlayer player = FakePlayer.get(level);
			try {
				List<String> failures = new ArrayList<>();
				answering = true;
				// Calcite, which the behaviour takes: not flattened, not doused, but tuff, vanilla's lookup made once.
				use(level, pos, player, Blocks.CALCITE.defaultBlockState(), Blocks.TUFF.defaultBlockState(), 1, 1, failures);
				// Grass: vanilla's path, through vanilla's lookup, which the hook sees.
				use(level, pos, player, Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.DIRT_PATH.defaultBlockState(), 1, 0, failures);
				// A lit campfire: vanilla's dousing, through vanilla's check, which the hook sees.
				BlockState lit = Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true);
				use(level, pos, player, lit, lit.setValue(CampfireBlock.LIT, false), 1, 1, failures);
				// The platform's block overriding the extension: its own answer, without vanilla's lookup or check.
				Block override = VanillaBreakTest.override();
				if (override != null) use(level, pos, player, override.defaultBlockState(), Blocks.AMETHYST_BLOCK.defaultBlockState(), 0, 0, failures);
				if (failures.isEmpty()) {
					LOGGER.info("FABRICATED-TEST vanilla shovel check OK" + (override != null ? " (with a block overriding the platform's extension)" : ""));
				} else {
					LOGGER.error("FABRICATED-TEST vanilla shovel check FAILED: {}", String.join("; ", failures));
				}
			} catch (RuntimeException | LinkageError e) {
				LOGGER.error("FABRICATED-TEST vanilla shovel check FAILED", e);
			} finally {
				answering = false;
				player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
			}
		}

		/**
		 * A wooden shovel used on the top of the state, air above it: the block must become {@code expected}, vanilla's lookup
		 * and campfire check hooked as often as given.
		 */
		private static void use(ServerLevel level, BlockPos pos, FakePlayer player, BlockState state, BlockState expected, int lookups, int checks,
				List<String> failures) {
			level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 2);
			level.setBlock(pos, state, 2);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WOODEN_SHOVEL));
			int lookedUp = LOOKUPS.get(), checked = CAMPFIRE_CHECKS.get();
			Items.WOODEN_SHOVEL.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)));
			BlockState after = level.getBlockState(pos);
			if (after != expected) failures.add("a shovel left " + state + " as " + after + ", not " + expected);
			if (LOOKUPS.get() - lookedUp != lookups) failures.add("vanilla's path lookup ran " + (LOOKUPS.get() - lookedUp) + " times for " + state + ", not " + lookups);
			if (CAMPFIRE_CHECKS.get() - checked != checks) {
				failures.add("vanilla's campfire check ran " + (CAMPFIRE_CHECKS.get() - checked) + " times for " + state + ", not " + checks);
			}
		}
	}
}
