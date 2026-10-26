package io.github.cmdralph.fabricated.forge.loot.mixin;

import java.util.concurrent.CompletableFuture;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import io.github.cmdralph.fabricated.forge.loot.LootBridge;
import net.minecraft.server.ReloadableServerRegistries;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * For Fabric's loot events: notes which resource manager a reload of the loot tables reads from (for the tables'
 * sources), and fires {@code LootTableEvents.ALL_LOADED} once the reload's loot table registry is complete. The
 * reload's result is passed on unchanged. Forge doesn't patch {@code ReloadableServerRegistries}.
 *
 * <p>No MixinExtras sugar ({@code @Local}): without Fabric API installed, Forge's MixinExtras couldn't load the Mixin
 * class its {@code @Local} needs ({@code InvalidImplicitDiscriminatorException}) and the server didn't start. The
 * resource manager, the method's only one, is taken with Mixin's own {@code @ModifyVariable} instead.
 */
@Mixin(ReloadableServerRegistries.class)
abstract class ReloadableServerRegistriesMixin {
	@ModifyVariable(method = "reload", at = @At("HEAD"), argsOnly = true)
	private static ResourceManager fabricated$startReload(ResourceManager manager) {
		LootBridge.startReload(manager);
		return manager;
	}

	@ModifyReturnValue(method = "reload", at = @At("RETURN"))
	private static <T> CompletableFuture<T> fabricated$afterReload(CompletableFuture<T> future) {
		// The method has run from its start, on this thread: the manager it noted is this reload's.
		ResourceManager manager = LootBridge.reloadingManager();
		return future.thenApply(result -> {
			LootBridge.allLoaded(manager, result);
			return result;
		});
	}
}
