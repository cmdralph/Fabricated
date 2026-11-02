package net.fabricmc.fabric.api.renderer.v1.sprite;

import net.fabricmc.fabric.api.renderer.v1.model.SpriteFinder;

/**
 * Compile-time stand-in: the member of Fabric API's 1.21.11 interface (injected into {@code SpriteLoader.Preparations})
 * that Fabricated calls.
 */
public interface FabricStitchResult {
	SpriteFinder spriteFinder();
}
