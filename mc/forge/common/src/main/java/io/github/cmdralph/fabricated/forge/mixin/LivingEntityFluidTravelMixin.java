package io.github.cmdralph.fabricated.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.cmdralph.fabricated.forge.fluid.FabricFluidType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FluidState;

/**
 * A living entity moves through a Fabric fluid that isn't water to it as through air, as on Fabric. Forge moves an
 * entity in any mod fluid with the fluid type's own movement, which leaves it to lava's when the type has none (read in
 * Forge 61 to 66's {@code LivingEntity} patch).
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
