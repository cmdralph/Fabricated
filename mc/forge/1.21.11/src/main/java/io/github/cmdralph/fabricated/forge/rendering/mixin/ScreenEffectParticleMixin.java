package io.github.cmdralph.fabricated.forge.rendering.mixin;

import org.apache.commons.lang3.tuple.Pair;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import io.github.cmdralph.fabricated.forge.rendering.FabricBlockRendering;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBlockStateModel;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The screen effect inside a block shows a Fabric model's level-aware particle sprite, as upstream's hook gives it
 * (PART-4). Forge 61 finds the block in a method of its own, which returns it with its position, and upstream's hook
 * reads the position from vanilla's lookup, which the screen effect no longer calls: pre-flight left the hook out, and
 * the effect showed the model's sprite without the level. Fabricated keeps the block Forge found, with its position and
 * level, for the sprite the effect asks next; every other model keeps Forge's call. On the render thread only.
 */
@Mixin(ScreenEffectRenderer.class)
abstract class ScreenEffectParticleMixin {
	/** The block Forge's lookup last found, and the level it is in, until the screen effect asks its sprite. */
	@Unique
	private static @Nullable Pair<BlockState, BlockPos> fabricated$overlay;
	@Unique
	private static @Nullable Level fabricated$overlayLevel;

	@Inject(method = "getOverlayBlock", at = @At("RETURN"))
	private static void fabricated$keepOverlayBlock(Player player, CallbackInfoReturnable<@Nullable Pair<BlockState, BlockPos>> cir) {
		fabricated$overlay = cir.getReturnValue();
		fabricated$overlayLevel = fabricated$overlay == null ? null : player.level();
	}

	@WrapOperation(method = "renderScreenEffect", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/block/BlockModelShaper;getParticleIcon(Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;"))
	private TextureAtlasSprite fabricated$levelAwareSprite(BlockModelShaper models, BlockState state, Operation<TextureAtlasSprite> original) {
		Pair<BlockState, BlockPos> overlay = fabricated$overlay;
		Level level = fabricated$overlayLevel;
		fabricated$overlay = null;
		fabricated$overlayLevel = null;
		if (overlay != null && overlay.getLeft() == state && level != null) {
			BlockStateModel model = models.getBlockModel(state);
			if (FabricBlockRendering.levelAwareParticle(model)) return ((FabricBlockStateModel) model).particleSprite(level, overlay.getRight(), state);
		}
		return original.call(models, state);
	}
}
