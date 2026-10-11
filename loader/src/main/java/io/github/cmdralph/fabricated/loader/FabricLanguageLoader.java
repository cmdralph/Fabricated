package io.github.cmdralph.fabricated.loader;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoadingException;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.language.IModLanguageLoader;
import net.neoforged.neoforgespi.language.ModFileScanData;

/**
 * NeoForge language loader for translated Fabric mods ({@code modLoader="fabricated"} in the generated
 * {@code neoforge.mods.toml}). It creates a {@link FabricModContainer} per mod; Fabric entrypoints are
 * run later by Fabricated's in-game mod, when registries are open.
 */
public final class FabricLanguageLoader implements IModLanguageLoader {
	@Override
	public String name() {
		return "fabricated";
	}

	@Override
	public String version() {
		return FabricatedInfo.get().fabricatedVersion();
	}

	@Override
	public ModContainer loadMod(IModInfo info, ModFileScanData modFileScanResults, ModuleLayer layer) throws ModLoadingException {
		FabricatedState state = FabricatedState.get();
		if (state == null || !state.isFabricMod(info.getModId())) {
			throw new ModLoadingException(ModLoadingIssue.error("§e[Fabricated]§r {0}",
					"Mod " + info.getModId() + " declares the Fabricated loader but was not translated by Fabricated").withAffectedMod(info));
		}
		return new FabricModContainer(info, layer, state);
	}
}
