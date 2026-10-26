package io.github.cmdralph.fabricated.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import io.github.cmdralph.fabricated.neoforge.fluid.FabricFluidType;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidType;

/**
 * A Fabric mod's fluid has a fluid type: NeoForge looks up vanilla's types for a fluid whose class doesn't name one,
 * and throws for any other fluid (see {@link FabricFluidType}). The fluid keeps the answer, so this runs once per fluid.
 */
@Mixin(Fluid.class)
abstract class FluidTypeMixin {
	@WrapOperation(method = "getFluidType", at = @At(value = "INVOKE",
			target = "Lnet/neoforged/neoforge/common/CommonHooks;getVanillaFluidType(Lnet/minecraft/world/level/material/Fluid;)Lnet/neoforged/neoforge/fluids/FluidType;"))
	private FluidType fabricated$fabricFluidType(Fluid fluid, Operation<FluidType> vanillaType) {
		FluidType type = FabricFluidType.forFluid(fluid);
		return type != null ? type : vanillaType.call(fluid);
	}
}
