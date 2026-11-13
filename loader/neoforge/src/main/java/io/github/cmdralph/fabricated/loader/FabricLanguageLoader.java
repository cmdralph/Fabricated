package io.github.cmdralph.fabricated.loader;

import java.lang.annotation.ElementType;
import java.util.Map;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoadingException;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.language.IModLanguageLoader;
import net.neoforged.neoforgespi.language.ModFileScanData;

/**
 * NeoForge language loader for translated Fabric mods ({@code modLoader="fabricated"} in the generated
 * {@code neoforge.mods.toml}). It creates a {@link FabricModContainer} per mod, and adds to the mod's scan data the
 * annotations other mods' NeoForge builds find its plugins by ({@link PluginAnnotations}): FML hands it the scan data
 * after scanning and before any mod is constructed. Fabric entrypoints are run later by Fabricated's in-game mod, when
 * registries are open.
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
			throw new ModLoadingException(ModLoadingIssue.error(FabricDependencyLocator.MESSAGE,
					"Mod " + info.getModId() + " declares the Fabricated loader but was not translated by Fabricated").withAffectedMod(info));
		}
		PluginAnnotations.add(info.getModId(), state.pluginAnnotations(info.getModId()), modFileScanResults.getAnnotations(),
				ModFileScanData.AnnotationData::annotationType, ModFileScanData.AnnotationData::clazz,
				(type, clazz) -> new ModFileScanData.AnnotationData(type, ElementType.TYPE, clazz, clazz.getClassName(), Map.of()));
		return new FabricModContainer(info, layer, state);
	}
}
