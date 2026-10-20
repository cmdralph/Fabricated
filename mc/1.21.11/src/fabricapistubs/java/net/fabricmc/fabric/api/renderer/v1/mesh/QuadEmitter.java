package net.fabricmc.fabric.api.renderer.v1.mesh;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;

/**
 * Compile-time stand-in: the members of Fabric API's 1.21.11 interface that Fabricated and its test mod call, in Mojang's
 * names.
 */
public interface QuadEmitter extends MutableQuadView {
	QuadEmitter color(int vertexIndex, int color);

	QuadEmitter normal(int vertexIndex, float x, float y, float z);

	QuadEmitter cullFace(@Nullable Direction face);

	QuadEmitter ambientOcclusion(TriState ao);

	QuadEmitter shadeMode(ShadeMode mode);

	QuadEmitter fromBakedQuad(BakedQuad quad);

	void pushTransform(QuadTransform transform);

	void popTransform();

	QuadEmitter emit();
}
