package io.github.cmdralph.fabricated.forge.resource.mixin;

import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.serialization.Decoder;

import io.github.cmdralph.fabricated.forge.resource.FabricResourceConditions;
import net.minecraftforge.common.crafting.conditions.ConditionCodec;

/** Forge's conditional decoder also honours Fabric's {@code fabric:load_conditions} (see {@link FabricResourceConditions}). */
@Mixin(ConditionCodec.class)
abstract class ConditionCodecMixin {
	@ModifyReturnValue(method = "wrap(Lcom/mojang/serialization/Decoder;)Lcom/mojang/serialization/Decoder;", at = @At("RETURN"))
	private static <T> Decoder<Optional<T>> fabricated$fabricConditions(Decoder<Optional<T>> forge) {
		return FabricResourceConditions.wrap(forge);
	}
}
