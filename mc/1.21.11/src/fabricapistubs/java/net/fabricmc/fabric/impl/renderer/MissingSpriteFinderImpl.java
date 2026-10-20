package net.fabricmc.fabric.impl.renderer;

import net.fabricmc.fabric.api.renderer.v1.model.SpriteFinder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * Compile-time stand-in for Fabric API's 1.21.11 sprite finder of an atlas models don't use: it finds the missing sprite.
 */
public record MissingSpriteFinderImpl(TextureAtlasSprite missingSprite) implements SpriteFinder {
}
