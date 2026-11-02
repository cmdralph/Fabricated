package net.fabricmc.fabric.impl.client.indigo.renderer.accessor;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.fabric.impl.client.indigo.renderer.render.TerrainRenderContext;

/**
 * Compile-time stand-in: the interface Indigo (Fabric API 0.92, 1.20.1) adds to every {@code RenderChunkRegion}, which
 * holds the terrain context its chunk build hook prepared for the region's compile.
 */
public interface AccessChunkRendererRegion {
	@Nullable TerrainRenderContext fabric_getRenderer();
}
