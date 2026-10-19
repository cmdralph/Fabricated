package io.github.cmdralph.fabricated.core.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
}
