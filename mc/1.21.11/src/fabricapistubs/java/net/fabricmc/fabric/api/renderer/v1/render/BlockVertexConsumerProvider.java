package net.fabricmc.fabric.api.renderer.v1.render;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.chunk.ChunkSectionLayer;

/** Compile-time stand-in for Fabric API's 1.21.11 interface, in Mojang's names. */
public interface BlockVertexConsumerProvider {
	VertexConsumer getBuffer(ChunkSectionLayer layer);
}
