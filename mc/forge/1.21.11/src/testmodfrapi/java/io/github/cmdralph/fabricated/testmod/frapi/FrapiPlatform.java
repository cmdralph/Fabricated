package io.github.cmdralph.fabricated.testmod.frapi;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

/**
 * What the renderer test mod's shared checks (mc/shared) ask of the platform they run on, Forge here (1.21.11). Every
 * platform's renderer test mod for 1.21.11 has this class with these methods, so the checks compile against each
 * platform's.
 */
final class FrapiPlatform {
	/** The class through which Fabricated sends a block to Fabric's renderer on this platform. */
	static final String FABRICATED_SEAM = "io.github.cmdralph.fabricated.forge.rendering.FabricBlockRendering";

	private FrapiPlatform() {
	}

	/**
	 * Once a world is loaded, what the platform's own renderers get from the test block's Fabric model: Forge's draw a
	 * Fabric block model through Fabric's renderer, where Fabricated sends it, and never from its parts.
	 */
	static void checkRendererParts(Minecraft client) {
	}

	/**
	 * The model, as one of the platform's with no {@code emitQuads} of its own, whose parts depend on the level: on Forge
	 * through its model data, which Forge asks of a model with the level.
	 */
	static BlockStateModel levelAware(BlockStateModel model) {
		return new LevelAwareModel(model);
	}

	private record LevelAwareModel(BlockStateModel wrapped) implements BlockStateModel {
		private static final AtomicBoolean TERRAIN = new AtomicBoolean();

		@Override
		public @NotNull ModelData getModelData(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ModelData data) {
			if (FrapiClientTestMod.isSection(level) && !TERRAIN.getAndSet(true)) FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi level-aware default OK ({})", state);
			return wrapped.getModelData(level, pos, state, data);
		}

		@Override
		public void collectParts(RandomSource random, List<BlockModelPart> parts) {
			wrapped.collectParts(random, parts);
		}

		@Override
		public void collectParts(RandomSource random, List<BlockModelPart> parts, ModelData data, @Nullable ChunkSectionLayer layer) {
			wrapped.collectParts(random, parts, data, layer);
		}

		@Override
		public Collection<ChunkSectionLayer> getRenderTypes(@NotNull BlockState state, @NotNull RandomSource random, @NotNull ModelData data) {
			return wrapped.getRenderTypes(state, random, data);
		}

		@Override
		public TextureAtlasSprite particleIcon() {
			return wrapped.particleIcon();
		}

		@Override
		public TextureAtlasSprite particleIcon(@NotNull ModelData data) {
			return wrapped.particleIcon(data);
		}
	}
}
