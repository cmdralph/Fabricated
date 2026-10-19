package io.github.cmdralph.fabricated.neoforge.rendering;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;

import net.fabricmc.fabric.api.client.model.loading.v1.UnbakedModelDeserializer;
import net.fabricmc.fabric.impl.client.model.loading.CustomUnbakedBlockStateModelRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;

/**
 * Fabric's custom model formats ({@code "fabric:type"}) in NeoForge's parsers, next to NeoForge's own ({@code "loader"}
 * in model files, {@code "type"} in blockstate files).
 *
 * <p>Upstream hooks vanilla's parsers, which NeoForge replaced: its model file deserializer is registered before
 * NeoForge's, which takes precedence, so Fabric's model types were read as plain models; its blockstate codecs replace
 * NeoForge's, so NeoForge's blockstate model types ({@code neoforge:composite}, and those NeoForge mods register) stopped
 * parsing. Here NeoForge's parsers stay, and only what names a Fabric type goes to Fabric.
 */
public final class FabricModelFormats {
	/** Fabric's model loading API is installed. Its classes are only touched then. */
	private static final boolean ENABLED = FabricLoader.getInstance().isModLoaded("fabric-model-loading-api-v1");
	private static final String TYPE_KEY = "fabric:type";

	private FabricModelFormats() {
	}

	/**
	 * The model a model file of a Fabric type describes, or null when it names none, or names an optional one nobody
	 * registered: NeoForge parses it then. Unknown types that aren't optional fail, as upstream.
	 */
	public static @Nullable UnbakedModel deserialize(JsonElement json, JsonDeserializationContext context) {
		if (!ENABLED || !(json instanceof JsonObject object) || !object.has(TYPE_KEY)) return null;
		return Fabric.deserialize(object, context);
	}

	/** NeoForge's blockstate model codec, also reading Fabric's custom blockstate models. */
	public static Codec<BlockStateModel.Unbaked> withFabricTypes(Codec<BlockStateModel.Unbaked> neoForge) {
		if (!ENABLED) return neoForge;
		return new Codec<>() {
			@Override
			public <T> DataResult<Pair<BlockStateModel.Unbaked, T>> decode(DynamicOps<T> ops, T input) {
				return (namesFabricType(ops, input) ? Fabric.blockStateCodec() : neoForge).decode(ops, input);
			}

			@Override
			public <T> DataResult<T> encode(BlockStateModel.Unbaked input, DynamicOps<T> ops, T prefix) {
				// Only data generators encode; NeoForge's codec refuses Fabric's custom models.
				DataResult<T> result = neoForge.encode(input, ops, prefix);
				return result.isError() ? Fabric.blockStateCodec().encode(input, ops, prefix) : result;
			}
		};
	}

	/** Whether a blockstate model, or one of its weighted variants, names a Fabric type. */
	private static <T> boolean namesFabricType(DynamicOps<T> ops, T input) {
		if (hasFabricType(ops, input)) return true;
		return ops.getStream(input).result().map(variants -> variants.anyMatch(variant -> hasFabricType(ops, variant))).orElse(false);
	}

	private static <T> boolean hasFabricType(DynamicOps<T> ops, T input) {
		return ops.getMap(input).result().map((MapLike<T> map) -> map.get(TYPE_KEY) != null).orElse(false);
	}

	/** Everything that names Fabric's model loading API: loaded only once {@link #ENABLED} is known to be true. */
	private static final class Fabric {
		/** As upstream's model file deserializer: the type is an id, or {@code {"id": ..., "optional": ...}}. */
		static @Nullable UnbakedModel deserialize(JsonObject object, JsonDeserializationContext context) {
			JsonElement type = object.get(TYPE_KEY);
			String id;
			boolean optional;
			if (type.isJsonPrimitive()) {
				id = type.getAsString();
				optional = false;
			} else if (type.isJsonObject()) {
				id = GsonHelper.getAsString(type.getAsJsonObject(), "id");
				optional = GsonHelper.getAsBoolean(type.getAsJsonObject(), "optional", false);
			} else {
				throw new JsonSyntaxException("Expected " + TYPE_KEY + " to be a string or object, was " + GsonHelper.getType(type));
			}
			UnbakedModelDeserializer deserializer = UnbakedModelDeserializer.get(Identifier.parse(id));
			if (deserializer != null) return deserializer.deserialize(object, context);
			if (!optional) throw new JsonParseException("Cannot deserialize custom unbaked model of unknown type '" + id + "'");
			return null;
		}

		/** Upstream's blockstate model codec (its custom types, and vanilla's variants in the same list). */
		static Codec<BlockStateModel.Unbaked> blockStateCodec() {
			return CustomUnbakedBlockStateModelRegistry.MODEL_CODEC;
		}
	}
}
