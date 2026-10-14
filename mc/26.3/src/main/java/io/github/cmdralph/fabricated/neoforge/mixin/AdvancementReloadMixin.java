package io.github.cmdralph.fabricated.neoforge.mixin;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import io.github.cmdralph.fabricated.neoforge.advancement.FabricAdvancementReload;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.ReloadableServerRegistries;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * {@code fabric-advancement-api-v1}'s reload hook on NeoForge 26.3, which loads the reloadable registries through its
 * own overload (with the updated tags) instead of the call upstream wraps; see {@link FabricAdvancementReload}.
 */
@Mixin(ReloadableServerRegistries.class)
abstract class AdvancementReloadMixin {
	@WrapOperation(method = "reload", at = @At(value = "INVOKE", target = "Lnet/minecraft/resources/RegistryDataLoader;load(Lnet/minecraft/server/packs/resources/ResourceManager;Ljava/util/List;Ljava/util/List;Ljava/util/concurrent/Executor;Ljava/util/List;)Ljava/util/concurrent/CompletableFuture;"))
	private static CompletableFuture<RegistryAccess.Frozen> fabricated$advancementReload(ResourceManager manager, List<HolderLookup.RegistryLookup<?>> context,
			List<RegistryDataLoader.RegistryData<?>> registries, Executor executor, List<Registry.PendingTags<?>> tags,
			Operation<CompletableFuture<RegistryAccess.Frozen>> original, @Local(name = "loadingContextWithTags") HolderLookup.Provider provider) {
		return FabricAdvancementReload.reload(manager, provider, () -> original.call(manager, context, registries, executor, tags));
	}
}
