package io.github.cmdralph.fabricated.testmod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

/**
 * CI test mod part for the energy bridge, when a run has Team Reborn's energy library: Fabricated doesn't bridge it to
 * Forge's energy capability yet (COMPATIBILITY.md, the support matrix), so a Fabric mod's energy reaches Fabric mods only.
 */
public final class EnergyTest implements ModInitializer {
	@Override
	public void onInitialize() {
		if (FabricLoader.getInstance().isModLoaded("team_reborn_energy")) {
			TestMod.LOGGER.info("FABRICATED-TEST energy SKIPPED: Fabricated has no bridge of Team Reborn's energy to Forge's energy capability yet");
		}
	}
}
