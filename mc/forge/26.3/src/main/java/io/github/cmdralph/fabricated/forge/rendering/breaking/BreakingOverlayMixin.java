package io.github.cmdralph.fabricated.forge.rendering.breaking;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;

import io.github.cmdralph.fabricated.forge.rendering.BreakingOverlay;
import io.github.cmdralph.fabricated.forge.rendering.FabricBlockRendering;
import io.github.cmdralph.fabricated.forge.rendering.FrapiFailures;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.state.level.BlockBreakingRenderState;
import net.minecraft.util.RandomSource;

/**
 * The block breaking overlay on 26.3: the renderer API's hook on this call draws each breaking block from its model's
 * emitter, unguarded. For a Fabric model (or a composite holding one) Fabricated draws it as that hook does, through
 * its guard ({@link BreakingOverlay}, FRAPI rule 8); every other model keeps the hook. This wraps the hook's call
 * (MixinExtras chains a wrapper around a redirected call), so the hook itself is untouched. Without the renderer API
 * the call is vanilla's, which it passes on. An allocation per breaking block per frame (MixinExtras's operation),
 * never per block of the world (rule 7).
 */
@Mixin(LevelRenderer.class)
abstract class BreakingOverlayMixin {
	@WrapOperation(method = "submitBlockDestroyAnimation(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;"
			+ "Lnet/minecraft/client/renderer/state/level/LevelRenderState;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitBreakingBlockModel(Lcom/mojang/blaze3d/vertex/PoseStack;Ljava/util/List;IZ)V"))
	private void fabricated$guard(SubmitNodeCollector collector, PoseStack poseStack, List<BlockStateModelPart> parts, int progress, boolean translucent, Operation<Void> original,
			@Local(name = "random") RandomSource random, @Local(name = "state") BlockBreakingRenderState state, @Local(name = "model") BlockStateModel model) {
		if (FabricBlockRendering.state(model) == FrapiFailures.NONE) {
			original.call(collector, poseStack, parts, progress, translucent);
		} else {
			BreakingOverlay.submit(collector, poseStack, parts, progress, translucent, random, state, model);
		}
	}
}
