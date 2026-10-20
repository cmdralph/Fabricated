package io.github.cmdralph.fabricated.neoforge.mixin;

import java.util.List;
import java.util.function.Supplier;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.neoforge.common.util.Lazy;

/**
 * Fabric API's biome modification API rebuilds a generator's features per step as Guava's memoized supplier, as
 * vanilla does, where NeoForge keeps its own {@link Lazy} and casts to it when it refreshes them (after its biome
 * modifiers). The features are rebuilt as NeoForge's again first, from the generator's current biomes.
 * {@code require = 0}: only needed while NeoForge declares {@code refreshFeaturesPerStep}; without it there is nothing
 * to keep.
 */
@Mixin(ChunkGenerator.class)
abstract class ChunkGeneratorFeaturesMixin {
	@Shadow
	@Final
	@Mutable
	private Supplier<List<FeatureSorter.StepFeatureData>> featuresPerStep;

	@Inject(method = "refreshFeaturesPerStep", at = @At("HEAD"), require = 0)
	private void fabricated$keepNeoForgesLazy(CallbackInfo ci) {
		if (featuresPerStep instanceof Lazy<?>) return;
		ChunkGenerator self = (ChunkGenerator) (Object) this;
		featuresPerStep = Lazy.of(() -> FeatureSorter.buildFeaturesPerStep(List.copyOf(self.getBiomeSource().possibleBiomes()),
				biome -> self.getBiomeGenerationSettings(biome).features(), true));
	}
}
