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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * CI test mod part for a crop's growth speed, where vanilla checks each soil block below the crop for farmland and its
 * moisture and NeoForge and Forge ask the soil's {@code canSustainPlant} and {@code isFertile}, which Fabricated puts back
 * where vanilla's checks answer the platform's (SEAM-12): the test mod's {@code growth.SoilCheckMixin} hooks vanilla's
 * farmland check (up to 1.21.11, as Balm's custom farmland does) or its moisture check (26.x, as Balm's does there), counting
 * each, and {@code growth.GrowthSpeedMixin} takes the speed with vanilla's parameters, the crop's block among them, which
 * NeoForge's method, taking the crop's state, works out of it (pre-flight's bridge gives it). Once the server has started,
 * wheat is given a random tick on nine blocks of moist farmland: the hook must see the nine checks, the speed be vanilla's
 * (1 + 3 + 8 * 3 / 4) and the block wheat; then with the platform's block overriding both soil checks in the middle
 * ({@code fabricated_testmod:break_override}, moist by its own answer), which is asked as the platform has it, without the
 * hook: eight checks, the same speed. Where a native mod's mixin hooks the platform's code there, Fabricated keeps the
 * class as the platform has it, as its report says and the class shows (no helper of Fabricated's in it): the check is then
 * SKIPPED, naming the mixin.
 */
public final class VanillaGrowthTest implements ModInitializer {
	private static final Logger LOGGER = LoggerFactory.getLogger("fabricated_testmod");
	private static final AtomicInteger CHECKS = new AtomicInteger();
	/** The report's line where a native mod's mixin keeps the class as the platform has it (SeamConflicts). */
	private static final Pattern KEPT = Pattern.compile("Vanilla's calls not put back in CropBlock:\\s*((?:NeoForge|Forge) mod [^\\n]*)");
	/** Vanilla's speed of a crop on nine moist farmland blocks, none of its kind beside it. */
	private static final float MOIST = 1.0F + 3.0F + 8 * 3.0F / 4.0F;
	private static volatile Block crop;
	private static volatile float speed = Float.NaN;

	/** The hook of vanilla's soil check: counts it. */
	public static void soilChecked() {
		CHECKS.incrementAndGet();
	}

	/** The growth speed, with the crop's block vanilla's method takes. */
	public static void grown(Block block, float growthSpeed) {
		crop = block;
		speed = growthSpeed;
	}

	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(VanillaGrowthTest::check);
	}

	/**
	 * Why Fabricated keeps CropBlock as the platform has it, where it does for a native mod's mixin: the class has none of
	 * the helpers its rewrite adds, and the report names the mixin. Null otherwise.
	 */
	private static String keptByANativeMod() {
		try {
			CropBlock.class.getDeclaredMethod("fabricated$vanillaFarmland", BlockState.class);
			return null;
		} catch (NoSuchMethodException e) {
			Matcher kept = KEPT.matcher(Fabricated.get().map(FabricatedApi::renderReport).orElse(""));
			return kept.find() ? kept.group(1).trim() : null;
		}
	}

	private static void check(MinecraftServer server) {
		String kept = keptByANativeMod();
		if (kept != null) {
			LOGGER.info("FABRICATED-TEST vanilla growth check SKIPPED: Fabricated keeps CropBlock as the platform has it: {}", kept);
			return;
		}
		ServerLevel level = server.overworld();
		BlockPos pos = new BlockPos(-96, 250, -84);
		BlockState farmland = Blocks.FARMLAND.defaultBlockState().setValue(BlockStateProperties.MOISTURE, 7);
		try {
			List<String> failures = new ArrayList<>();
			for (int x = -1; x <= 1; x++) {
				for (int z = -1; z <= 1; z++) level.setBlock(pos.offset(x, -1, z), farmland, 2);
			}
			// Vanilla's farmland: each of the nine soil checks goes back, which the hook sees.
			grow(level, pos, 9, failures);
			// The platform's block overriding both checks in the middle: asked as the platform has it, its answers moist farmland's.
			Block override = VanillaBreakTest.override();
			if (override != null) {
				level.setBlock(pos.below(), override.defaultBlockState(), 2);
				grow(level, pos, 8, failures);
			}
			if (failures.isEmpty()) {
				LOGGER.info("FABRICATED-TEST vanilla growth check OK" + (override != null ? " (with a block overriding the platform's extension)" : ""));
			} else {
				LOGGER.error("FABRICATED-TEST vanilla growth check FAILED: {}", String.join("; ", failures));
			}
		} catch (RuntimeException | LinkageError e) {
			LOGGER.error("FABRICATED-TEST vanilla growth check FAILED", e);
		} finally {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
			for (int x = -1; x <= 1; x++) {
				for (int z = -1; z <= 1; z++) level.setBlock(pos.offset(x, -1, z), Blocks.AIR.defaultBlockState(), 2);
			}
		}
	}

	/** Wheat at the position, given a random tick: the hook must see the soil checks given, the speed vanilla's and the crop wheat. */
	private static void grow(ServerLevel level, BlockPos pos, int checks, List<String> failures) {
		level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 2);
		level.setBlock(pos, Blocks.WHEAT.defaultBlockState(), 2);
		crop = null;
		speed = Float.NaN;
		int before = CHECKS.get();
		level.getBlockState(pos).randomTick(level, pos, level.getRandom());
		String soil = level.getBlockState(pos.below()).getBlock().toString();
		if (CHECKS.get() - before != checks) failures.add("vanilla's soil check ran " + (CHECKS.get() - before) + " times on " + soil + ", not " + checks);
		if (crop != Blocks.WHEAT) failures.add("the growth speed's hook was given " + crop + ", not wheat, on " + soil);
		if (speed != MOIST) failures.add("the growth speed on " + soil + " was " + speed + ", not " + MOIST);
	}
}
