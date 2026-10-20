package io.github.cmdralph.fabricated.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.cmdralph.fabricated.neoforge.fluid.FabricFluidType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FluidState;

/**
 * A living entity moves through a Fabric fluid that isn't water to it as through air, as on Fabric. NeoForge moves an
 * entity in any mod fluid with the fluid's own movement, which its fluid type leaves to the mod: none at all on 26.2
 * and later (the entity would hang where it is), lava's on 1.21.11.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityFluidTravelMixin {
	@Inject(method = "shouldTravelInFluid", at = @At("RETURN"), cancellable = true)
	private void fabricated$throughFabricFluid(FluidState fluid, CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (cir.getReturnValueZ() && !self.isInWater() && !self.isInLava() && FabricFluidType.leavesEntitiesAlone(fluid)) {
			cir.setReturnValue(false);
		}
	}
}
