package io.github.cmdralph.fabricated.neoforge.mixin.client;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.serialization.Codec;

import io.github.cmdralph.fabricated.neoforge.rendering.FabricModelFormats;
import net.minecraft.client.renderer.block.model.BlockStateModel;

/**
 * The blockstate model codec NeoForge builds (its own model types, {@code "type"}) also reads Fabric's custom blockstate
 * models ({@code "fabric:type"}). Upstream's mixin, which replaces the codec, is left out (fabric-api-plan.json); see
 * {@link FabricModelFormats}.
 */
@Mixin(BlockStateModel.Unbaked.class)
interface BlockStateModelUnbakedMixin {
	// The one flatComapMap after the weighted codec is assigned builds CODEC (as on 26.x, where the class moved to another package).
	@ModifyExpressionValue(method = "<clinit>", at = @At(value = "INVOKE", target = "Lcom/mojang/serialization/Codec;flatComapMap(Ljava/util/function/Function;Ljava/util/function/Function;)Lcom/mojang/serialization/Codec;"),
			slice = @Slice(from = @At(value = "FIELD", opcode = Opcodes.PUTSTATIC, target = "Lnet/minecraft/client/renderer/block/model/BlockStateModel$Unbaked;HARDCODED_WEIGHTED_CODEC:Lcom/mojang/serialization/Codec;")))
	private static Codec<BlockStateModel.Unbaked> fabricated$fabricTypes(Codec<BlockStateModel.Unbaked> codec) {
		return FabricModelFormats.withFabricTypes(codec);
	}
}
