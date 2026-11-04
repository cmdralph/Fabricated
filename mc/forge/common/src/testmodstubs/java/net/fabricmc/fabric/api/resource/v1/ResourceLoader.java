package net.fabricmc.fabric.api.resource.v1;

import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.resources.Identifier;

/** Stand-in for fabric-resource-loader-v1's {@code ResourceLoader}, for the test mod's built-in pack. Never packaged. */
public interface ResourceLoader {
	static boolean registerBuiltinPack(Identifier id, ModContainer container, PackActivationType activationType) {
		throw new AssertionError("stand-in");
	}
}
