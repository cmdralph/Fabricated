package io.github.cmdralph.fabricated.forgetest;

import io.github.cmdralph.fabricated.testmod.TestCompat;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

/**
 * The forge test mod's part for fabric-resource-conditions-api-v1 (with Fabric API's jar only): of its two advancements
 * (a JSON data loader's files) and two damage types (a data pack registry's entries), each pair with
 * {@code fabric:load_conditions}, the server must have loaded the {@code conditions_kept} ones, whose conditions hold,
 * and not the {@code conditions_dropped} ones, whose conditions name a mod that isn't installed, as on Fabric. Forge 52
 * and 61 decode registry entries through their own conditional decoder, and Forge 61's and 66's JSON data loaders hand
 * the data to lambdas that take more values than vanilla's, which upstream's hooks name.
 */
public final class ForgeConditionsTest {
	private static final Identifier KEPT = Identifier.fromNamespaceAndPath(ForgeTestMod.NAMESPACE, "conditions_kept");
	private static final Identifier DROPPED = Identifier.fromNamespaceAndPath(ForgeTestMod.NAMESPACE, "conditions_dropped");

	private ForgeConditionsTest() {
	}

	public static void check(MinecraftServer server) {
		boolean advancementKept = server.getAdvancements().get(KEPT) != null;
		boolean advancementDropped = server.getAdvancements().get(DROPPED) == null;
		var damageTypes = TestCompat.registry(server.registryAccess(), Registries.DAMAGE_TYPE);
		boolean entryKept = damageTypes.containsKey(KEPT);
		boolean entryDropped = !damageTypes.containsKey(DROPPED);
		report(advancementKept && advancementDropped, "data loader", "advancement", advancementKept, advancementDropped);
		report(entryKept && entryDropped, "registry", "damage type", entryKept, entryDropped);
	}

	private static void report(boolean ok, String check, String kind, boolean kept, boolean dropped) {
		if (ok) {
			ForgeTestMod.LOGGER.info("FABRICATED-TEST {} load conditions OK", check);
		} else {
			ForgeTestMod.LOGGER.error("FABRICATED-TEST {} load conditions FAILED: the {} whose conditions hold {}, the one whose conditions fail {}", check,
					kind, kept ? "loaded" : "didn't load", dropped ? "didn't load" : "loaded");
		}
	}
}
