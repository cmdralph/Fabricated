package io.github.cmdralph.fabricated.neoforge.rendering;

import java.util.Map;

import net.fabricmc.fabric.api.renderer.v1.model.SpriteFinder;
import net.fabricmc.fabric.api.renderer.v1.sprite.FabricStitchResult;
import net.fabricmc.fabric.impl.renderer.MissingSpriteFinderImpl;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;

/**
 * The sprite finders of the sprite getter models bake with, as upstream's {@code ModelManager1Mixin} gives them
 * (1.21.11): the finder of the stitch result being baked against for the block and item atlases, and one that only finds
 * the missing sprite for any other atlas. Only Fabric's renderer API asks, so this class, which names its types, is only
 * loaded when it is installed.
 */
public final class FabricSpriteFinders {
	private FabricSpriteFinders() {
	}

	public static SpriteFinder find(Map<Identifier, SpriteLoader.Preparations> atlases, Identifier atlas, TextureAtlasSprite missing) {
		SpriteLoader.Preparations preparations = atlases.get(atlas);
		return preparations != null ? ((FabricStitchResult) (Object) preparations).spriteFinder() : new MissingSpriteFinderImpl(missing);
	}
}
