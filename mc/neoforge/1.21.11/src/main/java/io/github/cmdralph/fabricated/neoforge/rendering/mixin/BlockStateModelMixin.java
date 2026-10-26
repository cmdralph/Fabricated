package io.github.cmdralph.fabricated.neoforge.rendering.mixin;

import java.util.List;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

import io.github.cmdralph.fabricated.neoforge.rendering.FabricBlockRendering;
import io.github.cmdralph.fabricated.neoforge.rendering.FrapiDefaults;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBlockStateModel;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.extensions.BlockStateModelExtension;

/**
 * Defaults for a block state model that doesn't implement these itself (1.21.11), declared on {@link BlockStateModel},
 * so models still count as drawing nothing of their own ({@code FabricBlockRendering}):
 * <ul>
 * <li>{@code emitQuads} emits the parts NeoForge's level-aware {@code collectParts} gives (see {@link FrapiDefaults});</li>
 * <li>NeoForge's level-aware {@code collectParts} makes a Fabric model's parts from the quads it emits, so NeoForge's
 * renderers and the mods that replace them see it (see {@link FabricBlockRendering#collectParts}).</li>
 * </ul>
 */
@Mixin(BlockStateModel.class)
interface BlockStateModelMixin extends FabricBlockStateModel, BlockStateModelExtension {
	@Override
	default void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, Predicate<@Nullable Direction> cullTest) {
		FrapiDefaults.emitQuads((BlockStateModel) this, emitter, level, pos, state, random, cullTest);
	}

	@Override
	default void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockModelPart> parts) {
		FabricBlockRendering.collectParts((BlockStateModel) this, level, pos, state, random, parts);
	}
}
