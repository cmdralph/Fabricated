package io.github.cmdralph.fabricated.forge.rendering.mixin;

import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import io.github.cmdralph.fabricated.forge.rendering.FabricBlockRendering;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Indigo's block renderer, which upstream's hooks give every block of a chunk section and every moving block on Forge:
 * its call of the model's emitter goes through Fabricated's guard (FRAPI rule 8, {@link FabricBlockRendering#emit}).
 * Pseudo: Mixin skips it without a word when Fabric API's renderer (Indigo) isn't installed.
 */
@Pseudo
@Mixin(targets = "net.fabricmc.fabric.impl.client.indigo.renderer.render.AltModelBlockRendererImpl", remap = false)
abstract class IndigoBlockRendererMixin {
	@Redirect(method = "tesselateBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;emitQuads("
			+ "Lnet/fabricmc/fabric/api/client/renderer/v1/mesh/QuadEmitter;Lnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;"
			+ "Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/util/RandomSource;Ljava/util/function/Predicate;)V"))
	private void fabricated$guardEmit(BlockStateModel model, QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
			Predicate<@Nullable Direction> cullTest) {
		FabricBlockRendering.emit(model, emitter, level, pos, state, random, cullTest, FabricBlockRendering.RENDERER);
	}
}
