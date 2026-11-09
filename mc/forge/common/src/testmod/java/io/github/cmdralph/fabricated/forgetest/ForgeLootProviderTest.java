package io.github.cmdralph.fabricated.forgetest;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.github.cmdralph.fabricated.api.FabricApiModule;
import io.github.cmdralph.fabricated.api.Fabricated;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;

/**
 * Fabricated's native fabric-loot-api-v3 on Forge, when another mod provides the module (the stand-ins' run,
 * ci/forge/standins.py): Fabricated's hooks into Forge's loot table loading stand down with its copy of the module, so
 * none of the loading events may come from them. The stand-in has no hooks of its own.
 */
final class ForgeLootProviderTest {
	private static final Set<String> SEEN = ConcurrentHashMap.newKeySet();

	private ForgeLootProviderTest() {
	}

	static void register() {
		if (!providedElsewhere()) return;
		LootTableEvents.REPLACE.register((key, original, source, registries) -> {
			SEEN.add("replace");
			return null;
		});
		LootTableEvents.MODIFY.register((key, builder, source, registries) -> SEEN.add("modify"));
		LootTableEvents.ALL_LOADED.register((manager, registry) -> SEEN.add("all_loaded"));
	}

	/** At the first server tick. */
	static void check() {
		if (!providedElsewhere()) return;
		List<String> fired = SEEN.stream().sorted().toList();
		if (fired.isEmpty()) {
			ForgeTestMod.LOGGER.info("FABRICATED-TEST loot left to its provider OK");
		} else {
			ForgeTestMod.LOGGER.error("FABRICATED-TEST loot left to its provider FAILED: another mod provides fabric-loot-api-v3, yet {} fired", fired);
		}
	}

	private static boolean providedElsewhere() {
		return Fabricated.get().flatMap(api -> api.fabricApiModule("fabric-loot-api-v3"))
				.map(module -> module.status() == FabricApiModule.Status.PROVIDED_BY_MOD).orElse(false);
	}
}
