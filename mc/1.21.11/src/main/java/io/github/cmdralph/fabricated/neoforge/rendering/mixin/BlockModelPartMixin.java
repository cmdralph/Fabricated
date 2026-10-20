package io.github.cmdralph.fabricated.neoforge.rendering.mixin;

import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

import io.github.cmdralph.fabricated.neoforge.rendering.FrapiDefaults;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBlockModelPart;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.core.Direction;

/** A block model part without its own {@code emitQuads} keeps NeoForge's ambient occlusion (1.21.11; see {@link FrapiDefaults}). */
@Mixin(BlockModelPart.class)
interface BlockModelPartMixin extends FabricBlockModelPart {
	@Override
	default void emitQuads(QuadEmitter emitter, Predicate<@Nullable Direction> cullTest) {
		FrapiDefaults.emitQuads((BlockModelPart) this, emitter, cullTest);
	}
}
