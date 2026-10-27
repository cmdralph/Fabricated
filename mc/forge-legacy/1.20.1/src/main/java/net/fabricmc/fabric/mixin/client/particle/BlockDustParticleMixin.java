/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.fabric.mixin.client.particle;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.extensions.common.IClientBlockExtensions;

import net.fabricmc.fabric.api.client.particle.v1.ParticleRenderEvents;

/**
 * Implements ParticleRenderEvents.ALLOW_BLOCK_DUST_TINT.
 *
 * <p>Fabricated: upstream turns the block state vanilla's constructor checks against grass into grass's when a callback
 * forbids the tint. Forge 47 replaced that check with its block extensions' {@code areBreakingParticlesTinted} (whose
 * default is vanilla's check), so upstream's hook found no check there: this one wraps Forge's instead, the event
 * asked first, as upstream asks it for every particle. The method is Forge's, named as it is at runtime too.
 */
@Mixin(TerrainParticle.class)
abstract class BlockDustParticleMixin {
	@WrapOperation(
			method = "<init>(Lnet/minecraft/client/multiplayer/ClientLevel;DDDDDDLnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraftforge/client/extensions/common/IClientBlockExtensions;areBreakingParticlesTinted(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/core/BlockPos;)Z", remap = false),
			allow = 1
	)
	private boolean removeUntintableParticles(IClientBlockExtensions extensions, BlockState state, ClientLevel world, BlockPos pos, Operation<Boolean> original) {
		if (!ParticleRenderEvents.ALLOW_BLOCK_DUST_TINT.invoker().allowBlockDustTint(state, world, pos)) {
			return false;
		}

		return original.call(extensions, state, world, pos);
	}
}
