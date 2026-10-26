package io.github.cmdralph.fabricated.neoforge.rendering.mixin;

import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

import io.github.cmdralph.fabricated.neoforge.rendering.FrapiDefaults;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.model.FabricBlockStateModel;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A block state model without its own {@code emitQuads} emits the parts NeoForge's level-aware {@code collectParts} gives
 * (see {@link FrapiDefaults}). Declared on {@link BlockStateModel}, so models still count as drawing nothing of their own
 * ({@code FabricBlockRendering}).
 */
@Mixin(BlockStateModel.class)
interface BlockStateModelMixin extends FabricBlockStateModel {
	@Override
	default void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, Predicate<@Nullable Direction> cullTest) {
		FrapiDefaults.emitQuads((BlockStateModel) this, emitter, level, pos, state, random, cullTest);
	}
}
