package net.fabricmc.fabric.api.resource.conditions.v1;

import java.util.function.Predicate;

import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;

/**
 * Compile-time stand-in: the members of Fabric API's 1.20.1 class that Fabricated's in-game mod calls, in Mojang's
 * names.
 */
public final class ResourceConditions {
	private ResourceConditions() {
	}

	/** The condition registered under this id, or null. */
	public static Predicate<JsonObject> get(ResourceLocation identifier) {
		throw new AssertionError("stand-in");
	}
}
