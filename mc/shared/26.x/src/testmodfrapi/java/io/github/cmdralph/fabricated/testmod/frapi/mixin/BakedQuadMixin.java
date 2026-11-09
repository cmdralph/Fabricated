package io.github.cmdralph.fabricated.testmod.frapi.mixin;

import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.testmod.frapi.FrapiClientTestMod;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;

/**
 * Marks each quad at the end of {@code BakedQuad}'s vanilla constructor, as Sodium's Fabric build computes its per-quad
 * data there. NeoForge bakes block quads with its own constructor and keeps vanilla's only as a delegate to it, so the
 * mark only reaches them through Fabricated's pre-flight repair (CI's "baked quad constructor hook" check).
 */
@Mixin(BakedQuad.class)
abstract class BakedQuadMixin implements FrapiClientTestMod.MarkedQuad {
	@Unique
	private boolean fabricatedTest$marked;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void fabricatedTest$mark(Vector3fc position0, Vector3fc position1, Vector3fc position2, Vector3fc position3, long packedUV0,
			long packedUV1, long packedUV2, long packedUV3, Direction direction, BakedQuad.MaterialInfo materialInfo, CallbackInfo ci) {
		fabricatedTest$marked = true;
	}

	@Override
	public boolean fabricatedTest$marked() {
		return fabricatedTest$marked;
	}
}
