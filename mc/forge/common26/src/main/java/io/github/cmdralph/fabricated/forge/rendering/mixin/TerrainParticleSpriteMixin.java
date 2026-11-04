package io.github.cmdralph.fabricated.forge.rendering.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import io.github.cmdralph.fabricated.forge.rendering.FabricBlockRendering;
import net.fabricmc.fabric.api.client.renderer.v1.model.FabricBlockStateModel;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

/**
 * A block's breaking particles keep a Fabric model's level-aware particle material (PART-4). Upstream's hook gives it
 * as the particle is made, but Forge 64 to 66 then set the sprite again from the block's position ({@code updateSprite},
 * called by each of the client level's particle effects and the block particle's provider), asking the model with its
 * model data. Fabricated asks Fabric's level-aware material there instead for a Fabric model, in the particle's level;
 * every other model keeps Forge's call.
 */
@Mixin(TerrainParticle.class)
abstract class TerrainParticleSpriteMixin {
	@WrapOperation(method = "updateSprite", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;particleMaterial("
			+ "Lnet/minecraftforge/client/model/data/ModelData;)Lnet/minecraft/client/resources/model/sprite/Material$Baked;"))
	private Material.Baked fabricated$levelAwareMaterial(BlockStateModel model, ModelData data, Operation<Material.Baked> original,
			@Local(argsOnly = true) BlockState state, @Local(argsOnly = true) BlockPos pos) {
		return FabricBlockRendering.levelAwareParticle(model)
				? ((FabricBlockStateModel) model).particleMaterial(((ParticleLevelAccessor) (Object) this).fabricated$level(), pos, state)
				: original.call(model, data);
	}
}
