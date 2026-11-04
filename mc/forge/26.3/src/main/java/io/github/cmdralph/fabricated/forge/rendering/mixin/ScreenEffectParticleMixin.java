package io.github.cmdralph.fabricated.forge.rendering.mixin;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import io.github.cmdralph.fabricated.forge.rendering.FabricBlockRendering;
import net.fabricmc.fabric.api.client.renderer.v1.model.FabricBlockStateModel;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The screen effect inside a block shows a Fabric model's level-aware particle material, as upstream's hook gives it
 * (PART-4), on Forge 66, which extracts it with the player's state. Forge finds the block in a method of its own, and
 * upstream's hook reads the position from the view blocking check of vanilla's lookup, which the extraction no longer
 * calls: pre-flight left the hook out, and the effect showed the model's material without the level. Fabricated keeps
 * the block that check passes in Forge's method, with its position and level, for the material the extraction asks
 * next (Forge's method returns the first block it passes); every other model keeps Forge's call. On the render thread
 * only.
 */
@Mixin(LevelExtractor.class)
abstract class ScreenEffectParticleMixin {
	/** The block the view blocking check last passed in Forge's lookup, until the extraction asks its material. */
	@Unique
	private static @Nullable BlockState fabricated$overlayState;
	@Unique
	private static @Nullable BlockPos fabricated$overlayPos;
	@Unique
	private static @Nullable BlockGetter fabricated$overlayLevel;

	@WrapOperation(method = "getOverlayBlock", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/state/BlockState;isViewBlocking(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/AABB;)Z"))
	private static boolean fabricated$keepOverlayBlock(BlockState state, BlockGetter level, BlockPos pos, AABB bounds, Operation<Boolean> original) {
		boolean blocking = original.call(state, level, pos, bounds);
		if (blocking) {
			fabricated$overlayState = state;
			fabricated$overlayPos = pos.immutable();
			fabricated$overlayLevel = level;
		}
		return blocking;
	}

	@WrapOperation(method = "extractPlayerState", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/block/BlockStateModelSet;getParticleMaterial(Lnet/minecraft/world/level/block/state/BlockState;)"
					+ "Lnet/minecraft/client/resources/model/sprite/Material$Baked;"))
	private Material.Baked fabricated$levelAwareMaterial(BlockStateModelSet models, BlockState state, Operation<Material.Baked> original) {
		BlockState found = fabricated$overlayState;
		BlockPos pos = fabricated$overlayPos;
		BlockGetter level = fabricated$overlayLevel;
		fabricated$overlayState = null;
		fabricated$overlayPos = null;
		fabricated$overlayLevel = null;
		// The client level is the one Fabric's renderer API takes; the player's is it.
		if (found == state && pos != null && level instanceof BlockAndTintGetter blocks) {
			BlockStateModel model = models.get(state);
			if (FabricBlockRendering.levelAwareParticle(model)) return ((FabricBlockStateModel) model).particleMaterial(blocks, pos, state);
		}
		return original.call(models, state);
	}
}
