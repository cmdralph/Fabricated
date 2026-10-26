package io.github.cmdralph.fabricated.forge.loot.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.serialization.DynamicOps;

import io.github.cmdralph.fabricated.forge.loot.LootBridge;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.ReloadableServerRegistries;

/**
 * Notes the registries a reload reads its loot tables with (with the reload's tags), which Fabric's loot events take:
 * those the reload makes the tables' serialization context from, as upstream finds them.
 */
@Mixin(ReloadableServerRegistries.class)
abstract class LoadingRegistriesMixin {
	@WrapOperation(method = "reload", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/core/HolderLookup$Provider;createSerializationContext(Lcom/mojang/serialization/DynamicOps;)Lnet/minecraft/resources/RegistryOps;"))
	private static <T> RegistryOps<T> fabricated$noteLoadingRegistries(HolderLookup.Provider registries, DynamicOps<T> ops, Operation<RegistryOps<T>> original) {
		LootBridge.loadingRegistries(registries);
		return original.call(registries, ops);
	}
}
