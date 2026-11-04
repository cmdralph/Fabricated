package io.github.cmdralph.fabricated.forge.rendering.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import io.github.cmdralph.fabricated.forge.rendering.FabricBlockRendering;
import net.fabricmc.fabric.api.client.renderer.v1.model.FabricBlockStateModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.BlockMarker;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

/**
 * A block marker (the particle a barrier or a light block shows) takes a Fabric model's level-aware particle material,
 * as upstream's hook gives it (PART-4). Forge 64 to 66 ask the model with its model data instead of the call upstream's
 * hook redirects, so pre-flight left the hook out. The call is an argument of the particle's super constructor, where
 * upstream's hook is too: a static redirect, with the constructor's arguments, as upstream's takes them. Every other
 * model keeps Forge's call.
 */
@Mixin(BlockMarker.class)
abstract class BlockMarkerParticleMixin {
	@Redirect(method = "<init>(Lnet/minecraft/client/multiplayer/ClientLevel;DDDLnet/minecraft/world/level/block/state/BlockState;)V", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;particleMaterial(Lnet/minecraftforge/client/model/data/ModelData;)"
					+ "Lnet/minecraft/client/resources/model/sprite/Material$Baked;"))
	private static Material.Baked fabricated$levelAwareMaterial(BlockStateModel model, ModelData data, ClientLevel level, double x, double y, double z,
			BlockState state) {
		return FabricBlockRendering.levelAwareParticle(model) ? ((FabricBlockStateModel) model).particleMaterial(level, BlockPos.containing(x, y, z), state)
				: model.particleMaterial(data);
	}
}
