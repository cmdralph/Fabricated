package net.fabricmc.fabric.api.renderer.v1.mesh;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;

/** Compile-time stand-in: the members of Fabric API's 1.21.11 interface that Fabricated and its test mod read. */
public interface QuadView {
	int color(int vertexIndex);

	boolean hasNormal(int vertexIndex);

	float normalX(int vertexIndex);

	float normalY(int vertexIndex);

	float normalZ(int vertexIndex);

	@Nullable
	Direction cullFace();

	@Nullable
	ChunkSectionLayer renderLayer();

	TriState ambientOcclusion();

	BakedQuad toBakedQuad(TextureAtlasSprite sprite);
}
