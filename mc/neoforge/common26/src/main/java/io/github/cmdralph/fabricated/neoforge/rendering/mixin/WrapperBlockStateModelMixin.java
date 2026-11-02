package io.github.cmdralph.fabricated.neoforge.rendering.mixin;

import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import io.github.cmdralph.fabricated.core.runtime.HeldThrows;
import io.github.cmdralph.fabricated.neoforge.rendering.FabricBlockRendering;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Holds what a wrapped model throws where a model wrapping it through Fabric's {@code WrapperBlockStateModel} calls it,
 * while one of Fabricated's draws of a Fabric model is under way on this thread (26.x, FRAPI rule 8,
 * {@link HeldThrows}): the wrapper's call returns, with the emitter's quad transforms as it gave them, and the wrapper
 * finishes; Fabricated's draw then takes the throw back and NeoForge's renderer draws the block. Continuity's connected
 * texture models keep a per-thread transform active through this call, and a throw unwinding them left it active, so
 * that thread drew no connected textures for the rest of the session (CORPUS-56). Outside Fabricated's draws, a throw
 * passes as before.
 *
 * <p>{@code @Pseudo}: Fabric API's model loading API is a mod, which a game may not have.
 */
@Pseudo
@Mixin(targets = "net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel")
abstract class WrapperBlockStateModelMixin {
	@WrapMethod(method = "emitQuads(Lnet/fabricmc/fabric/api/client/renderer/v1/mesh/QuadEmitter;Lnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/util/RandomSource;Ljava/util/function/Predicate;)V", require = 0)
	private void fabricated$holdWrappedThrow(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
			Predicate<@Nullable Direction> cullTest, Operation<Void> original) {
		if (!HeldThrows.holding()) {
			original.call(emitter, level, pos, state, random, cullTest);
			return;
		}
		int transforms = FabricBlockRendering.transforms(emitter);
		try {
			original.call(emitter, level, pos, state, random, cullTest);
		} catch (Throwable thrown) {
			if (!HeldThrows.hold(thrown)) throw thrown;
			FabricBlockRendering.dropTransforms(emitter, transforms);
		}
	}
}
