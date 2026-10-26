package net.fabricmc.fabric.api.renderer.v1.mesh;

import java.util.function.Consumer;

/** Compile-time stand-in: the member of Fabric API's 1.21.11 interface that Fabricated calls. */
public interface MeshView {
	void forEach(Consumer<? super QuadView> action);
}
