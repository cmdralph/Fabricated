package net.fabricmc.fabric.api.renderer.v1.render;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.renderer.v1.mesh.QuadAtlas;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.RenderType;

/** Compile-time stand-in for Fabric API's 1.21.11 interface, in Mojang's names. */
@FunctionalInterface
public interface ItemRenderTypeGetter {
	/** The render type for an item layer's emitted quads of this atlas and chunk layer; null for the layer's own. */
	@Nullable RenderType renderType(QuadAtlas quadAtlas, @Nullable ChunkSectionLayer sectionLayer);
}
