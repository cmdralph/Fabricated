package io.github.cmdralph.fabricated.testmod.frapi.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.cmdralph.fabricated.testmod.frapi.FrapiCompat;
import net.minecraft.client.renderer.texture.AtlasSet;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Puts the renderer test mod's Fabric models in place of the baked ones, as a Fabric mod for 1.19.2 does without model
 * loading plugins: once the bakery has baked the top-level models (vanilla's {@code uploadTextures}), whose map the
 * model manager then draws from.
 */
@Mixin(ModelBakery.class)
abstract class ModelBakeryMixin {
	@Shadow
	@Final
	private Map<ResourceLocation, BakedModel> bakedTopLevelModels;

	@Inject(method = "uploadTextures", at = @At("RETURN"))
	private void fabricatedFrapiTest$replaceModels(TextureManager textureManager, ProfilerFiller profiler, CallbackInfoReturnable<AtlasSet> cir) {
		FrapiCompat.replace(bakedTopLevelModels);
	}
}
