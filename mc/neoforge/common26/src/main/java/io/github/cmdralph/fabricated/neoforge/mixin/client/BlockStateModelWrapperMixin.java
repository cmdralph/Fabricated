package io.github.cmdralph.fabricated.neoforge.mixin.client;

import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.neoforge.rendering.FabricBlockRendering;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.block.model.BlockStateModelWrapper;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fills the render state of a block drawn as an entity (a minecart's contents, an enderman's block, a block display)
 * whose model is a Fabric model with that model's quads, through Fabric's renderer API's mesh (see
 * {@link FabricBlockRendering}). Upstream overwrites this method and sends every block through Fabric's renderer, which
 * drops the model parts NeoForge's own submit draws ({@code BlockModelRenderState.submitMultiLayer}); Fabricated's plan
 * leaves that overwrite out, and every other block keeps NeoForge's path.
 */
@Mixin(BlockStateModelWrapper.class)
abstract class BlockStateModelWrapperMixin {
	@Shadow
	@Final
	private BlockStateModel model;
	@Shadow
	@Final
	private Matrix4fc transformation;

	@Shadow
	abstract void updateTints(BlockModelRenderState renderState, BlockState blockState);

	/** NeoForge fills the state unless the model is a Fabric model, whose quads fill it instead (unless it throws). */
	@Inject(method = "update(Lnet/minecraft/client/renderer/block/BlockModelRenderState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/client/renderer/block/model/BlockDisplayContext;J)V", at = @At("HEAD"), cancellable = true)
	private void fabricated$fabricModels(BlockModelRenderState output, BlockState blockState, BlockDisplayContext displayContext, long seed, CallbackInfo ci) {
		if (!FabricBlockRendering.draws(model)) return;
		// A model that threw is left to NeoForge instead (FRAPI rule 8).
		if (FabricBlockRendering.updateEntityBlock(output, transformation, model, blockState, seed)) {
			updateTints(output, blockState);
			ci.cancel();
		}
	}
}
