package io.github.cmdralph.fabricated.neoforge.mixin.client;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.extensions.BlockStateModelExtension;

/**
 * Declares NeoForge's model defaults on {@link BlockStateModel} itself. {@code fabric-renderer-api-v1} makes every block
 * state model a {@code FabricBlockStateModel}, whose defaults for these two methods have NeoForge's signatures (and do
 * the same); a model inheriting both defaults could not call either, so the most specific one is declared here.
 * Without Fabric's renderer API this changes nothing.
 */
@Mixin(BlockStateModel.class)
public interface BlockStateModelDefaultsMixin extends BlockStateModelExtension {
	@Override
	default @Nullable Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
		return BlockStateModelExtension.super.createGeometryKey(level, pos, state, random);
	}

	@Override
	default Material.Baked particleMaterial(BlockAndTintGetter level, BlockPos pos, BlockState state) {
		return BlockStateModelExtension.super.particleMaterial(level, pos, state);
	}
}
