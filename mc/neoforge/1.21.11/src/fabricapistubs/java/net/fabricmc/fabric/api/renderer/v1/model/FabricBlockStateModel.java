package net.fabricmc.fabric.api.renderer.v1.model;

import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Compile-time stand-in: the member of Fabric API's 1.21.11 interface that Fabricated's test mod overrides and calls,
 * in Mojang's names. Fabricated itself only names the type.
 */
public interface FabricBlockStateModel {
	default void emitQuads(QuadEmitter emitter, BlockAndTintGetter blockView, BlockPos pos, BlockState state, RandomSource random,
			Predicate<@Nullable Direction> cullTest) {
		throw new AssertionError("stub");
	}
}
