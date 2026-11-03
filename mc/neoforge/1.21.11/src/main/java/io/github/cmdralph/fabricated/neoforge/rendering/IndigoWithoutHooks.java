package io.github.cmdralph.fabricated.neoforge.rendering;

import com.mojang.blaze3d.vertex.PoseStack;

import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableMesh;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.render.BlockVertexConsumerProvider;
import net.fabricmc.fabric.api.renderer.v1.render.ItemRenderTypeGetter;
import net.fabricmc.fabric.api.renderer.v1.render.RenderLayerHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fabric API's own renderer (Indigo) as Fabricated registers it when no renderer registered and Indigo stood down for
 * a mod saying it contains one (Sodium for NeoForge on 1.21.11, which meshes chunks itself and has none). Indigo's
 * mixins then never applied, and three of its methods need theirs: drawing a block as an entity reads the block colours
 * through an accessor, and an item layer's emitter and render type getter live in a mixin on the layer, so a Fabric
 * item model failed (ClassCastException) as it was drawn (FRAPI-10). Here those work without them:
 * <ul>
 * <li>a block drawn as an entity takes the game's block colours, which the dispatcher holds;</li>
 * <li>an item layer's emitter writes into a mesh of the layer's own ({@link FallbackLayerMesh}), whose quads join the
 * layer's quads as it is drawn, in the layer's render type;</li>
 * <li>an item layer's render type getter isn't asked (a layer's quads have one render type), so a quad's own render
 * layer or blend mode isn't kept.</li>
 * </ul>
 * Meshes and block models go to Indigo itself, whose contexts need no mixin.
 */
final class IndigoWithoutHooks implements Renderer {
	private final Renderer indigo;

	IndigoWithoutHooks(Renderer indigo) {
		this.indigo = indigo;
	}

	@Override
	public MutableMesh mutableMesh() {
		return indigo.mutableMesh();
	}

	@Override
	public void render(ModelBlockRenderer modelRenderer, BlockAndTintGetter blockView, BlockStateModel model, BlockState state, BlockPos pos, PoseStack matrices,
			BlockVertexConsumerProvider vertexConsumers, boolean cull, long seed, int overlay) {
		indigo.render(modelRenderer, blockView, model, state, pos, matrices, vertexConsumers, cull, seed, overlay);
	}

	@Override
	public void render(PoseStack.Pose matrices, BlockVertexConsumerProvider vertexConsumers, BlockStateModel model, float red, float green, float blue, int light,
			int overlay, BlockAndTintGetter blockView, BlockPos pos, BlockState state) {
		indigo.render(matrices, vertexConsumers, model, red, green, blue, light, overlay, blockView, pos, state);
	}

	/** As Indigo's, with the game's block colours (the dispatcher's, which Indigo reads through its accessor mixin). */
	@Override
	public void renderBlockAsEntity(BlockRenderDispatcher renderManager, BlockState state, PoseStack matrices, MultiBufferSource vertexConsumers, int light,
			int overlay, BlockAndTintGetter blockView, BlockPos pos) {
		if (state.getRenderShape() == RenderShape.INVISIBLE) return;
		BlockStateModel model = renderManager.getBlockModel(state);
		int tint = Minecraft.getInstance().getBlockColors().getColor(state, null, null, 0);
		float red = (tint >> 16 & 255) / 255.0F;
		float green = (tint >> 8 & 255) / 255.0F;
		float blue = (tint & 255) / 255.0F;
		// What FabricBlockModelRenderer.render does, which goes to the registered renderer: this one, so Indigo.
		indigo.render(matrices.last(), RenderLayerHelper.entityDelegate(vertexConsumers), model, red, green, blue, light, overlay, blockView, pos, state);
	}

	@Override
	public QuadEmitter getLayerRenderStateEmitter(ItemStackRenderState.LayerRenderState layer) {
		return ((FallbackLayerMesh) (Object) layer).fabricated$fallbackMesh().emitter();
	}

	/** Not asked: the layer's quads are drawn in its own render type. */
	@Override
	public void setLayerRenderTypeGetter(ItemStackRenderState.LayerRenderState layer, ItemRenderTypeGetter renderTypeGetter) {
	}
}
