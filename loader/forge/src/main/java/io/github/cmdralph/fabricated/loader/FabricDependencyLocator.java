package io.github.cmdralph.fabricated.loader;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraftforge.fml.loading.moddiscovery.AbstractModProvider;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.forgespi.locating.IDependencyLocator;
import net.minecraftforge.forgespi.locating.IModFile;

/**
 * Fabricated's dependency locator on Forge (docs/PORTING.md §4.1), which Forge runs after every mod locator with the
 * files found so far. For now it hands Forge the in-game mod nested in the Fabricated jar, which loads in the game
 * layer; discovery and translation of the Fabric jars, and their files, follow (F3 step 3). In a development
 * workspace, where the in-game mod is already a mod of its own, it adds nothing.
 */
public final class FabricDependencyLocator extends AbstractModProvider implements IDependencyLocator {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	private static final String MOD_ID = "fabricated";

	@Override
	public String name() {
		return MOD_ID;
	}

	@Override
	public List<IModFile> scanMods(Iterable<IModFile> loadedMods) {
		for (IModFile file : loadedMods) {
			for (IModInfo mod : file.getModInfos()) {
				if (mod.getModId().equals(MOD_ID)) return List.of();
			}
		}
		try {
			var gameMod = createMod(NestedJars.extract(NestedJars.GAME_MOD));
			if (gameMod.file() != null) return List.of(gameMod.file());
			LOGGER.error("Forge refused Fabricated's in-game mod; Fabric mods will not be loaded this launch", gameMod.ex());
		} catch (IOException | RuntimeException e) {
			// A dependency locator's only error path stops the game (EarlyLoadingException): Fabric mods are given up instead.
			LOGGER.error("Fabricated could not hand Forge its in-game mod; Fabric mods will not be loaded this launch", e);
		}
		return List.of();
	}
}
