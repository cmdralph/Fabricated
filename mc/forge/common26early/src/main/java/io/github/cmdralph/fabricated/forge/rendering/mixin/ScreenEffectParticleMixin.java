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
import net.fabricmc.fabric.api.client.renderer.v1.model.FabricBlockStateModel;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The screen effect inside a block shows a Fabric model's level-aware particle material, as upstream's hook gives it
 * (PART-4), on Forge 64 and 65 (Forge 66 extracts it elsewhere: its own mixin of this name). Forge finds the block in a
 * method of its own, which returns it with its position, and upstream's hook reads the position from vanilla's lookup,
 * which the screen effect no longer calls: pre-flight left the hook out, and the effect showed the model's material
 * without the level. Fabricated keeps the block Forge found, with its position and level, for the material the effect
 * asks next (in 26.1.2's {@code renderScreenEffect}, 26.2's {@code submit}); every other model keeps Forge's call. On
 * the render thread only.
 */
@Mixin(ScreenEffectRenderer.class)
abstract class ScreenEffectParticleMixin {
	/** The block Forge's lookup last found, and the level it is in, until the screen effect asks its material. */
	@Unique
	private static @Nullable Pair<BlockState, BlockPos> fabricated$overlay;
	@Unique
	private static @Nullable Level fabricated$overlayLevel;

	@Inject(method = "getOverlayBlock", at = @At("RETURN"))
	private static void fabricated$keepOverlayBlock(Player player, CallbackInfoReturnable<@Nullable Pair<BlockState, BlockPos>> cir) {
		fabricated$overlay = cir.getReturnValue();
		fabricated$overlayLevel = fabricated$overlay == null ? null : player.level();
	}

	@WrapOperation(method = {"renderScreenEffect", "submit"}, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/block/BlockStateModelSet;getParticleMaterial(Lnet/minecraft/world/level/block/state/BlockState;)"
					+ "Lnet/minecraft/client/resources/model/sprite/Material$Baked;"))
	private Material.Baked fabricated$levelAwareMaterial(BlockStateModelSet models, BlockState state, Operation<Material.Baked> original) {
		Pair<BlockState, BlockPos> overlay = fabricated$overlay;
		Level level = fabricated$overlayLevel;
		fabricated$overlay = null;
		fabricated$overlayLevel = null;
		// The client level is the one Fabric's renderer API takes; the player's is it.
		if (overlay != null && overlay.getLeft() == state && level instanceof BlockAndTintGetter blocks) {
			BlockStateModel model = models.get(state);
			if (FabricBlockRendering.levelAwareParticle(model)) return ((FabricBlockStateModel) model).particleMaterial(blocks, overlay.getRight(), state);
		}
		return original.call(models, state);
	}
}
