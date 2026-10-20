package io.github.cmdralph.fabricated.core.translate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.cmdralph.fabricated.core.metadata.FabricModMetadata.EntrypointSpec;

class PluginAnnotationsTest {
	@Test
	void pluginEntrypointsBecomeTheAnnotationsTheNeoForgeBuildsLookFor() {
		Map<String, List<EntrypointSpec>> entrypoints = new LinkedHashMap<>();
		entrypoints.put("main", List.of(new EntrypointSpec("default", "a.Main")));
		entrypoints.put("jei_mod_plugin", List.of(new EntrypointSpec("default", "a.JeiPlugin")));
		entrypoints.put("emi", List.of(new EntrypointSpec("default", "a.EmiPlugin")));
		entrypoints.put("rei_client", List.of(new EntrypointSpec("default", "a.Rei$Client")));
		entrypoints.put("rei_plugins", List.of(new EntrypointSpec("default", "a.ReiLegacy")));
		entrypoints.put("jade", List.of(new EntrypointSpec("default", "a.Jade::INSTANCE"), new EntrypointSpec("kotlin", "a.KotlinJade")));
		List<String> notes = new ArrayList<>();

		List<PluginAnnotations.Annotation> annotations = PluginAnnotations.of(entrypoints, notes);

		assertEquals(List.of(
				new PluginAnnotations.Annotation("Lmezz/jei/api/JeiPlugin;", "a.JeiPlugin", "JEI"),
				new PluginAnnotations.Annotation("Ldev/emi/emi/api/EmiEntrypoint;", "a.EmiPlugin", "EMI"),
				new PluginAnnotations.Annotation("Lme/shedaniel/rei/forge/REIPluginLoaderClient;", "a.Rei$Client", "REI"),
				new PluginAnnotations.Annotation("Lme/shedaniel/rei/forge/REIPluginLoaderCommon;", "a.ReiLegacy", "REI")), annotations);
		// Jade for NeoForge can only construct a class: a field reference or another language adapter's value is reported.
		assertEquals(2, notes.size(), notes.toString());
		assertTrue(notes.get(0).startsWith("Its Jade plugin a.Jade::INSTANCE is left out"), notes.get(0));
		assertTrue(notes.get(1).contains("a.KotlinJade (language adapter kotlin)"), notes.get(1));
	}
}
