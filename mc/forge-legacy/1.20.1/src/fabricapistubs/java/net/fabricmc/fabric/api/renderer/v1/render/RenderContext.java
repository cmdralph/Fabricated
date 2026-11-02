package net.fabricmc.fabric.api.renderer.v1.render;

import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * Compile-time stand-in: the members of Fabric API 0.92's interface (1.20.1) that Fabricated and its renderer test mod
 * call, in Mojang's names.
 */
public interface RenderContext {
	void pushTransform(QuadTransform transform);

	void popTransform();

	/** Whether a transform is pushed. */
	default boolean hasTransform() {
		throw new AssertionError("stub");
	}

	/** Where the item being drawn is shown ({@code ModelTransformationMode} in Fabric's names); NONE outside items. */
	default ItemDisplayContext itemTransformationMode() {
		throw new AssertionError("stub");
	}

	@FunctionalInterface
	interface QuadTransform {
		boolean transform(MutableQuadView quad);
	}
}
