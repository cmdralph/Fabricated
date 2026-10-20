package net.fabricmc.fabric.api.renderer.v1.mesh;

/** Compile-time stand-in for Fabric API's 1.21.11 interface. */
@FunctionalInterface
public interface QuadTransform {
	boolean transform(MutableQuadView quad);
}
