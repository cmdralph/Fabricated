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
 * {@code FluidRendererMixin.modSpriteArray}, at the method's neighbour fluid check ({@code isNeighborSameFluid}, which
 * it makes once, unconditionally), puts a Fabric fluid render handler's sprites in their place, which have no overlay
 * for a fluid without one (lava's, a Fabric mod's), so the client crashed meshing the first lava it saw. Right after
 * upstream's hook, at the same point (a higher priority: applied after it), an array without the slot gets an empty
 * one, which Forge reads as no overlay. A later point would be one the method can skip: its first neighbour occlusion
 * check is only made for a face it draws, so a fluid not drawing its bottom face kept upstream's array there.
 */
@Mixin(value = LiquidBlockRenderer.class, priority = 1100)
abstract class LiquidBlockRendererSpritesMixin {
	@ModifyVariable(method = "tesselate", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/LiquidBlockRenderer;"
			+ "isNeighborSameFluid(Lnet/minecraft/world/level/material/FluidState;Lnet/minecraft/world/level/material/FluidState;)Z"), ordinal = 0)
	private TextureAtlasSprite[] fabricated$overlaySlot(TextureAtlasSprite[] sprites) {
		return sprites.length > 2 ? sprites : Arrays.copyOf(sprites, 3);
	}
}
