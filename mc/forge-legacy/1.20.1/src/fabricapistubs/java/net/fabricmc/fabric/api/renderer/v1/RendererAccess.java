package net.fabricmc.fabric.api.renderer.v1;

import org.jetbrains.annotations.Nullable;

/**
 * Compile-time stand-in: the members of Fabric API 0.92's interface (1.20.1) that Fabricated calls.
 */
public interface RendererAccess {
	RendererAccess INSTANCE = null;

	@Nullable Renderer getRenderer();
}
