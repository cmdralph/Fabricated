package net.fabricmc.fabric.api.renderer.v1.model;

import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.minecraft.core.Direction;

/** Compile-time stand-in: the member of Fabric API's 1.21.11 interface (injected into {@code BlockModelPart}) that Fabricated calls. */
public interface FabricBlockModelPart {
	default void emitQuads(QuadEmitter emitter, Predicate<@Nullable Direction> cullTest) {
		throw new AssertionError("stub");
	}
}
