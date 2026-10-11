package io.github.cmdralph.fabricated.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FabricatedConfigTest {
	@Test
	void createsDefaultFileAndReadsItBack(@TempDir Path dir) throws Exception {
		FabricatedConfig created = FabricatedConfig.load(dir);
		assertTrue(created.warnings().isEmpty());
		assertTrue(Files.exists(dir.resolve(FabricatedConfig.FILE_NAME)));
		FabricatedConfig reloaded = FabricatedConfig.load(dir);
		assertTrue(reloaded.warnings().isEmpty(), reloaded.warnings().toString());
		assertEquals(created.toJson(), reloaded.toJson());
		assertEquals(FabricatedConfig.EntrypointFailure.ERROR, reloaded.onEntrypointFailure());
	}

	@Test
	void parsesOverridesAndValidatesValues() {
		FabricatedConfig c = FabricatedConfig.parse("""
				{
				  "logLevel": "debug",
				  "onEntrypointFailure": "disable",
				  "strictLinkage": "yes",
				  "cache": {"maxUnusedLaunches": 0},
				  "limits": {"maxEntries": 500},
				  "mods": {"some-mod": {"disabled": true, "skipMixins": ["a.mixins.json:Foo"], "aliases": ["alias"], "bogus": 1}},
				  "unknownKey": true
				}
				""");
		assertEquals("DEBUG", c.logLevel());
		assertEquals(FabricatedConfig.EntrypointFailure.DISABLE, c.onEntrypointFailure());
		assertFalse(c.strictLinkage(), "invalid type falls back to default");
		assertEquals(5, c.maxUnusedLaunches(), "out of range falls back to default");
		assertEquals(500, c.limits().maxEntries());
		assertTrue(c.override("some-mod").disabled());
		assertEquals(Set.of("a.mixins.json:Foo"), c.override("some-mod").skipMixins());
		assertFalse(c.override("other").disabled());
		assertEquals(4, c.warnings().size(), c.warnings().toString());
	}

	@Test
	void brokenJsonFallsBackToDefaults() {
		FabricatedConfig c = FabricatedConfig.parse("{ not json");
		assertEquals(1, c.warnings().size());
		assertEquals("INFO", c.logLevel());
	}
}
