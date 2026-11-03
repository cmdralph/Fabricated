package io.github.cmdralph.fabricated.neoforge.rendering.mixin;

import java.util.function.Supplier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import io.github.cmdralph.fabricated.core.runtime.HeldThrows;
import io.github.cmdralph.fabricated.neoforge.rendering.FabricBlockRendering;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Holds what a wrapped model throws where a model wrapping it through Fabric's {@code ForwardingBakedModel} calls it,
 * while one of Fabricated's Indigo draws is under way on this thread (1.21.1, FRAPI rule 8, {@link HeldThrows}): the
 * wrapper's call returns, with the context's quad transforms as it gave them, and the wrapper finishes; Fabricated's
 * draw then takes the throw back and NeoForge's renderer draws the block. Continuity's connected texture models keep a
 * per-thread transform active through this call, and a throw unwinding them left it active, so that thread drew no
 * connected textures for the rest of the session (CORPUS-56). Outside Fabricated's draws, a throw passes as before.
 *
 * <p>{@code @Pseudo}: Fabric API's renderer API is a mod, which a game may not have.
 */
@Pseudo
@Mixin(targets = "net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel")
abstract class ForwardingBakedModelMixin {
	@WrapMethod(method = "emitBlockQuads(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Ljava/util/function/Supplier;Lnet/fabricmc/fabric/api/renderer/v1/render/RenderContext;)V", require = 0)
	private void fabricated$holdWrappedThrow(BlockAndTintGetter level, BlockState state, BlockPos pos, Supplier<RandomSource> random, RenderContext context,
			Operation<Void> original) {
		if (!HeldThrows.holding()) {
			original.call(level, state, pos, random, context);
			return;
		}
		int transforms = FabricBlockRendering.transforms(context);
		try {
			original.call(level, state, pos, random, context);
		} catch (Throwable thrown) {
			if (!HeldThrows.hold(thrown)) throw thrown;
			FabricBlockRendering.dropTransforms(context, transforms);
		}
	}
}
