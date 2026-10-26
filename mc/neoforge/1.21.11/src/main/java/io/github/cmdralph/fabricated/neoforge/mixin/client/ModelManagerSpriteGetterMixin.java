package io.github.cmdralph.fabricated.neoforge.mixin.client;

import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.sugar.Local;

import io.github.cmdralph.fabricated.neoforge.rendering.FabricSpriteFinders;
import net.fabricmc.fabric.api.renderer.v1.model.SpriteFinder;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.SpriteGetter;
import net.minecraft.resources.Identifier;

/**
 * Gives the sprite getter models bake with Fabric's sprite finders (1.21.11), in place of upstream's mixin, which reads
 * the block and item stitch results from the fields javac named after the captured variables in Mojang's build
 * ({@code val$blockAtlas}). NeoForge compiles the game from decompiled sources, so those fields have other names there.
 * This takes the stitch results from the constructor's arguments instead and tells them apart by their atlas, so it
 * assumes no name.
 */
@Mixin(targets = "net.minecraft.client.resources.model.ModelManager$1")
abstract class ModelManagerSpriteGetterMixin implements SpriteGetter {
	@Shadow
	@Final
	private TextureAtlasSprite blockMissing;

	/** The stitch results this getter takes sprites from, by atlas. */
	@Unique
	private Map<Identifier, SpriteLoader.Preparations> fabricated$atlases;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void fabricated$keepAtlases(CallbackInfo ci, @Local(argsOnly = true, ordinal = 0) SpriteLoader.Preparations first,
			@Local(argsOnly = true, ordinal = 1) SpriteLoader.Preparations second) {
		fabricated$atlases = Map.of(first.missing().atlasLocation(), first, second.missing().atlasLocation(), second);
	}

	/** Fabric's {@code FabricErrorCollectingSpriteGetter.spriteFinder}, which only Fabric's renderer API declares and calls. */
	public SpriteFinder spriteFinder(Identifier atlas) {
		return FabricSpriteFinders.find(fabricated$atlases, atlas, blockMissing);
	}
}
