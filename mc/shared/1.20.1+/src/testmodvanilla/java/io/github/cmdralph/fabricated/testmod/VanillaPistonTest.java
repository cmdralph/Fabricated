package io.github.cmdralph.fabricated.testmod;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;

/**
 * CI test mod part for vanilla's piston stickiness methods, {@code PistonStructureResolver.isSticky} and
 * {@code canStickToEachOther}, which the platforms replaced with the block state's extensions and Fabricated puts back
 * (SEAM-1): the test mod's mixin ({@code vanilla.PistonStickinessMixin}) makes smooth basalt sticky and says it doesn't
 * stick to calcite. Once the server has started, a piston pushing smooth basalt must move the stone on it and not the
 * calcite beside it, through the mixin's hooks; and with them, a piston pushing slime must still move the stone on it and
 * not the honey beside it, which the platform says doesn't stick to slime.
 */
public final class VanillaPistonTest implements ModInitializer {
	private static final Logger LOGGER = LoggerFactory.getLogger("fabricated_testmod");
	private static final AtomicInteger ASKED = new AtomicInteger();

	/** The mixin's {@code isSticky} hook: smooth basalt is sticky. */
	public static boolean sticky(BlockState state) {
		return state.is(Blocks.SMOOTH_BASALT);
	}

	/** The mixin's {@code canStickToEachOther} hooks: smooth basalt sticks to all but calcite; null for the platform's answer. */
	public static Boolean stick(BlockState state, BlockState other) {
		if (!sticky(state) && !sticky(other)) return null;
		return !state.is(Blocks.CALCITE) && !other.is(Blocks.CALCITE);
	}

	/** The mixin's hook of the resolver's calls of {@code isSticky}: counts them. */
	public static void askedSticky() {
		ASKED.incrementAndGet();
	}

	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			ServerLevel level = server.overworld();
			List<BlockPos> placed = new ArrayList<>();
			try {
				int asked = ASKED.get();
				List<String> failures = new ArrayList<>();
				push(level, new BlockPos(-96, 250, -96), Blocks.SMOOTH_BASALT, Blocks.CALCITE, placed, failures);
				push(level, new BlockPos(-96, 250, -90), Blocks.SLIME_BLOCK, Blocks.HONEY_BLOCK, placed, failures);
				if (ASKED.get() == asked) failures.add("the hook of the resolver's isSticky calls didn't run");
				if (failures.isEmpty()) {
					LOGGER.info("FABRICATED-TEST vanilla piston stickiness OK");
				} else {
					LOGGER.error("FABRICATED-TEST vanilla piston stickiness FAILED: {}", String.join("; ", failures));
				}
			} catch (RuntimeException | LinkageError e) {
				LOGGER.error("FABRICATED-TEST vanilla piston stickiness FAILED", e);
			} finally {
				for (BlockPos pos : placed) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
			}
		});
	}

	/**
	 * A piston at {@code at} facing east, {@code sticky} in front of it, stone on that and {@code refused} beside it: the
	 * push must move {@code sticky} and the stone, and not {@code refused}.
	 */
	private static void push(ServerLevel level, BlockPos at, Block sticky, Block refused, List<BlockPos> placed, List<String> failures) {
		BlockPos front = at.east();
		place(level, at, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST), placed);
		place(level, front, sticky.defaultBlockState(), placed);
		place(level, front.above(), Blocks.STONE.defaultBlockState(), placed);
		place(level, front.north(), refused.defaultBlockState(), placed);
		PistonStructureResolver resolver = new PistonStructureResolver(level, at, Direction.EAST, true);
		List<BlockPos> moved = resolver.resolve() ? resolver.getToPush() : List.of();
		String name = sticky.getName().getString();
		if (!moved.contains(front) || !moved.contains(front.above())) failures.add(name + " didn't pull the stone on it: " + moved);
		if (moved.contains(front.north())) failures.add(name + " pulled " + refused.getName().getString() + ": " + moved);
	}

	private static void place(ServerLevel level, BlockPos pos, BlockState state, List<BlockPos> placed) {
		level.setBlock(pos, state, 2);
		placed.add(pos);
	}
}
