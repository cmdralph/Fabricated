package io.github.cmdralph.fabricated.testmod;

import java.util.ArrayList;
import java.util.Arrays;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * CI test mod part for vanilla's eyes-in-water check in a player's mining speed, {@code isEyeInFluid(FluidTags.WATER)} in
 * {@code Player.getDestroySpeed}, which NeoForge 26.x asks of the fluid types at the eyes ({@code
 * getFluidInteraction().isEyeInFluidMatching}) and Fabricated puts back where the eyes are in vanilla's water, lava or no
 * fluid (SEAM-9); the other platforms keep vanilla's call. The test mod's {@code mixin.EyesInWaterMixin} hooks the call as
 * Artifacts' sinking ability does, taking the slower mining underwater away while the check asks it to, and counts it.
 * Once the server has started, a fake player mines stone bare-handed in the air, then with its eyes in water (each
 * asking the hook once, and slower in water), then in water with the hook answering no: as fast as in the air. With
 * Fabric API's fake player, so on Forge only with Fabric API. Where a native mod's mixin hooks the platform's code there,
 * Fabricated keeps the class as the platform has it, as its report says and the class shows (no helper of Fabricated's
 * in it): the check is then SKIPPED, naming the mixin.
 */
public final class VanillaEyesTest implements ModInitializer {
	private static final Logger LOGGER = LoggerFactory.getLogger("fabricated_testmod");
	private static final AtomicInteger ASKED = new AtomicInteger();
	/** The report's line where a native mod's mixin keeps the class as the platform has it (SeamConflicts). */
	private static final Pattern KEPT = Pattern.compile("Vanilla's calls not put back in Player:\\s*((?:NeoForge|Forge) mod [^\\n]*)");
	/** The helper Fabricated's rewrite adds to Player where it puts the call back (NeoForge 26.x). */
	private static final String HELPER = "fabricated$vanillaEyes";
	/** Whether the hook takes the slower mining underwater away: only while the check asks it to. */
	private static volatile boolean sinking;

	/** The hook of vanilla's eyes-in-water check in the mining speed: counts it, and answers no while sinking. */
	public static boolean eyesInWater(boolean inWater) {
		ASKED.incrementAndGet();
		return inWater && !sinking;
	}

	@Override
	public void onInitialize() {
		if (!FabricLoader.getInstance().isModLoaded("fabric-events-interaction-v0")) return;
		ServerLifecycleEvents.SERVER_STARTED.register(Mining::check);
	}

	/**
	 * Why Fabricated keeps Player as the platform has it, where it does for a native mod's mixin: the class has none of the
	 * helpers its rewrite adds, and the report names the mixin. Null otherwise, and where the platform keeps vanilla's call.
	 */
	private static String keptByANativeMod() {
		if (Arrays.stream(Player.class.getDeclaredMethods()).anyMatch(m -> m.getName().equals(HELPER))) return null;
		Matcher kept = KEPT.matcher(Fabricated.get().map(FabricatedApi::renderReport).orElse(""));
		return kept.find() ? kept.group(1).trim() : null;
	}

	/** The checks, apart: only this class names Fabric API's fake player. */
	private static final class Mining {
		static void check(MinecraftServer server) {
			String kept = keptByANativeMod();
			if (kept != null) {
				LOGGER.info("FABRICATED-TEST vanilla eyes check SKIPPED: Fabricated keeps Player as the platform has it: {}", kept);
				return;
			}
			ServerLevel level = server.overworld();
			BlockPos pos = new BlockPos(-96, 250, -88);
			FakePlayer player = FakePlayer.get(level);
			double x = player.getX(), y = player.getY(), z = player.getZ();
			BlockState stone = Blocks.STONE.defaultBlockState();
			try {
				List<String> failures = new ArrayList<>();
				float air = speed(player, pos, stone, "in the air", failures);
				// Water at the feet and the eyes: vanilla's check sees the eyes in it, through the hook.
				level.setBlock(pos, Blocks.WATER.defaultBlockState(), 2);
				level.setBlock(pos.above(), Blocks.WATER.defaultBlockState(), 2);
				float water = speed(player, pos, stone, "in water", failures);
				if (!(water < air)) failures.add("mining in water was " + water + ", not slower than in the air (" + air + ")");
				// The hook answering no, as Artifacts' sinking ability does: the speed in the air.
				sinking = true;
				float sunk = speed(player, pos, stone, "in water, the hook answering no", failures);
				if (sunk != air) failures.add("mining in water with the hook answering no was " + sunk + ", not the speed in the air (" + air + ")");
				if (failures.isEmpty()) {
					LOGGER.info("FABRICATED-TEST vanilla eyes check OK");
				} else {
					LOGGER.error("FABRICATED-TEST vanilla eyes check FAILED: {}", String.join("; ", failures));
				}
			} catch (RuntimeException | LinkageError e) {
				LOGGER.error("FABRICATED-TEST vanilla eyes check FAILED", e);
			} finally {
				sinking = false;
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
				level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 2);
				player.setPos(x, y, z);
				player.baseTick();
			}
		}

		/**
		 * The player's speed mining the state, standing at the position after a tick of its own there, which reads the fluids
		 * at its eyes: vanilla's check must be asked once.
		 */
		private static float speed(FakePlayer player, BlockPos pos, BlockState state, String where, List<String> failures) {
			player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
			player.baseTick();
			int before = ASKED.get();
			float speed = player.getDestroySpeed(state);
			if (ASKED.get() - before != 1) failures.add("vanilla's eyes-in-water check ran " + (ASKED.get() - before) + " times " + where + ", not once");
			return speed;
		}
	}
}
