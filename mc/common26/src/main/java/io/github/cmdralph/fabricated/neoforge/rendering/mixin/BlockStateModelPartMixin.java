package io.github.cmdralph.fabricated.neoforge.rendering.mixin;

import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

import io.github.cmdralph.fabricated.neoforge.rendering.FrapiDefaults;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.model.FabricBlockStateModelPart;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.core.Direction;

/** A block state model part without its own {@code emitQuads} keeps NeoForge's ambient occlusion (see {@link FrapiDefaults}). */
@Mixin(BlockStateModelPart.class)
interface BlockStateModelPartMixin extends FabricBlockStateModelPart {
	@Override
	default void emitQuads(QuadEmitter emitter, Predicate<@Nullable Direction> cullTest) {
		FrapiDefaults.emitQuads((BlockStateModelPart) this, emitter, cullTest);
	}
}
