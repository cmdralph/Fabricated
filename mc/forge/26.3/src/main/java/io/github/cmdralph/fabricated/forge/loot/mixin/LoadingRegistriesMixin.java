package io.github.cmdralph.fabricated.forge.loot.mixin;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import io.github.cmdralph.fabricated.forge.loot.LootBridge;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.ReloadableServerRegistries;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Notes the registries a reload reads its loot tables with, which Fabric's loot events take: the reload's context
 * registries with its tags, as vanilla makes its own loading context of them ({@code loadingContextWithTags}).
 */
@Mixin(ReloadableServerRegistries.class)
abstract class LoadingRegistriesMixin {
	@WrapOperation(method = "reload", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/resources/RegistryDataLoader;load(Lnet/minecraft/server/packs/resources/ResourceManager;Ljava/util/List;Ljava/util/List;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
	private static CompletableFuture<RegistryAccess.Frozen> fabricated$noteLoadingRegistries(ResourceManager manager, List<HolderLookup.RegistryLookup<?>> contextRegistries,
			List<?> registriesToLoad, Executor executor, Operation<CompletableFuture<RegistryAccess.Frozen>> original) {
		LootBridge.loadingRegistries(HolderLookup.Provider.create(contextRegistries.stream()));
		return original.call(manager, contextRegistries, registriesToLoad, executor);
	}
}
