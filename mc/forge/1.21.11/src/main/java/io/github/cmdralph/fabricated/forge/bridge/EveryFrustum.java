package io.github.cmdralph.fabricated.forge.bridge;

import org.joml.Matrix4f;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;

/**
 * A frustum every box is visible in, for the platform's methods that cull by a frustum where vanilla's culls by none
 * ({@code mixin.LevelRendererBlockEntitiesBridgeMixin}). In this version's folder: Forge 61's {@code Frustum} constructor
 * takes (Matrix4f, Matrix4f).
 */
public final class EveryFrustum extends Frustum {
	public static final Frustum INSTANCE = new EveryFrustum();

	private EveryFrustum() {
		super(new Matrix4f(), new Matrix4f());
	}

	@Override
	public boolean isVisible(AABB box) {
		return true;
	}
}
