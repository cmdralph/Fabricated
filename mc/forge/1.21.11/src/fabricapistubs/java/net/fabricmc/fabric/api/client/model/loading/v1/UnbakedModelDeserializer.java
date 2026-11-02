package net.fabricmc.fabric.api.client.model.loading.v1;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;

import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.Identifier;

/**
 * Compile-time stand-in: the members of Fabric API's 1.21.11 interface that Fabricated and its test mod call, in
 * Mojang's names.
 */
public interface UnbakedModelDeserializer {
	static void register(Identifier id, UnbakedModelDeserializer deserializer) {
		throw new AssertionError("stub");
	}

	static UnbakedModelDeserializer get(Identifier id) {
		throw new AssertionError("stub");
	}

	UnbakedModel deserialize(JsonObject jsonObject, JsonDeserializationContext context);
}
