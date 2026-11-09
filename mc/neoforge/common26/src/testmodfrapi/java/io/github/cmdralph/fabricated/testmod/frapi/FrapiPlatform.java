package io.github.cmdralph.fabricated.testmod.frapi;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;
import net.neoforged.neoforge.client.model.block.CompositeBlockModel;

/**
 * What the renderer test mod's shared checks (mc/shared) ask of the platform they run on, NeoForge here (26.x). Every
 * platform's renderer test mod has this class with these methods, so the checks compile against each platform's.
 */
final class FrapiPlatform {
	/** The class through which Fabricated sends a block to Fabric's renderer on this platform. */
	static final String FABRICATED_SEAM = "io.github.cmdralph.fabricated.neoforge.rendering.FabricBlockRendering";

	private FrapiPlatform() {
	}

	/** Whether the platform loaded a mod of its own with this id (Forgified Fabric API's modules). */
	static boolean isModLoaded(String modId) {
		return ModList.get().isLoaded(modId);
	}

	/** Whether the model is the one the platform's composite blockstate model type bakes ({@code neoforge:composite}). */
	static boolean isPlatformComposite(BlockStateModel model) {
		return model instanceof CompositeBlockModel;
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
		public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockStateModelPart> parts) {
			if (FrapiClientTestMod.isSection(level) && !TERRAIN.getAndSet(true)) FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi level-aware default OK ({})", state);
			super.collectParts(level, pos, state, random, parts);
		}
	}
}
