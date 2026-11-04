package io.github.cmdralph.fabricated.forgetest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.fabricmc.fabric.api.registry.FuelRegistry;
import net.minecraft.class_1799;
import net.minecraft.class_2609;

/**
 * The forge-legacy test mod's part for fabric-content-registries-v0's fuel registry (FUEL-1; with Fabric API's jar
 * only): the test item, registered as a fuel through {@code FuelRegistry}, must be in vanilla's fuel table
 * ({@code getFuel}, which Fabricated's in-game mod adds Fabric's fuels to, as upstream's hook, left out on Forge 47, did)
 * and a fuel to the furnace ({@code isFuel}), which Forge 47 answers from its own burn times, read from that table as
 * the data packs' tags load. Where another mod provides the module (Forgified Fabric API), the table is that mod's to
 * fill ({@link ForgeTestMod#report}).
 */
public final class ForgeFuelTest {
	/** Ticks: an odd value no vanilla fuel has. */
	static final int BURN_TIME = 1234;
	private static final String NOT_IN_TABLE = "the test item isn't in vanilla's fuel table";

	private ForgeFuelTest() {
	}

	/** From the main entrypoint, once the test item is registered; only with Fabric API's content registries module. */
	static void register() {
		FuelRegistry.INSTANCE.add(ForgeTestMod.testItem, BURN_TIME);
	}

	/** At the first server tick, the data packs' tags loaded. */
	public static void check() {
		Integer burnTime = class_2609.method_11196().get(ForgeTestMod.testItem);
		List<String> failed = new ArrayList<>();
		if (burnTime == null) failed.add(NOT_IN_TABLE);
		else if (burnTime != BURN_TIME) failed.add("vanilla's fuel table gives the test item " + burnTime + " ticks");
		if (!class_2609.method_11195(new class_1799(ForgeTestMod.testItem))) failed.add("the test item isn't a fuel to the furnace");
		ForgeTestMod.report("fuel registry", failed, "fabric-content-registries-v0", Map.of(NOT_IN_TABLE,
				"its FuelRegistry gives burn times through Forge's FurnaceFuelBurnTimeEvent, not vanilla's fuel table"));
	}
}
