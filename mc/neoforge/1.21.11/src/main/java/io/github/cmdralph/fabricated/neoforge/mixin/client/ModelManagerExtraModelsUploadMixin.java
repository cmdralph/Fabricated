package io.github.cmdralph.fabricated.neoforge.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import io.github.cmdralph.fabricated.neoforge.rendering.FabricBakedExtraModels;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelManager;

/** Fabric's baked extra models, handed to the model manager as NeoForge's bake event starts (see {@link FabricBakedExtraModels}). */
@Mixin(ModelManager.class)
abstract class ModelManagerExtraModelsUploadMixin {
	@WrapOperation(method = "apply", at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/client/ClientHooks;onModelBake(Lnet/minecraft/client/resources/model/ModelManager;Lnet/minecraft/client/resources/model/ModelBakery$BakingResult;Lnet/minecraft/client/resources/model/ModelBakery;)V"))
	private void fabricated$fabricExtraModels(ModelManager manager, ModelBakery.BakingResult result, ModelBakery bakery, Operation<Void> original) {
		FabricBakedExtraModels.handOver(manager, result);
		original.call(manager, result, bakery);
	}
}
