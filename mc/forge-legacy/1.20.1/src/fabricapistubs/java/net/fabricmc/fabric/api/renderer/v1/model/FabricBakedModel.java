package net.fabricmc.fabric.api.renderer.v1.model;

import java.util.function.Supplier;

import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Compile-time stand-in: the members of Fabric API 0.92's interface (1.20.1; its renderer API adds it to every
 * {@code BakedModel}) that Fabricated and its renderer test mod call, in Mojang's names.
 */
public interface FabricBakedModel {
	default boolean isVanillaAdapter() {
		throw new AssertionError("stub");
	}

	default void emitBlockQuads(BlockAndTintGetter blockView, BlockState state, BlockPos pos, Supplier<RandomSource> randomSupplier, RenderContext context) {
		throw new AssertionError("stub");
	}

	default void emitItemQuads(ItemStack stack, Supplier<RandomSource> randomSupplier, RenderContext context) {
		throw new AssertionError("stub");
	}
}
