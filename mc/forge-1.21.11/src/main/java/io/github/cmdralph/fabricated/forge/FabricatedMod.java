package io.github.cmdralph.fabricated.forge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.cmdralph.fabricated.loader.FabricatedState;
import net.minecraftforge.fml.common.Mod;

/**
 * Fabricated's in-game mod on Minecraft Forge. A scaffold: it reports whether discovery ran, which needs the
 * forgespi 8 adapter in {@code loader/forge} (docs/PORTING.md §7, F3).
 */
@Mod(FabricatedMod.MOD_ID)
public final class FabricatedMod {
	public static final String MOD_ID = "fabricated";
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");

	public FabricatedMod() {
		if (FabricatedState.get() == null) {
			LOGGER.warn("Fabricated's discovery did not run; Fabric mods are not loaded (Forge support is not finished)");
		}
	}
}
