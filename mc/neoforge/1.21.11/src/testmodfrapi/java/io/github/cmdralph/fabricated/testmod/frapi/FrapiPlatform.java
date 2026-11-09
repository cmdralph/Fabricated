package io.github.cmdralph.fabricated.testmod.frapi;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;

/**
 * What the renderer test mod's shared checks (mc/shared) ask of the platform they run on, NeoForge here (1.21.11). Every
 * platform's renderer test mod for 1.21.11 has this class with these methods, so the checks compile against each
 * platform's.
 */
final class FrapiPlatform {
	/** The class through which Fabricated sends a block to Fabric's renderer on this platform. */
	static final String FABRICATED_SEAM = "io.github.cmdralph.fabricated.neoforge.rendering.FabricBlockRendering";

	private FrapiPlatform() {
	}

	/** The model, as one of the platform's with no {@code emitQuads} of its own, whose parts depend on the level. */
	static BlockStateModel levelAware(BlockStateModel model) {
		return new LevelAwareModel(model);
	}

	private static final class LevelAwareModel extends DelegateBlockStateModel {
		private static final AtomicBoolean TERRAIN = new AtomicBoolean();

		LevelAwareModel(BlockStateModel wrapped) {
			super(wrapped);
		}

		@Override
		public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockModelPart> parts) {
			if (FrapiClientTestMod.isSection(level) && !TERRAIN.getAndSet(true)) FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi level-aware default OK ({})", state);
			super.collectParts(level, pos, state, random, parts);
		}
	}
}
