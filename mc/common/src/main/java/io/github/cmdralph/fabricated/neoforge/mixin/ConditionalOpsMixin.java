package io.github.cmdralph.fabricated.neoforge.mixin;

import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.serialization.Codec;

import io.github.cmdralph.fabricated.neoforge.resource.FabricResourceConditions;
import net.neoforged.neoforge.common.conditions.ConditionalOps;

/** NeoForge's conditional codecs also honour Fabric's {@code fabric:load_conditions} (see {@link FabricResourceConditions}). */
@Mixin(ConditionalOps.class)
abstract class ConditionalOpsMixin {
	@ModifyReturnValue(method = "createConditionalCodec(Lcom/mojang/serialization/Codec;Ljava/lang/String;)Lcom/mojang/serialization/Codec;", at = @At("RETURN"))
	private static <T> Codec<Optional<T>> fabricated$fabricConditions(Codec<Optional<T>> neoForge) {
		return FabricResourceConditions.wrap(neoForge);
	}
}
