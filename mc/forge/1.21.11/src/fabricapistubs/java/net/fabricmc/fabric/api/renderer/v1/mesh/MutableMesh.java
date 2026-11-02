package net.fabricmc.fabric.api.renderer.v1.mesh;

/** Compile-time stand-in: the members of Fabric API's 1.21.11 interface that Fabricated calls. */
public interface MutableMesh extends MeshView {
	QuadEmitter emitter();

	void clear();
}
