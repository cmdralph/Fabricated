package io.github.cmdralph.fabricated.forge.loot.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.serialization.DynamicOps;

import io.github.cmdralph.fabricated.forge.loot.LootBridge;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.ReloadableServerRegistries;

/**
 * Notes the registries a reload reads its loot tables with, which Fabric's loot events take: those the reload makes the
 * tables' serialization context from, as upstream finds them. 1.21.1's reload makes it from its own wrapper of the
 * registries, without their tags ({@code ReloadableServerRegistries.EmptyTagLookupWrapper}), where 1.21.11's takes a
 * provider with them. The wrapper is the class's own, so the handler takes it as the provider it is (@Coerce), as
 * upstream does.
 */
@Mixin(ReloadableServerRegistries.class)
abstract class LoadingRegistriesMixin {
	@WrapOperation(method = "reload", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/server/ReloadableServerRegistries$EmptyTagLookupWrapper;createSerializationContext(Lcom/mojang/serialization/DynamicOps;)Lnet/minecraft/resources/RegistryOps;"))
	private static <T> RegistryOps<T> fabricated$noteLoadingRegistries(@Coerce HolderLookup.Provider registries, DynamicOps<T> ops, Operation<RegistryOps<T>> original) {
		LootBridge.loadingRegistries(registries);
		return original.call(registries, ops);
	}
}
