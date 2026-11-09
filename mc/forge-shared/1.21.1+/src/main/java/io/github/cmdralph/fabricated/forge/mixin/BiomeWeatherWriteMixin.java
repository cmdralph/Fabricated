package io.github.cmdralph.fabricated.forge.mixin;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import io.github.cmdralph.fabricated.forge.biome.BiomeInfoClimate;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.common.world.ModifiableBiomeInfo;

/**
 * Fabric API's weather modifications (fabric-biome-api-v1, passed through on Forge) write {@code Biome.climateSettings},
 * which Forge keeps private for its coremod's getter, so the write threw {@code IllegalAccessError} as the server applied
 * the modifications and the server stopped (BIOME-3). The new climate goes into the field and into Forge's biome info,
 * whose climate Forge's getter returns: its original info, which Forge's biome modifiers start from, and its modified
 * info once they ran. Pseudo: Mixin skips it without a word when Fabric API's biome module isn't installed.
 */
@Pseudo
@Mixin(targets = "net.fabricmc.fabric.impl.biome.modification.BiomeModificationContextImpl$WeatherContextImpl", remap = false)
abstract class BiomeWeatherWriteMixin {
	@Redirect(method = {"setPrecipitation", "setTemperature", "setTemperatureModifier", "setDownfall"}, at = @At(value = "FIELD",
			target = "Lnet/minecraft/world/level/biome/Biome;climateSettings:Lnet/minecraft/world/level/biome/Biome$ClimateSettings;", opcode = Opcodes.PUTFIELD))
	private void fabricated$setClimate(Biome biome, Biome.ClimateSettings climate) {
		((BiomeClimateAccessor) (Object) biome).fabricated$setClimateSettings(climate);
		ModifiableBiomeInfo info = biome.modifiableBiomeInfo();
		ModifiableBiomeInfoAccessor infos = (ModifiableBiomeInfoAccessor) info;
		infos.fabricated$setOriginalBiomeInfo(BiomeInfoClimate.withClimate(info.getOriginalBiomeInfo(), climate));
		if (info.getModifiedBiomeInfo() != null) {
			infos.fabricated$setModifiedBiomeInfo(BiomeInfoClimate.withClimate(info.getModifiedBiomeInfo(), climate));
		}
	}
}
