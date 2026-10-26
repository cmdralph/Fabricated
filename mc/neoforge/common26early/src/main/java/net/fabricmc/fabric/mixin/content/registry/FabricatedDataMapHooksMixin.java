package net.fabricmc.fabric.mixin.content.registry;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.fabricmc.fabric.api.registry.FuelValueEvents;
import net.fabricmc.fabric.impl.content.registry.FuelRegistryEventsContextImpl;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.FuelValues;
import net.neoforged.neoforge.common.DataMapHooks;

/**
 * Fabric's fuel events. NeoForge builds the game's fuel values from its furnace fuel data map instead of vanilla's
 * list (where upstream hooks), so the events run on that builder, after the data map's entries: {@code BUILD}, then
 * {@code EXCLUSIONS}.
 */
@Mixin(DataMapHooks.class)
abstract class FabricatedDataMapHooksMixin {
	@WrapOperation(method = "populateFuelValues", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/FuelValues$Builder;build()Lnet/minecraft/world/level/block/entity/FuelValues;"))
	private static FuelValues fabricated$fuelEvents(FuelValues.Builder builder, Operation<FuelValues> original,
			@Local(argsOnly = true) RegistryAccess registries, @Local(argsOnly = true) FeatureFlagSet features) {
		var context = new FuelRegistryEventsContextImpl(registries, features, AbstractFurnaceBlockEntity.BURN_TIME_STANDARD);
		FuelValueEvents.BUILD.invoker().build(builder, context);
		FuelValueEvents.EXCLUSIONS.invoker().buildExclusions(builder, context);
		return original.call(builder);
	}
}
