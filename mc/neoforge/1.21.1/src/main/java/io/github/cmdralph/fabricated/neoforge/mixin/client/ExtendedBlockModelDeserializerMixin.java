package io.github.cmdralph.fabricated.neoforge.mixin.client;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import io.github.cmdralph.fabricated.core.metadata.ModIds;
import io.github.cmdralph.fabricated.loader.FabricatedState;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.model.ExtendedBlockModelDeserializer;
import net.neoforged.neoforge.client.model.geometry.GeometryLoaderManager;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;

/**
 * A model's {@code loader} key naming a Fabric mod's loader is left to that mod, as on Fabric, where the game ignores
 * the key (CORPUS-62). NeoForge's model deserializer reads {@code loader} as the name of one of its geometry loaders and
 * fails the model when it names none. Fabric mods that read the key do so in their own hook into vanilla's deserializer,
 * which NeoForge's deserializer runs first (Moonlight's loaders, which Supplementaries' rope knots, flower boxes, way
 * signs and others use): the model they returned was then thrown away, logged as "Model loader '...' not found", and
 * drawn as the missing model.
 *
 * <p>The key is left alone only when it names no loader NeoForge knows and its namespace is a Fabric mod's id. A loader
 * NeoForge knows is NeoForge's to read, and any other unknown loader still fails the model, as on NeoForge.
 */
@Mixin(ExtendedBlockModelDeserializer.class)
abstract class ExtendedBlockModelDeserializerMixin {
	@WrapOperation(method = "deserialize(Lcom/google/gson/JsonElement;Ljava/lang/reflect/Type;Lcom/google/gson/JsonDeserializationContext;)Lnet/minecraft/client/renderer/block/model/BlockModel;",
			at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/client/model/ExtendedBlockModelDeserializer;deserializeGeometry(Lcom/google/gson/JsonDeserializationContext;Lcom/google/gson/JsonObject;)Lnet/neoforged/neoforge/client/model/geometry/IUnbakedGeometry;"))
	private IUnbakedGeometry<?> fabricated$leaveFabricModsLoadersToThem(JsonDeserializationContext context, JsonObject json,
			Operation<IUnbakedGeometry<?>> original) {
		ResourceLocation loader = fabricated$loaderName(json);
		if (loader != null && GeometryLoaderManager.get(loader) == null
				&& FabricatedState.get().isFabricMod(ModIds.toNeoForgeId(loader.getNamespace()))) {
			return null;
		}
		return original.call(context, json);
	}

	/** The loader {@code json} names, in either of NeoForge's forms (a string, or an object's {@code id}); null if none. */
	@Unique
	private static @Nullable ResourceLocation fabricated$loaderName(JsonObject json) {
		JsonElement loader = json.get("loader");
		if (loader instanceof JsonObject object) loader = object.get("id");
		// Anything else is left to NeoForge, which reports it as it does.
		return loader instanceof JsonPrimitive name && name.isString() ? ResourceLocation.tryParse(name.getAsString()) : null;
	}
}
