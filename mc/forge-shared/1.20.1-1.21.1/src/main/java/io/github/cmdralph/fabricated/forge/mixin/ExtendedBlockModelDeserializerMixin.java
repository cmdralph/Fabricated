package io.github.cmdralph.fabricated.forge.mixin;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import io.github.cmdralph.fabricated.forge.rendering.FabricModelLoaders;
import net.minecraftforge.client.model.ExtendedBlockModelDeserializer;
import net.minecraftforge.client.model.geometry.IUnbakedGeometry;

/**
 * Forge 47's and 52's block model reader reads a model's geometry only where {@link FabricModelLoaders} says Forge's
 * reading is the model's (CORPUS-92, CORPUS-102, MODEL-5): a Fabric mod's loader is left to that mod's own hook into
 * vanilla's reader, which the model came through, so the model keeps what the hook made of it, without Forge's
 * geometry. Only the model's own {@code loader} is read here; the parts of Forge's own composite models go through
 * Forge's reading as before.
 */
@Mixin(value = ExtendedBlockModelDeserializer.class, remap = false)
abstract class ExtendedBlockModelDeserializerMixin {
	@WrapOperation(method = "deserialize(Lcom/google/gson/JsonElement;Ljava/lang/reflect/Type;Lcom/google/gson/JsonDeserializationContext;)Lnet/minecraft/client/renderer/block/model/BlockModel;",
			at = @At(value = "INVOKE", target = "Lnet/minecraftforge/client/model/ExtendedBlockModelDeserializer;deserializeGeometry(Lcom/google/gson/JsonDeserializationContext;Lcom/google/gson/JsonObject;)Lnet/minecraftforge/client/model/geometry/IUnbakedGeometry;"))
	private IUnbakedGeometry<?> fabricated$fabricLoader(JsonDeserializationContext context, JsonObject json, Operation<IUnbakedGeometry<?>> read) {
		return FabricModelLoaders.forgeReads(json) ? read.call(context, json) : null;
	}
}
