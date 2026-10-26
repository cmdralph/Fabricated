package io.github.cmdralph.fabricated.neoforge.mixin;

import java.util.concurrent.CompletableFuture;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;

import io.github.cmdralph.fabricated.neoforge.loot.LootBridge;
import io.github.cmdralph.fabricated.neoforge.loot.LootCompat;
import net.minecraft.server.ReloadableServerRegistries;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * For Fabric's loot events: notes which resource manager a reload of the loot tables reads from (for the tables'
 * sources), and fires {@code LootTableEvents.ALL_LOADED} once the reload's loot table registry is complete. The
 * reload's result is passed on unchanged.
 */
@Mixin(ReloadableServerRegistries.class)
abstract class ReloadableServerRegistriesMixin {
	@Inject(method = "reload", at = @At("HEAD"))
	private static void fabricated$startReload(CallbackInfoReturnable<?> cir, @Local(argsOnly = true) ResourceManager manager) {
		LootBridge.startReload(manager);
	}

	@ModifyReturnValue(method = "reload", at = @At("RETURN"))
	private static <T> CompletableFuture<T> fabricated$afterReload(CompletableFuture<T> future, @Local(argsOnly = true) ResourceManager manager) {
		return future.thenApply(result -> {
			LootBridge.allLoaded(manager, LootCompat.lootTables(result));
			return result;
		});
	}
}
