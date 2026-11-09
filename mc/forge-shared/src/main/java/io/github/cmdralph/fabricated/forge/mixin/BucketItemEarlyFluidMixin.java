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
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.registries.IForgeRegistry;

/**
 * A bucket made for a fluid not registered yet ({@link EarlyRegistryObjects}, CORPUS-73): Forge's patch of vanilla's
 * constructor, on every Forge version, looks the fluid's delegate up and threw. Such a bucket keeps the fluid itself;
 * one made for a registered fluid keeps Forge's delegate. The supplier is Forge's field, set last in the constructor.
 */
@Mixin(BucketItem.class)
abstract class BucketItemEarlyFluidMixin {
	@Shadow(remap = false)
	@Final
	@Mutable
	private Supplier<? extends Fluid> fluidSupplier;

	@WrapOperation(method = "<init>(Lnet/minecraft/world/level/material/Fluid;Lnet/minecraft/world/item/Item$Properties;)V", remap = false, at = @At(value = "INVOKE",
			target = "Lnet/minecraftforge/registries/IForgeRegistry;getDelegateOrThrow(Ljava/lang/Object;)Lnet/minecraft/core/Holder$Reference;", remap = false))
	private Holder.Reference<?> fabricated$delegateIfRegistered(IForgeRegistry<?> fluids, Object fluid, Operation<Holder.Reference<?>> getDelegate) {
		return EarlyRegistryObjects.delegate(fluids, fluid);
	}

	@Inject(method = "<init>(Lnet/minecraft/world/level/material/Fluid;Lnet/minecraft/world/item/Item$Properties;)V", remap = false, at = @At("TAIL"))
	private void fabricated$fluidItselfIfUnregistered(Fluid fluid, Item.Properties properties, CallbackInfo ci) {
		if (fluidSupplier == null) fluidSupplier = EarlyRegistryObjects.itself(fluid);
	}
}
