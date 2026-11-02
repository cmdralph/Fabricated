package io.github.cmdralph.fabricated.forge.rendering.mixin;

import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import io.github.cmdralph.fabricated.forge.rendering.FabricBlockRendering;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.model.BlockStateModelWrapper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A block drawn as an entity (a minecart's contents, an enderman's block, a block display): the renderer API's
 * replacement of this method fills the render state's mesh from the model's emitter, which goes through Fabricated's
 * guard (FRAPI rule 8). Without the renderer API the call isn't there (vanilla's method collects parts), so nothing
 * applies: require 0. The renderer test mod's broken entity model checks that it does with it.
 *
 * <p>The method is upstream's (an {@code @Overwrite} at Mixin's default priority, 1000), and Mixin lets a mixin inject
 * into a method another one merged only at a higher priority, which also applies it after upstream's.
 */
@Mixin(value = BlockStateModelWrapper.class, priority = 1100)
abstract class EntityBlockModelMixin {
	@Redirect(method = "update", require = 0, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;emitQuads("
			+ "Lnet/fabricmc/fabric/api/client/renderer/v1/mesh/QuadEmitter;Lnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;"
			+ "Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/util/RandomSource;Ljava/util/function/Predicate;)V"))
	private void fabricated$guardEmit(BlockStateModel model, QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
			Predicate<@Nullable Direction> cullTest) {
		FabricBlockRendering.emit(model, emitter, level, pos, state, random, cullTest, FabricBlockRendering.ENTITY);
	}
}
