package io.github.cmdralph.fabricated.core.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PlatformLibrariesTest {
	@Test
	void findsTheNestedMixinExtras() throws Exception {
		String metadata = """
				{"jars": [
				  {"identifier": {"group": "net.neoforged", "artifact": "bus"}, "version": {"range": "[8,)", "artifactVersion": "8.0.5"}, "path": "META-INF/jarjar/bus.jar"},
				  {"identifier": {"group": "io.github.llamalad7", "artifact": "mixinextras-neoforge"},
				   "version": {"range": "[0.5.5,)", "artifactVersion": "0.5.5"}, "path": "META-INF/jarjar/mixinextras-neoforge-0.5.5.jar"}
				]}""";
		assertEquals("0.5.5", PlatformLibraries.nestedMixinExtras(metadata));
		assertNull(PlatformLibraries.nestedMixinExtras("{\"jars\": []}"));
		assertNull(PlatformLibraries.nestedMixinExtras("{}"));
	}

	/** Forge 61's fmlloader jarjar_options.json, as it reads (a nested MixinExtras dependency, then the offered jar). */
	@Test
	void findsTheMixinExtrasForgeOffers() throws Exception {
		String options = """
				{"options": [{
				  "resource": "com/llamalad7/mixinextras/platform/forge/MixinExtrasConfigPlugin.class",
				  "layer": "GAMELIBRARY", "id": "mixinextras",
				  "deps": [{"identifier": {"group": "com.github.LlamaLad7", "artifact": "MixinExtras"},
				            "version": {"range": "[0.5.4,)", "artifactVersion": "0.5.4"}, "path": "", "isObfuscated": false}],
				  "meta": {"identifier": {"group": "io.github.llamalad7", "artifact": "mixinextras-forge"},
				           "version": {"artifactVersion": "0.5.4"}, "path": "", "isObfuscated": false},
				  "nested": true
				}]}""";
		assertEquals("0.5.4", PlatformLibraries.offeredMixinExtras(options));
		assertNull(PlatformLibraries.offeredMixinExtras("{\"options\": [{\"meta\": {\"identifier\": {\"group\": \"x\", \"artifact\": \"y\"},"
				+ " \"version\": {\"artifactVersion\": \"1\"}}}]}"));
		assertNull(PlatformLibraries.offeredMixinExtras("{}"));
	}

	/** Only what the platform ships: OneConfig's night-config JSON module is not NeoForge's (which has core and TOML). */
	@Test
	void providesOnlyThePlatformsModulesOfALibrary() {
		assertTrue(PlatformLibraries.isProvided("com/electronwill/nightconfig/core/file/FileConfig.class"));
		assertTrue(PlatformLibraries.isProvided("com/electronwill/nightconfig/toml/TomlFormat.class"));
		assertFalse(PlatformLibraries.isProvided("com/electronwill/nightconfig/json/JsonFormat.class"));
		assertFalse(PlatformLibraries.isProvided("com/electronwill/nightconfig/yaml/YamlFormat.class"));
	}
}
