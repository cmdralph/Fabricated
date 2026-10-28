package io.github.cmdralph.fabricated.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import io.github.cmdralph.fabricated.neoforge.fluid.FabricFluidType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FluidState;

/**
 * A living entity moves through a Fabric fluid that isn't water to it as through air, as on Fabric (Minecraft 1.21.1).
 * NeoForge 21.1 moves an entity in any mod fluid but lava as in water, unless the fluid type moves it itself.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityFluidTravelMixin {
	@WrapOperation(method = "travel", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/LivingEntity;isInFluidType(Lnet/minecraft/world/level/material/FluidState;)Z"))
	private boolean fabricated$throughFabricFluid(LivingEntity self, FluidState fluid, Operation<Boolean> original) {
		return original.call(self, fluid) && !FabricFluidType.leavesEntitiesAlone(fluid);
	}
}
