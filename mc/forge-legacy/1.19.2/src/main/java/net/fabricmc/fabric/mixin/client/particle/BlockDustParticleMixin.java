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

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Slice;

import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import net.fabricmc.fabric.api.client.particle.v1.ParticleRenderEvents;

// Implements ParticleRenderEvents.ALLOW_BLOCK_DUST_TINT
@Mixin(TerrainParticle.class)
abstract class BlockDustParticleMixin extends TextureSheetParticle {
	@Shadow
	@Final
	private BlockPos pos;

	private BlockDustParticleMixin() {
		super(null, 0, 0, 0);
	}

	@ModifyVariable(
			method = "<init>(Lnet/minecraft/client/multiplayer/ClientLevel;DDDDDDLnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)V",
			at = @At("LOAD"),
			argsOnly = true,
			slice = @Slice(
					from = @At(value = "FIELD", target = "Lnet/minecraft/client/particle/TerrainParticle;bCol:F", ordinal = 0),
					to = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/world/level/block/Block;)Z")
			),
			allow = 1
	)
	private BlockState removeUntintableParticles(BlockState state) {
		if (!ParticleRenderEvents.ALLOW_BLOCK_DUST_TINT.invoker().allowBlockDustTint(state, level, pos)) {
			// As of 1.20.1, vanilla hardcodes grass block particles to not get tinted.
			return Blocks.GRASS_BLOCK.defaultBlockState();
		}

		return state;
	}
}
