package io.github.cmdralph.fabricated.forge.mixin;

import java.util.function.Supplier;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import io.github.cmdralph.fabricated.forge.EarlyRegistryObjects;
import net.minecraft.core.Holder;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.registries.IForgeRegistry;

/**
 * A liquid block made for a fluid not registered yet ({@link EarlyRegistryObjects}, CORPUS-73): Forge 47's patch of
 * vanilla's constructor looks the fluid's delegate up and threw (TechReborn makes its fluids' blocks before it
 * registers the fluids). Such a block keeps the fluid itself, as Forge 61's constructor does for every fluid; one made
 * for a registered fluid keeps Forge's delegate. The supplier is Forge's field, set last in the constructor.
 */
@Mixin(LiquidBlock.class)
abstract class LiquidBlockEarlyFluidMixin {
	@Shadow(remap = false)
	@Final
	@Mutable
	private Supplier<? extends Fluid> supplier;

	@WrapOperation(method = "<init>(Lnet/minecraft/world/level/material/FlowingFluid;Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;)V", remap = false,
			at = @At(value = "INVOKE", target = "Lnet/minecraftforge/registries/IForgeRegistry;getDelegateOrThrow(Ljava/lang/Object;)Lnet/minecraft/core/Holder$Reference;",
					remap = false))
	private Holder.Reference<?> fabricated$delegateIfRegistered(IForgeRegistry<?> fluids, Object fluid, Operation<Holder.Reference<?>> getDelegate) {
		return EarlyRegistryObjects.delegate(fluids, fluid);
	}

	@Inject(method = "<init>(Lnet/minecraft/world/level/material/FlowingFluid;Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;)V", remap = false,
			at = @At("TAIL"))
	private void fabricated$fluidItselfIfUnregistered(FlowingFluid fluid, BlockBehaviour.Properties properties, CallbackInfo ci) {
		if (supplier == null) supplier = EarlyRegistryObjects.itself(fluid);
	}
}
