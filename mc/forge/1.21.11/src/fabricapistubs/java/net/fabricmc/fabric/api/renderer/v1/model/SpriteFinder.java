package net.fabricmc.fabric.api.renderer.v1.model;

import net.fabricmc.fabric.api.renderer.v1.mesh.QuadView;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * Compile-time stand-in for Fabric API's 1.21.11 interface: Fabricated passes sprite finders along and finds a quad's
 * sprite with one.
 */
public interface SpriteFinder {
	default TextureAtlasSprite find(QuadView quad) {
		throw new AssertionError("stub");
	}
}
