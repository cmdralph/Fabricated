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

	/** Only what the platform ships: OneConfig's night-config JSON module is not NeoForge's (which has core and TOML). */
	@Test
	void providesOnlyThePlatformsModulesOfALibrary() {
		assertTrue(PlatformLibraries.isProvided("com/electronwill/nightconfig/core/file/FileConfig.class"));
		assertTrue(PlatformLibraries.isProvided("com/electronwill/nightconfig/toml/TomlFormat.class"));
		assertFalse(PlatformLibraries.isProvided("com/electronwill/nightconfig/json/JsonFormat.class"));
		assertFalse(PlatformLibraries.isProvided("com/electronwill/nightconfig/yaml/YamlFormat.class"));
	}
}
