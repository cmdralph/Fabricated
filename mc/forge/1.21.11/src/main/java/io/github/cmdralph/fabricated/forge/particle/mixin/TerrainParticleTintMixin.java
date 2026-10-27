package io.github.cmdralph.fabricated.forge.particle.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import io.github.cmdralph.fabricated.forge.particle.BlockDustTint;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.extensions.common.IClientBlockExtensions;

/**
 * A block's breaking particles are tinted only where Fabric mods allow it too ({@link BlockDustTint}): asked where Forge
 * decides the tint, which replaced the check upstream's hook looks for. The event is asked first, as upstream asks it
 * for every particle.
 */
@Mixin(TerrainParticle.class)
abstract class TerrainParticleTintMixin {
	@WrapOperation(method = "<init>(Lnet/minecraft/client/multiplayer/ClientLevel;DDDDDDLnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraftforge/client/extensions/common/IClientBlockExtensions;areBreakingParticlesTinted(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/core/BlockPos;)Z"))
	private boolean fabricated$allowBlockDustTint(IClientBlockExtensions extensions, BlockState state, ClientLevel level, BlockPos pos,
			Operation<Boolean> tinted) {
		return BlockDustTint.allowed(state, level, pos) && tinted.call(extensions, state, level, pos);
	}
}
