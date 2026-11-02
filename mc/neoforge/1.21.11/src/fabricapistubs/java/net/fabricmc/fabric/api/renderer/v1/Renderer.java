package net.fabricmc.fabric.api.renderer.v1;

import com.mojang.blaze3d.vertex.PoseStack;

import net.fabricmc.fabric.api.renderer.v1.mesh.MutableMesh;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.render.BlockVertexConsumerProvider;
import net.fabricmc.fabric.api.renderer.v1.render.ItemRenderTypeGetter;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Compile-time stand-in: the members of Fabric API's 1.21.11 interface that Fabricated calls or implements
 * ({@code IndigoWithoutHooks} implements them all), in Mojang's names.
 */
public interface Renderer {
	static Renderer get() {
		throw new AssertionError("stub");
	}

	static void register(Renderer renderer) {
		throw new AssertionError("stub");
	}

	MutableMesh mutableMesh();

	void render(ModelBlockRenderer modelRenderer, BlockAndTintGetter blockView, BlockStateModel model, BlockState state, BlockPos pos, PoseStack matrices,
			BlockVertexConsumerProvider vertexConsumers, boolean cull, long seed, int overlay);

	void render(PoseStack.Pose matrices, BlockVertexConsumerProvider vertexConsumers, BlockStateModel model, float red, float green, float blue, int light,
			int overlay, BlockAndTintGetter blockView, BlockPos pos, BlockState state);

	void renderBlockAsEntity(BlockRenderDispatcher renderManager, BlockState state, PoseStack matrices, MultiBufferSource vertexConsumers, int light,
			int overlay, BlockAndTintGetter blockView, BlockPos pos);

	QuadEmitter getLayerRenderStateEmitter(ItemStackRenderState.LayerRenderState layer);

	void setLayerRenderTypeGetter(ItemStackRenderState.LayerRenderState layer, ItemRenderTypeGetter renderTypeGetter);
}
