package net.fabricmc.fabric.api.renderer.v1.render;

import net.minecraft.client.renderer.MultiBufferSource;

/** Compile-time stand-in: the member of Fabric API's 1.21.11 class that Fabricated calls, in Mojang's names. */
public final class RenderLayerHelper {
	private RenderLayerHelper() {
	}

	public static BlockVertexConsumerProvider entityDelegate(MultiBufferSource vertexConsumers) {
		throw new AssertionError("stub");
	}
}
