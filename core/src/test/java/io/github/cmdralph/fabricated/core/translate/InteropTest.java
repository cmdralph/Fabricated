package io.github.cmdralph.fabricated.core.translate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.cmdralph.fabricated.core.json.JsonException;
import io.github.cmdralph.fabricated.core.metadata.FabricModMetadata.EntrypointSpec;

class InteropTest {
	@Test
	void pluginEntrypointsBecomeTheAnnotationsTheNeoForgeBuildsLookFor() {
		Map<String, List<EntrypointSpec>> entrypoints = new LinkedHashMap<>();
		entrypoints.put("main", List.of(new EntrypointSpec("default", "a.Main")));
		entrypoints.put("jei_mod_plugin", List.of(new EntrypointSpec("default", "a.JeiPlugin")));
		entrypoints.put("emi", List.of(new EntrypointSpec("default", "a.EmiPlugin")));
		entrypoints.put("rei_client", List.of(new EntrypointSpec("default", "a.Rei$Client")));
		entrypoints.put("rei_plugins", List.of(new EntrypointSpec("default", "a.ReiLegacy")));
		entrypoints.put("jade", List.of(new EntrypointSpec("default", "a.Jade::INSTANCE"), new EntrypointSpec("kotlin", "a.KotlinJade")));
		entrypoints.put("voicechat", List.of(new EntrypointSpec("default", "a.VoicePlugin")));
		List<String> notes = new ArrayList<>();

		List<Interop.Annotation> annotations = Interop.annotations(entrypoints, notes);

		assertEquals(List.of(
				new Interop.Annotation("Lmezz/jei/api/JeiPlugin;", "a.JeiPlugin", "JEI"),
				new Interop.Annotation("Ldev/emi/emi/api/EmiEntrypoint;", "a.EmiPlugin", "EMI"),
				new Interop.Annotation("Lme/shedaniel/rei/forge/REIPluginLoaderClient;", "a.Rei$Client", "REI"),
				new Interop.Annotation("Lme/shedaniel/rei/forge/REIPluginLoaderCommon;", "a.ReiLegacy", "REI"),
				new Interop.Annotation("Lde/maxhenkel/voicechat/api/ForgeVoicechatPlugin;", "a.VoicePlugin", "Simple Voice Chat")), annotations);
		// Jade for NeoForge can only construct a class: a field reference or another language adapter's value is reported.
		assertEquals(2, notes.size(), notes.toString());
		assertTrue(notes.get(0).startsWith("Its Jade plugin a.Jade::INSTANCE is left out"), notes.get(0));
		assertTrue(notes.get(1).contains("a.KotlinJade (language adapter kotlin)"), notes.get(1));
	}

	@Test
	void anEntrypointWithAPropertyConventionBecomesThatProperty() {
		Map<String, List<EntrypointSpec>> entrypoints = Map.of("sodium:config_api_user", List.of(new EntrypointSpec("default", "a.SodiumPage")),
				"jei_mod_plugin", List.of(new EntrypointSpec("default", "a.JeiPlugin")));
		List<String> notes = new ArrayList<>();
		assertEquals(Map.of("sodium:config_api_user", "a.SodiumPage"), Interop.properties(entrypoints, notes), "an annotation is no property");
		assertEquals(List.of(), notes);
		assertEquals(List.of(new Interop.Annotation("Lmezz/jei/api/JeiPlugin;", "a.JeiPlugin", "JEI")), Interop.annotations(entrypoints, notes),
				"a property is no annotation");
	}

	@Test
	void aConventionMustBeOneAnnotationOrOneProperty() throws Exception {
		assertEquals(Map.of("x", new Interop.Convention("X", "La/X;", null)),
				Interop.parse("{\"schema\":1,\"entrypoints\":{\"x\":{\"host\":\"X\",\"annotation\":\"La/X;\"}}}"));
		for (String broken : List.of(
				"{\"schema\":2,\"entrypoints\":{}}",
				"{\"schema\":1,\"entrypoints\":{\"x\":{\"annotation\":\"La/X;\"}}}",
				"{\"schema\":1,\"entrypoints\":{\"x\":{\"host\":\"X\"}}}",
				"{\"schema\":1,\"entrypoints\":{\"x\":{\"host\":\"X\",\"annotation\":\"La/X;\",\"property\":\"x\"}}}",
				"{\"schema\":1,\"entrypoints\":{\"x\":{\"host\":\"X\",\"annotation\":\"a.X\"}}}",
				"{\"schema\":1,\"entrypoints\":{\"x\":{\"host\":\"X\",\"property\":\"x\",\"other\":1}}}")) {
			assertThrows(JsonException.class, () -> Interop.parse(broken), broken);
		}
	}
}
