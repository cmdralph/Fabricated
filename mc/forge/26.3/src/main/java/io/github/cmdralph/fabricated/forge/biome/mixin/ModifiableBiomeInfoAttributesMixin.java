package io.github.cmdralph.fabricated.forge.biome.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.Holder;
import net.minecraft.world.attribute.EnvironmentAttributeMap;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.common.world.ModifiableBiomeInfo;
import net.minecraftforge.common.world.ModifiableBiomeInfo.BiomeInfo;

/**
 * The environment attributes a mod gave a biome before Forge 66 applies its biome modifiers stay the biome's (BIOME-4).
 * Forge 66 keeps a biome's mob spawns, with its other environment attributes, in the biome info it builds the biome
 * from, and its modifiers start from the info the biome was made with, then write the info they built back into the
 * biome. Fabric API's biome modifications (a spawn, a colour, any attribute) write the biome's attributes as the server
 * is created, before that: Forge's write put the attributes the biome was made with back. Forge's modifiers now start
 * from the attributes the biome has, as they do on Forge 61 to 65, whose biome info holds the spawn settings Fabric API
 * changes in place.
 */
@Mixin(value = ModifiableBiomeInfo.class, remap = false)
abstract class ModifiableBiomeInfoAttributesMixin {
	@Shadow
	@Final
	@Mutable
	private BiomeInfo originalBiomeInfo;

	@Inject(method = "applyBiomeModifiers", at = @At("HEAD"))
	private void fabricated$keepAttributes(Holder<Biome> biome, List<BiomeModifier> modifiers, CallbackInfo ci) {
		BiomeInfo original = originalBiomeInfo;
		EnvironmentAttributeMap attributes = biome.value().getAttributes();
		if (attributes != original.attributes()) {
			originalBiomeInfo = new BiomeInfo(original.climateSettings(), attributes, original.effects(), original.generationSettings());
		}
	}
}
