package io.github.cmdralph.fabricated.forge.content;

import java.util.function.Predicate;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.crafting.conditions.ICondition;

/**
 * Fabric API's resource conditions where Forge 47 reads a recipe's or an advancement's {@code conditions} (CORPUS-97).
 * Supplementaries' Fabric build names its own condition there ({@code supplementaries:flag}) and registers it with
 * Fabric API's resource conditions; on Fabric, Moonlight's bridge tests such a Forge-style condition (its {@code type})
 * through the condition Fabric API has under that id, handing it the condition's JSON. Forge 47 reads {@code conditions}
 * itself first, knows only its own condition serializers, and refused 246 of the corpus server's recipes ("Unknown
 * condition type"). {@code CraftingHelperFabricConditionsMixin} asks here for a type Forge has no serializer of: Fabric
 * API's condition of that id, tested on the condition's JSON as Moonlight's bridge tests it, or null, and Forge refuses
 * the type as before.
 */
public final class FabricRecipeConditions {
	/** Fabric API's resource conditions module is installed. Its classes are only touched then. */
	private static final boolean ENABLED = FabricLoader.getInstance().isModLoaded("fabric-resource-conditions-api-v1");

	private FabricRecipeConditions() {
	}

	/**
	 * Fabric API's condition for a Forge-style condition whose type Forge has no serializer of, or null.
	 *
	 * @param forgeKnows whether Forge has a serializer of a type
	 */
	public static ICondition of(JsonObject json, Predicate<ResourceLocation> forgeKnows) {
		if (!ENABLED) return null;
		JsonElement type = json.get("type");
		if (type == null || !type.isJsonPrimitive() || !type.getAsJsonPrimitive().isString()) return null;
		ResourceLocation id = ResourceLocation.tryParse(type.getAsString());
		if (id == null || forgeKnows.test(id)) return null;
		Predicate<JsonObject> fabric = Upstream.get(id);
		return fabric == null ? null : new FabricCondition(id, fabric, json);
	}

	/** A Fabric API condition as Forge tests one: on the condition's JSON, whatever the context. */
	private record FabricCondition(ResourceLocation id, Predicate<JsonObject> fabric, JsonObject json) implements ICondition {
		@Override
		public ResourceLocation getID() {
			return id;
		}

		@Override
		public boolean test(IContext context) {
			return fabric.test(json);
		}
	}

	/** Everything that names Fabric's resource conditions module: loaded only once {@link #ENABLED} is known to be true. */
	private static final class Upstream {
		private Upstream() {
		}

		static Predicate<JsonObject> get(ResourceLocation id) {
			return ResourceConditions.get(id);
		}
	}
}
