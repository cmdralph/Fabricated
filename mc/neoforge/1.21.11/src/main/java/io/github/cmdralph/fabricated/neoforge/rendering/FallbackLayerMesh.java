package io.github.cmdralph.fabricated.neoforge.rendering;

import net.fabricmc.fabric.api.renderer.v1.mesh.MutableMesh;

/**
 * An item layer's mesh for Fabric's renderer API when the renderer is Indigo without its own hooks (1.21.11,
 * {@link IndigoWithoutHooks}): implemented on {@code ItemStackRenderState.LayerRenderState} by {@code LayerRenderStateMixin},
 * which turns the quads emitted into it into the layer's own quads as the layer is drawn.
 */
public interface FallbackLayerMesh {
	/** The layer's mesh, made on first use; emptied as the layer is cleared or drawn. */
	MutableMesh fabricated$fallbackMesh();
}
