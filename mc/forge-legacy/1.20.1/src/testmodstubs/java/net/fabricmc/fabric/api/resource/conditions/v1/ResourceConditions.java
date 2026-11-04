package net.fabricmc.fabric.api.resource.conditions.v1;

import java.util.function.Predicate;

import com.google.gson.JsonObject;

import net.minecraft.class_2960;

/** Stand-in for Fabric API 0.92.12's fabric-resource-conditions-api-v1 class, passed through, in Intermediary's names. */
public final class ResourceConditions {
	private ResourceConditions() {
	}

	public static void register(class_2960 identifier, Predicate<JsonObject> condition) {
		throw new AssertionError("stand-in");
	}
}
