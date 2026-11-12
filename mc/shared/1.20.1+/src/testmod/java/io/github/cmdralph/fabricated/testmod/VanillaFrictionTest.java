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
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * CI test mod part for vanilla's friction, which NeoForge and Forge ask through the block state's
 * {@code getFriction(level, pos, entity)}, whose default asks the block's {@code getFriction()}, and Fabricated puts back
 * for the blocks keeping that default (SEAM-8): the test mod's {@code mixin.FrictionMixin} wraps the call in an item's
 * movement, as FrozenLib's friction API and Artifacts' snowshoes wrap a living entity's, making packed ice rough while the
 * check asks it to. Once the server has started, an item slides one tick over stone, then packed ice: each must slow by the
 * friction the hook answers (stone's vanilla one, packed ice's rough one), each asking the hook once; over the platform's
 * block overriding the extension ({@code fabricated_testmod:break_override}), by that block's own friction, without the
 * hook. The item's movement multiplies the ground's friction by vanilla's drag, and Forge 65's and 66's by it once more
 * (their patch keeps the factor 26.2 moved into {@code getAirDrag}): stone tells which. Where a native mod's mixin hooks the platform's code there, Fabricated keeps the class as the platform has it, as
 * its report says and the class shows (no helper of Fabricated's in it): the check is then SKIPPED, naming the mixin.
 */
public final class VanillaFrictionTest implements ModInitializer {
	private static final Logger LOGGER = LoggerFactory.getLogger("fabricated_testmod");
	private static final AtomicInteger ASKED = new AtomicInteger();
	/** The report's line where a native mod's mixin keeps the class as the platform has it (SeamConflicts). */
	private static final Pattern KEPT = Pattern.compile("Vanilla's calls not put back in ItemEntity:\\s*((?:NeoForge|Forge) mod [^\\n]*)");
	/** The friction the hook answers for packed ice while the check asks it to (vanilla's is 0.98). */
	private static final float ROUGH = 0.5F;
	/** The item's speed as it starts its tick. */
	private static final double SPEED = 0.3;
	/** The friction of the platform's block overriding the extension ({@code BreakOverride}). */
	private static final float OVERRIDING = 0.9F;
	/** Stone's friction, vanilla's, which the hook passes on. */
	private static final float STONE = 0.6F;
	/** What the platform multiplies vanilla's slowing by: nothing, or Forge 65's and 66's drag once more. */
	private static final float[] EXTRA = {1.0F, 0.98F};
	/** Whether the hook makes packed ice rough: only while the check asks it to. */
	private static volatile boolean answering;

	/** The hook of vanilla's friction in an item's movement: counts it, and makes packed ice rough while asked to. */
	public static float friction(Block block, float vanilla) {
		ASKED.incrementAndGet();
		return answering && block == Blocks.PACKED_ICE ? ROUGH : vanilla;
	}

	/** What the block overriding the platform's friction extension answers ({@code BreakOverride}). */
	public static float overridingFriction() {
		return OVERRIDING;
	}

	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(VanillaFrictionTest::check);
	}

	/**
	 * Why Fabricated keeps ItemEntity as the platform has it, where it does for a native mod's mixin: the class has none of
	 * the helpers its rewrite adds, and the report names the mixin. Null otherwise.
	 */
	private static String keptByANativeMod() {
		try {
			ItemEntity.class.getDeclaredMethod("fabricated$keepsFrictionDefault", BlockState.class);
			return null;
		} catch (NoSuchMethodException e) {
			Matcher kept = KEPT.matcher(Fabricated.get().map(FabricatedApi::renderReport).orElse(""));
			return kept.find() ? kept.group(1).trim() : null;
		}
	}

	private static void check(MinecraftServer server) {
		String kept = keptByANativeMod();
		if (kept != null) {
			LOGGER.info("FABRICATED-TEST vanilla friction check SKIPPED: Fabricated keeps ItemEntity as the platform has it: {}", kept);
			return;
		}
		ServerLevel level = server.overworld();
		BlockPos base = new BlockPos(-96, 250, -48);
		try {
			List<String> failures = new ArrayList<>();
			answering = true;
			// Stone: the hook passes vanilla's friction on, and the item's slowing tells the platform's drag.
			BlockState stone = Blocks.STONE.defaultBlockState();
			double speed = slide(level, base, stone, 1, failures);
			float extra = 0;
			for (float candidate : EXTRA) if (Math.abs(speed - expected(STONE, candidate)) <= 1.0E-6) extra = candidate;
			if (extra == 0) failures.add(stone.getBlock() + " slowed the item to " + speed + ", not " + expected(STONE, 1.0F) + " (friction " + STONE + ")");
			Block override = VanillaBreakTest.override();
			if (extra != 0) {
				// Packed ice, which keeps the platform's default: the hook's friction slows the item.
				slides(level, base, Blocks.PACKED_ICE.defaultBlockState(), ROUGH, extra, 1, failures);
				// The platform's block overriding the extension: its own friction, without the hook.
				if (override != null) slides(level, base, override.defaultBlockState(), OVERRIDING, extra, 0, failures);
			}
			if (failures.isEmpty()) {
				LOGGER.info("FABRICATED-TEST vanilla friction check OK" + (override != null ? " (with a block overriding the platform's extension)" : ""));
			} else {
				LOGGER.error("FABRICATED-TEST vanilla friction check FAILED: {}", String.join("; ", failures));
			}
		} catch (RuntimeException | LinkageError e) {
			LOGGER.error("FABRICATED-TEST vanilla friction check FAILED", e);
		} finally {
			answering = false;
			for (int x = 0; x < 3; x++) level.setBlock(base.east(x), Blocks.AIR.defaultBlockState(), 2);
		}
	}

	/** The item's speed after a tick over a block of the friction: vanilla's drag applied, and the platform's extra. */
	private static double expected(float friction, float extra) {
		return SPEED * (double) (extra * (friction * 0.98F));
	}

	/** An item sliding one tick east over a row of the state: it must slow by the friction expected. */
	private static void slides(ServerLevel level, BlockPos base, BlockState state, float friction, float extra, int hooks, List<String> failures) {
		double speed = slide(level, base, state, hooks, failures), expected = expected(friction, extra);
		if (Math.abs(speed - expected) > 1.0E-6) failures.add(state.getBlock() + " slowed the item to " + speed + ", not " + expected + " (friction " + friction + ")");
	}

	/** The item's speed after sliding one tick east over a row of the state, which must ask the hook as many times as given. */
	private static double slide(ServerLevel level, BlockPos base, BlockState state, int hooks, List<String> failures) {
		for (int x = 0; x < 3; x++) level.setBlock(base.east(x), state, 2);
		ItemEntity item = new ItemEntity(level, base.getX() + 0.5, base.getY() + 1.0, base.getZ() + 0.5, new ItemStack(Items.STICK));
		item.setDeltaMovement(SPEED, 0.0, 0.0);
		int asked = ASKED.get();
		item.tick();
		double speed = item.getDeltaMovement().x();
		if (ASKED.get() != asked + hooks) failures.add("the hook of vanilla's friction ran " + (ASKED.get() - asked) + " times over " + state.getBlock() + ", not " + hooks);
		item.discard();
		return speed;
	}
}
