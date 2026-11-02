package io.github.cmdralph.fabricated.forgetest;

import java.util.Map;

import net.fabricmc.fabric.api.registry.FuelRegistry;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_2609;

/**
 * The forge-legacy test mod's part for fabric-content-registries-v0's fuel registry (FUEL-1; with Fabric API's jar
 * only): the test item, registered as a fuel through {@code FuelRegistry}, must be in vanilla's fuel table
 * ({@code getFuel}, which upstream's hook adds Fabric's fuels to) and a fuel to the furnace ({@code isFuel}), which Forge
 * 47 answers from its own burn times, read from that table as the data packs' tags load.
 */
public final class ForgeFuelTest {
	/** Ticks: an odd value no vanilla fuel has. */
	static final int BURN_TIME = 1234;

	private ForgeFuelTest() {
	}

	/** From the main entrypoint, once the test item is registered; only with Fabric API's content registries module. */
	static void register() {
		FuelRegistry.INSTANCE.add(ForgeTestMod.testItem, BURN_TIME);
	}

	/** At the first server tick, the data packs' tags loaded. */
	public static void check() {
		Map<class_1792, Integer> table = class_2609.method_11196();
		boolean inTable = Integer.valueOf(BURN_TIME).equals(table.get(ForgeTestMod.testItem));
		boolean fuel = class_2609.method_11195(new class_1799(ForgeTestMod.testItem));
		if (inTable && fuel) {
			ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST fuel registry OK");
		} else {
			ForgeTestMod.LOGGER.error("FABRICATED-FORGE-TEST fuel registry FAILED: the test item is in vanilla's fuel table {} (burn time {}), a fuel to the furnace {}",
					inTable, table.get(ForgeTestMod.testItem), fuel);
		}
	}
}
