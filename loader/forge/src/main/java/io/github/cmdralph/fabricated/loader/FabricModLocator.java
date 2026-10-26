package io.github.cmdralph.fabricated.loader;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraftforge.fml.loading.moddiscovery.AbstractJarFileModProvider;
import net.minecraftforge.forgespi.locating.IModLocator;

/**
 * Fabricated's mod locator on Forge (docs/PORTING.md §4.1). Forge finds the Fabricated jar by its service files and
 * leaves it out of the mods folder's scan, so nothing nested in it is seen unless a locator hands it over: this one
 * hands Forge the language provider, which Forge loads into its plugin layer by its manifest. Fabric jars need no
 * claiming: Forge's mods folder locator skips a jar without {@code META-INF/mods.toml} or {@code FMLModType}.
 */
public final class FabricModLocator extends AbstractJarFileModProvider implements IModLocator {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");

	@Override
	public String name() {
		return "fabricated";
	}

	/**
	 * Forge 47 leaves this to each provider (Forge 61's base class has it): no arguments are read. The class scan of the
	 * files it hands over, which finds the in-game mod's {@code @Mod} class, is {@link AbstractJarFileModProvider}'s.
	 */
	@Override
	public void initArguments(Map<String, ?> arguments) {
	}

	@Override
	public List<ModFileOrException> scanMods() {
		try {
			return List.of(createMod(NestedJars.extract(NestedJars.LANG_PROVIDER)));
		} catch (IOException | RuntimeException e) {
			// A locator's only error path stops the game (EarlyLoadingException): Fabric mods are given up instead.
			LOGGER.error("Fabricated could not hand Forge its language provider; Fabric mods will not be loaded this launch", e);
			return List.of();
		}
	}
}
