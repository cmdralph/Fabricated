package io.github.cmdralph.fabricated.neoforge.rendering.mixin;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.neoforge.rendering.FabricBlockRendering;
import io.github.cmdralph.fabricated.neoforge.rendering.FallbackLayerMesh;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableMesh;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.item.ItemStackRenderState;

/**
 * Where a Fabric item model's emitted quads go when the renderer is Indigo without its own hooks (1.21.11,
 * {@code IndigoWithoutHooks}): a mesh of the layer's own, whose quads join the layer's quads as the layer is drawn, so
 * the game's item renderer (or the mod replacing it) draws them in the layer's render type. Unused with any other
 * renderer (Indigo with its hooks keeps a mesh of its own on the layer): the mesh is made only when asked for.
 */
@Mixin(ItemStackRenderState.LayerRenderState.class)
abstract class LayerRenderStateMixin implements FallbackLayerMesh {
	@Unique
	private @Nullable MutableMesh fabricated$mesh;

	@Shadow
	public abstract List<BakedQuad> prepareQuadList();

	@Override
	public MutableMesh fabricated$fallbackMesh() {
		MutableMesh mesh = fabricated$mesh;
		if (mesh == null) fabricated$mesh = mesh = FabricBlockRendering.fallbackMesh();
		return mesh;
	}

	@Inject(method = "clear()V", at = @At("RETURN"))
	private void fabricated$clearMesh(CallbackInfo ci) {
		if (fabricated$mesh != null) fabricated$mesh.clear();
	}

	@Inject(method = "submit", at = @At("HEAD"))
	private void fabricated$drawMesh(CallbackInfo ci) {
		MutableMesh mesh = fabricated$mesh;
		if (mesh == null || mesh.size() == 0) return;
		// Into the layer's quads once: a layer drawn again (an outline, a second pass) draws them from there.
		FabricBlockRendering.addItemQuads(mesh, prepareQuadList());
		mesh.clear();
	}
}
