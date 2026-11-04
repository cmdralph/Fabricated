package net.fabricmc.fabric.api.resource;

import net.fabricmc.loader.api.ModContainer;
import net.minecraft.class_2960;

/** Stand-in for Fabric API 0.92.12's fabric-resource-loader-v0 interface, passed through, in Intermediary's names. */
public interface ResourceManagerHelper {
	static boolean registerBuiltinResourcePack(class_2960 id, ModContainer container, ResourcePackActivationType activationType) {
		throw new AssertionError("stand-in");
	}
}
