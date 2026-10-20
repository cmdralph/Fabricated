package io.github.cmdralph.fabricated.core.translate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.cmdralph.fabricated.core.metadata.FabricModMetadata.EntrypointSpec;

/**
 * The plugins a Fabric mod declares for other mods as entrypoints, as the NeoForge builds of those mods find plugins:
 * by an annotation on the plugin class in NeoForge's scan data. That is how JEI, EMI, REI and Jade discover plugins on
 * NeoForge; a Fabric jar has no such annotation, so the loader adds it (WTHIT needs nothing: on NeoForge it reads the
 * jar's plugin file, which the translated mod keeps). The NeoForge builds only load the class by name and call its
 * public no-argument constructor, which is what Fabric's default language adapter does with a plain class name; any
 * other entrypoint value is reported instead.
 */
public final class PluginAnnotations {
	/** REI's loader annotations accept any plugin provider and make one instance of it, as Fabric's REI does. */
	private static final String REI_COMMON = "Lme/shedaniel/rei/forge/REIPluginLoaderCommon;";
	/** Entrypoint key → the mod reading it, and the annotation its NeoForge build looks for. */
	private static final Map<String, Viewer> BY_KEY = Map.ofEntries(
			Map.entry("jei_mod_plugin", new Viewer("JEI", "Lmezz/jei/api/JeiPlugin;")),
			Map.entry("emi", new Viewer("EMI", "Ldev/emi/emi/api/EmiEntrypoint;")),
			Map.entry("jade", new Viewer("Jade", "Lsnownee/jade/api/WailaPlugin;")),
			Map.entry("rei_common", new Viewer("REI", REI_COMMON)),
			Map.entry("rei", new Viewer("REI", REI_COMMON)),
			Map.entry("rei_plugins", new Viewer("REI", REI_COMMON)),
			Map.entry("rei_plugins_v0", new Viewer("REI", REI_COMMON)),
			Map.entry("rei_containers", new Viewer("REI", REI_COMMON)),
			Map.entry("rei_client", new Viewer("REI", "Lme/shedaniel/rei/forge/REIPluginLoaderClient;")),
			Map.entry("rei_server", new Viewer("REI", "Lme/shedaniel/rei/forge/REIPluginLoaderDedicatedServer;")));

	private record Viewer(String name, String annotation) {
	}

	/**
	 * An annotation to add to a mod's scan data.
	 *
	 * @param descriptor the annotation type, e.g. {@code Lmezz/jei/api/JeiPlugin;}
	 * @param className  the plugin class, as a binary name ({@code a.b.Outer$Inner})
	 * @param viewer     the mod that reads it, for the report
	 */
	public record Annotation(String descriptor, String className, String viewer) {
	}

	private PluginAnnotations() {
	}

	/**
	 * @param entrypoints the mod's entrypoints, as Fabricated runs them (compatibility hints and skips applied)
	 * @param notes       receives a line for each plugin the NeoForge build of its mod can't load
	 */
	public static List<Annotation> of(Map<String, List<EntrypointSpec>> entrypoints, List<String> notes) {
		List<Annotation> annotations = new ArrayList<>();
		for (Map.Entry<String, List<EntrypointSpec>> e : entrypoints.entrySet()) {
			Viewer viewer = BY_KEY.get(e.getKey());
			if (viewer == null) continue;
			for (EntrypointSpec spec : e.getValue()) {
				if (spec.adapter().equals("default") && !spec.value().contains("::")) {
					annotations.add(new Annotation(viewer.annotation(), spec.value(), viewer.name()));
				} else {
					notes.add("Its " + viewer.name() + " plugin " + spec.value() + (spec.adapter().equals("default") ? "" : " (language adapter " + spec.adapter() + ")")
							+ " is left out: " + viewer.name() + " for NeoForge only creates a plugin from a class with a public no-argument constructor");
				}
			}
		}
		return annotations;
	}
}
