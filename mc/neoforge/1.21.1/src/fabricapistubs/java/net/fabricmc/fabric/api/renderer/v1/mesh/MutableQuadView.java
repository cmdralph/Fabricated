package net.fabricmc.fabric.api.renderer.v1.mesh;

/**
 * Compile-time stand-in: the members of Fabric API 0.116's quad view (declared on {@code QuadView}, which it extends)
 * that Fabricated's test mods read.
 */
public interface MutableQuadView {
	/** The vertex's texture coordinates in the atlas. */
	float u(int vertexIndex);

	float v(int vertexIndex);
}
