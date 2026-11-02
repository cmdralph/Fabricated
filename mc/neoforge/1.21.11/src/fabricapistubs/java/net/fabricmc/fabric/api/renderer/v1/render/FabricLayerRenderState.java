package net.fabricmc.fabric.api.renderer.v1.render;

import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;

/**
 * Compile-time stand-in for Fabric API's 1.21.11 interface, which its renderer API adds to
 * {@code ItemStackRenderState.LayerRenderState}: the members the test mod calls.
 */
public interface FabricLayerRenderState {
	default QuadEmitter emitter() {
		throw new AssertionError("stub");
	}

	default void setRenderTypeGetter(ItemRenderTypeGetter renderTypeGetter) {
		throw new AssertionError("stub");
	}
}
