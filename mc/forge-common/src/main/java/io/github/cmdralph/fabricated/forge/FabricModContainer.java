package io.github.cmdralph.fabricated.forge;

import io.github.cmdralph.fabricated.loader.ForgeLoaderHooks;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModLoadingException;
import net.minecraftforge.fml.ModLoadingStage;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.forgespi.language.ModFileScanData;

/**
 * Forge's container for one Fabric mod, which Fabricated's language provider creates by name through the game's class
 * loader (docs/PORTING.md §4.1). Fabric mods have no mod object and don't use Forge's mod bus; their entrypoints run
 * from Fabricated's in-game mod. The container connects the mod to its classes ({@link ForgeLoaderHooks#attach}).
 */
public final class FabricModContainer extends ModContainer {
	public FabricModContainer(IModInfo info, ModFileScanData scan, ModuleLayer gameLayer) {
		super(info);
		String moduleName = info.getOwningFile().moduleName();
		if (ForgeLoaderHooks.attach(info.getModId(), gameLayer, moduleName).isEmpty()) {
			throw new ModLoadingException(info, ModLoadingStage.CONSTRUCT, "fml.modloading.failedtoloadmodclass",
					new IllegalStateException("Mod " + info.getModId() + " declares the Fabricated loader but Fabricated didn't translate it, or its module "
							+ moduleName + " isn't in the game layer"));
		}
	}

	/** Fabric mods have no mod object. */
	@Override
	public boolean matches(Object mod) {
		return false;
	}

	@Override
	public Object getMod() {
		return null;
	}
}
