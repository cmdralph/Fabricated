package io.github.cmdralph.fabricated.forge.rendering.breaking;

import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import io.github.cmdralph.fabricated.forge.rendering.FabricBlockRendering;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.feature.BlockFeatureRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The block breaking overlay on 26.1.2: the renderer API's replacement of this method draws each breaking block from
 * its model's emitter, which goes through Fabricated's guard (FRAPI rule 8). Without the renderer API the call isn't
 * there (vanilla's method draws parts), so nothing applies: require 0.
 *
 * <p>The method is upstream's (an {@code @Overwrite} at Mixin's default priority, 1000), and Mixin lets a mixin inject
 * into a method another one merged only at a higher priority, which also applies it after upstream's.
 */
@Mixin(value = BlockFeatureRenderer.class, priority = 1100)
abstract class BreakingOverlayMixin {
	@Redirect(method = "renderBreakingBlockModelSubmits", require = 0, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;emitQuads("
			+ "Lnet/fabricmc/fabric/api/client/renderer/v1/mesh/QuadEmitter;Lnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;"
			+ "Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/util/RandomSource;Ljava/util/function/Predicate;)V"))
	private void fabricated$guardEmit(BlockStateModel model, QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
			Predicate<@Nullable Direction> cullTest) {
		FabricBlockRendering.emit(model, emitter, level, pos, state, random, cullTest, FabricBlockRendering.BREAKING);
	}
}
