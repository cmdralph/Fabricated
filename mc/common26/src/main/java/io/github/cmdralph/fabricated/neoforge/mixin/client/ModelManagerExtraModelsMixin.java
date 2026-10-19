package io.github.cmdralph.fabricated.neoforge.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.sugar.Local;

import io.github.cmdralph.fabricated.neoforge.rendering.FabricExtraModels;
import net.minecraft.client.resources.model.ModelDiscovery;
import net.minecraft.client.resources.model.ModelManager;

/** Fabric's extra models, in NeoForge's model discovery (see {@link FabricExtraModels}). */
@Mixin(ModelManager.class)
abstract class ModelManagerExtraModelsMixin {
	@Inject(method = "discoverModelDependencies(Ljava/util/Map;Lnet/minecraft/client/resources/model/BlockStateModelLoader$LoadedModels;Lnet/minecraft/client/resources/model/ClientItemInfoLoader$LoadedClientInfos;Lnet/neoforged/neoforge/client/model/standalone/StandaloneModelLoader$LoadedModels;)Lnet/minecraft/client/resources/model/ModelManager$ResolvedModels;",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/model/ModelDiscovery;resolve()Ljava/util/Map;"))
	private static void fabricated$fabricExtraModels(CallbackInfoReturnable<?> cir, @Local ModelDiscovery discovery) {
		FabricExtraModels.addRoots(discovery);
	}
}
