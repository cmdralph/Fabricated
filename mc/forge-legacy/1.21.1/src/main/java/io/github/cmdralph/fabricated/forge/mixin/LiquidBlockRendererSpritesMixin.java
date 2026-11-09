package io.github.cmdralph.fabricated.forge.mixin;

import java.util.Arrays;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * Gives a fluid's sprites the overlay slot Forge 52's fluid renderer reads (FLUID-4). Forge's {@code tesselate} takes a
 * fluid's still, flowing and overlay sprites from its fluid type, and asks the third for a side's overlay; upstream's
 * {@code FluidRendererMixin.modSpriteArray}, at the method's first neighbour check, puts a Fabric fluid render
 * handler's sprites in their place, which have no overlay for a fluid without one (lava's, a Fabric mod's), so the
 * client crashed meshing the first lava it saw. Just after upstream's hook, an array without the slot gets an empty
 * one, which Forge reads as no overlay.
 */
@Mixin(LiquidBlockRenderer.class)
abstract class LiquidBlockRendererSpritesMixin {
	@ModifyVariable(method = "tesselate", at = @At(value = "INVOKE", ordinal = 0, target = "Lnet/minecraft/client/renderer/block/LiquidBlockRenderer;"
			+ "isFaceOccludedByNeighbor(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;F"
			+ "Lnet/minecraft/world/level/block/state/BlockState;)Z"), ordinal = 0)
	private TextureAtlasSprite[] fabricated$overlaySlot(TextureAtlasSprite[] sprites) {
		return sprites.length > 2 ? sprites : Arrays.copyOf(sprites, 3);
	}
}
