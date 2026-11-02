package net.fabricmc.fabric.api.renderer.v1;

import org.jspecify.annotations.Nullable;

/**
 * Compile-time stand-in: the members of Fabric API 0.116's interface that Fabricated calls.
 */
public interface RendererAccess {
	RendererAccess INSTANCE = null;

	@Nullable Renderer getRenderer();
}
