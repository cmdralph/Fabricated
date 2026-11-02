package net.fabricmc.fabric.api.renderer.v1.render;

import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * Compile-time stand-in: the members of Fabric API 0.116's interface that Fabricated's test mods call, in Mojang's
 * names.
 */
public interface RenderContext {
	void pushTransform(QuadTransform transform);

	void popTransform();

	/** Where the item being drawn is shown ({@code ModelTransformationMode} in Fabric's names); NONE outside items. */
	default ItemDisplayContext itemTransformationMode() {
		throw new AssertionError("stub");
	}

	@FunctionalInterface
	interface QuadTransform {
		boolean transform(MutableQuadView quad);
	}
}
