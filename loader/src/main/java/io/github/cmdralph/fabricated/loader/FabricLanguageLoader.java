package io.github.cmdralph.fabricated.loader;

import java.lang.annotation.ElementType;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.cmdralph.fabricated.core.translate.Interop;
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
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");

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
		addPluginAnnotations(info.getModId(), state.pluginAnnotations(info.getModId()), modFileScanResults);
		return new FabricModContainer(info, layer, state);
	}

	/**
	 * Lets the NeoForge builds of recipe viewers and tooltip mods find the mod's plugins: they look for annotations in
	 * NeoForge's scan data, which FML hands this method after scanning and before any mod is constructed (the same
	 * object they read later). A class that already carries the annotation (a multi-loader mod) was found by the scan,
	 * and adding it again would load the plugin twice.
	 */
	private static void addPluginAnnotations(String modId, List<Interop.Annotation> plugins, ModFileScanData scan) {
		if (plugins.isEmpty()) return;
		Set<ModFileScanData.AnnotationData> annotations = scan.getAnnotations();
		for (Interop.Annotation plugin : plugins) {
			Type type = Type.getType(plugin.descriptor());
			Type clazz = Type.getObjectType(plugin.className().replace('.', '/'));
			if (annotations.stream().anyMatch(a -> a.annotationType().equals(type) && a.clazz().equals(clazz))) continue;
			try {
				annotations.add(new ModFileScanData.AnnotationData(type, ElementType.TYPE, clazz, plugin.className(), Map.of()));
			} catch (UnsupportedOperationException e) {
				LOGGER.warn("NeoForge's scan data of {} can't be extended; its plugins for other mods won't be found", modId);
				return;
			}
		}
	}
}
