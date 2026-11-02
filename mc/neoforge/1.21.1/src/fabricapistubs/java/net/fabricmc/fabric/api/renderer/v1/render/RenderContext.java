package net.fabricmc.fabric.api.renderer.v1.render;

import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;

/**
 * Compile-time stand-in: the members of Fabric API 0.116's interface that Fabricated's test mods call, in Mojang's
 * names.
 */
public interface RenderContext {
	void pushTransform(QuadTransform transform);

	void popTransform();

	@FunctionalInterface
	interface QuadTransform {
		boolean transform(MutableQuadView quad);
	}
}
