package io.github.cmdralph.fabricated.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidType;

/**
 * An entity's fluid lookups by tag answer for any tag, as vanilla's do (FLUID-2). NeoForge's build (26.2 and later)
 * tracks the fluids an entity is in by fluid type, and its lookups by tag ({@code Entity.isEyeInFluid},
 * {@code EntityFluidInteraction.getFluidHeight}, {@code isInFluid}, {@code applyCurrentTo}) throw for any tag but water's
 * and lava's, where vanilla answers no for a tag it doesn't track. Fabric mods ask with their own fluids' tags: Biomes
 * O' Plenty's screen overlay asks whether the player's eyes are in its blood, which stopped the 26.2 advanced client.
 * Such a tag stands for its fluids' type, or for no type when they have none or several. Injects at the start of
 * {@code getFluidTypeByTag} only; {@code @Pseudo}, as 26.1 has no such class.
 */
@Pseudo
@Mixin(targets = "net.minecraft.world.entity.EntityFluidInteraction")
abstract class EntityFluidInteractionTagMixin {
	@Inject(method = "getFluidTypeByTag", at = @At("HEAD"), cancellable = true)
	private static void fabricated$typeOfModTag(TagKey<Fluid> tag, CallbackInfoReturnable<FluidType> cir) {
		if (tag == FluidTags.WATER || tag == FluidTags.LAVA) return;
		FluidType type = null;
		for (Holder<Fluid> fluid : BuiltInRegistries.FLUID.getTagOrEmpty(tag)) {
			FluidType own = fluid.value().getFluidType();
			if (type != null && own != type) {
				type = null;
				break;
			}
			type = own;
		}
		cir.setReturnValue(type != null ? type : NeoForgeMod.EMPTY_TYPE.value());
	}
}
