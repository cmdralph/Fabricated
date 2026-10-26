package io.github.cmdralph.fabricated.neoforge.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.vertex.VertexConsumer;

import io.github.cmdralph.fabricated.neoforge.rendering.FabricFluidTint;
import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/** A Fabric fluid render handler's color, where NeoForge tints the fluid (see {@link FabricFluidTint}). */
@Mixin(LiquidBlockRenderer.class)
abstract class LiquidBlockTintMixin {
	@ModifyExpressionValue(method = "tesselate", at = @At(value = "INVOKE",
			target = "Lnet/neoforged/neoforge/client/extensions/common/IClientFluidTypeExtensions;getTintColor(Lnet/minecraft/world/level/material/FluidState;Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;)I"))
	private int fabricated$fabricFluidColor(int tint, BlockAndTintGetter level, BlockPos pos, VertexConsumer consumer, BlockState block, FluidState fluid) {
		return FabricFluidTint.apply(tint, level, pos, fluid);
	}
}
