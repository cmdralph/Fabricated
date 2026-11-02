package net.fabricmc.fabric.api.client.rendering.v1.hud;

import net.minecraft.resources.Identifier;

/**
 * Compile-time stand-in: the members of Fabric API's 1.21.11 interface that the forge test mod calls, in Mojang's names.
 */
public interface HudElementRegistry {
	static void attachElementBefore(Identifier beforeThis, Identifier identifier, HudElement element) {
		throw new AssertionError("stand-in");
	}

	static void attachElementAfter(Identifier afterThis, Identifier identifier, HudElement element) {
		throw new AssertionError("stand-in");
	}
}
