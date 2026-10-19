package io.github.cmdralph.fabricated.neoforge.mixin;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.datafixers.util.Either;

import io.github.cmdralph.fabricated.neoforge.item.FabricRegistryEntries;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.resources.RegistryLoadTask;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceManagerRegistryLoadTask;
import net.minecraft.server.packs.resources.Resource;

/**
 * {@code fabric-item-api-v1}'s modify events for data pack registry entries (see {@link FabricRegistryEntries}), at the
 * point upstream runs them: as each entry is read from its file. A changed entry is no longer the known pack's own, so
 * it is sent to clients in full.
 */
@Mixin(ResourceManagerRegistryLoadTask.class)
abstract class ResourceManagerRegistryLoadTaskMixin {
	@Unique
	private volatile RegistryOps.@Nullable RegistryInfoLookup fabricated$registries;

	@Inject(method = "load", at = @At("HEAD"))
	private void fabricated$captureRegistries(RegistryOps.RegistryInfoLookup registries, Executor executor, CallbackInfoReturnable<CompletableFuture<?>> cir) {
		fabricated$registries = registries;
	}

	@WrapOperation(method = "lambda$load$2", at = @At(value = "NEW", target = "net/minecraft/resources/RegistryLoadTask$PendingRegistration"))
	private <T> RegistryLoadTask.PendingRegistration<T> fabricated$modify(ResourceKey<T> key, Either<T, Exception> value, RegistrationInfo info,
			Operation<RegistryLoadTask.PendingRegistration<T>> original, @Local(argsOnly = true) Resource resource) {
		RegistryOps.RegistryInfoLookup registries = fabricated$registries;
		T modified = registries == null ? null : value.left().map(entry -> FabricRegistryEntries.modify(key, entry, resource, registries)).orElse(null);
		if (modified != null) {
			value = Either.left(modified);
			info = new RegistrationInfo(Optional.empty(), info.lifecycle());
		}
		return original.call(key, value, info);
	}
}
