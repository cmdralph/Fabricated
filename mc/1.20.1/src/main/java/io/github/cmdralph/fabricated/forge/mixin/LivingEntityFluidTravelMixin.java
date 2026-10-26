package io.github.cmdralph.fabricated.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import io.github.cmdralph.fabricated.forge.fluid.FabricFluidType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FluidState;

/**
 * A living entity moves through a Fabric fluid that isn't water to it as through air, as on Fabric. Forge 47's
 * {@code travel} moves an entity in any mod fluid as in water, unless the fluid type moves it itself (read in Forge 47's
 * {@code LivingEntity} patch): its two checks of the fluid type don't count such a fluid. The check is Forge's method,
 * which keeps its name in SRG's.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityFluidTravelMixin {
	@WrapOperation(method = "travel", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/LivingEntity;isInFluidType(Lnet/minecraft/world/level/material/FluidState;)Z", remap = false))
	private boolean fabricated$throughFabricFluid(LivingEntity self, FluidState fluid, Operation<Boolean> inFluidType) {
		return inFluidType.call(self, fluid) && !FabricFluidType.leavesEntitiesAlone(fluid);
	}
}
