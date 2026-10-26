package io.github.cmdralph.fabricated.forgetest;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.minecraft.class_1299;
import net.minecraft.class_1959;
import net.minecraft.class_2378;
import net.minecraft.class_2960;
import net.minecraft.class_5321;
import net.minecraft.class_5455;
import net.minecraft.class_7923;
import net.minecraft.class_7924;

/**
 * The forge-legacy test mod's part for Fabricated's native fabric-biome-api-v1 on Forge 47 (L4): a fox spawn added to
 * plains and a changed temperature must reach the biome Forge reads, its modified info. Forge's coremod points the
 * biome's reads of its climate field there, so the temperature also shows the coremod applied: upstream's module,
 * passed through, widened that field and the coremod failed. Fabric API's biome classes and the game's are named as a
 * Fabric mod for 1.20.1 names them (the stand-ins in src/testmodstubs).
 */
public final class ForgeBiomeTest {
	// Plains' own is 0.8: close enough to change nothing that generates.
	private static final float TEMPERATURE = 0.81F;
	private static class_1299<?> fox;

	private ForgeBiomeTest() {
	}

	/** From the main entrypoint, as a Fabric mod adds its biome modifications. */
	public static void register() {
		class_2378<class_1299<?>> entityTypes = class_7923.field_41177;
		fox = entityTypes.method_10223(new class_2960("minecraft", "fox"));
		BiomeModifications.addSpawn(BiomeSelectors.includeByKey(plains()), fox.method_5891(), fox, 5, 1, 2);
		BiomeModifications.create(new class_2960(ForgeTestMod.MOD_ID, "warmer_plains"))
				.add(ModificationPhase.REPLACEMENTS, BiomeSelectors.includeByKey(plains()), context -> context.getWeather().setTemperature(TEMPERATURE));
	}

	/** Once the server runs, after Forge applied its biome modifiers. */
	public static void check(class_5455 registries) {
		class_1959 plains = registries.method_30530(class_7924.field_41236).method_31140(plains());
		List<String> failed = new ArrayList<>();
		if (fox == null || plains.method_30966().method_31004(fox.method_5891()).method_34994().stream().noneMatch(spawn -> spawn.field_9389 == fox)) {
			failed.add("spawn");
		}
		if (plains.method_8712() != TEMPERATURE) failed.add("temperature " + plains.method_8712());
		if (failed.isEmpty()) {
			ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST biome modifications OK");
		} else {
			ForgeTestMod.LOGGER.warn("FABRICATED-FORGE-TEST biome modifications: not in the biome Forge reads: {}", failed);
		}
	}

	private static class_5321<class_1959> plains() {
		return class_5321.method_29179(class_7924.field_41236, new class_2960("minecraft", "plains"));
	}
}
