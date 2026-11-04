package io.github.cmdralph.fabricated.forge.rendering.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import io.github.cmdralph.fabricated.forge.rendering.FabricBlockRendering;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBlockStateModel;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A block's breaking particles keep a Fabric model's level-aware particle sprite (PART-4). Upstream's hook gives it as
 * the particle is made, but Forge 61 then sets the sprite again from the block's position ({@code updateSprite}, called
 * by each of the client level's particle effects and the block particle's provider), asking the model with its model
 * data. Fabricated asks Fabric's level-aware sprite there instead for a Fabric model; every other model keeps Forge's
 * call.
 */
@Mixin(TerrainParticle.class)
abstract class TerrainParticleSpriteMixin {
	@WrapOperation(method = "updateSprite", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/BlockModelShaper;getParticleIcon("
			+ "Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;"))
	private TextureAtlasSprite fabricated$levelAwareSprite(BlockModelShaper models, BlockState state, Level level, BlockPos pos, Operation<TextureAtlasSprite> original) {
		BlockStateModel model = models.getBlockModel(state);
		return FabricBlockRendering.levelAwareParticle(model) ? ((FabricBlockStateModel) model).particleSprite(level, pos, state)
				: original.call(models, state, level, pos);
	}
}
