package io.github.cmdralph.fabricated.neoforge.mixin.client;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;

import io.github.cmdralph.fabricated.neoforge.rendering.FabricBlockRendering;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.feature.BlockFeatureRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The block breaking overlay of a Fabric model: its geometry, which NeoForge can't collect from it, is added as one
 * part (see {@link FabricBlockRendering}). On 26.1.2 the block feature renderer draws the overlay; upstream's
 * replacement of that method, which sent every model through Fabric's renderer, is left out (fabric-api-plan.json).
 */
@Mixin(BlockFeatureRenderer.class)
abstract class BlockBreakingRenderMixin {
	@WrapWithCondition(method = "renderBreakingBlockModelSubmits", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;collectParts(Lnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/util/RandomSource;Ljava/util/List;)V"))
	private boolean fabricated$fabricModels(BlockStateModel model, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
			List<BlockStateModelPart> parts) {
		if (!FabricBlockRendering.draws(model)) return true;
		FabricBlockRendering.collectBreakingParts(model, level, pos, state, random, parts);
		return false;
	}
}
