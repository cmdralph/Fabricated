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
	void disablingAModChangesOnlyItsEntry(@TempDir Path dir) throws Exception {
		Path file = dir.resolve(FabricatedConfig.FILE_NAME);
		Files.writeString(file, """
				{ "strictLinkage": true, "mods": { "sodium": { "loadUnsupported": true }, "other": { "skipMixins": ["a.mixins.json:A"] } } }""");
		FabricatedConfig.setModDisabled(dir, "sodium", true);
		FabricatedConfig.setModDisabled(dir, "newmod", true);
		FabricatedConfig config = FabricatedConfig.load(dir);
		assertTrue(config.warnings().isEmpty(), config.warnings().toString());
		assertTrue(config.strictLinkage());
		assertTrue(config.override("sodium").disabled() && config.override("sodium").loadUnsupported());
		assertTrue(config.override("newmod").disabled());
		assertEquals(Set.of("a.mixins.json:A"), config.override("other").skipMixins());
		assertFalse(config.override("other").disabled());

		FabricatedConfig.setModDisabled(dir, "sodium", false);
		assertFalse(FabricatedConfig.load(dir).override("sodium").disabled());
		// Without a file, the defaults with the one change.
		Path empty = dir.resolve("empty");
		FabricatedConfig.setModDisabled(empty, "x", true);
		assertTrue(FabricatedConfig.load(empty).override("x").disabled());
		// A file that isn't a config object is left for the player to fix.
		Files.writeString(file, "{ \"mods\": [] }");
		org.junit.jupiter.api.Assertions.assertThrows(io.github.cmdralph.fabricated.core.json.JsonException.class,
				() -> FabricatedConfig.setModDisabled(dir, "sodium", true));
		assertEquals("{ \"mods\": [] }", Files.readString(file));
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
				  "mods": {"some-mod": {"disabled": true, "skipMixins": ["a.mixins.json:Foo"], "aliases": ["alias"], "bogus": 1, "loadUnsupported": true}},
				  "unknownKey": true
				}
				""");
		assertEquals(FabricatedConfig.EntrypointFailure.DISABLE, c.onEntrypointFailure());
		assertFalse(c.strictLinkage(), "invalid type falls back to default");
		assertEquals(5, c.maxUnusedLaunches(), "out of range falls back to default");
		assertEquals(500, c.limits().maxEntries());
		assertTrue(c.override("some-mod").disabled());
		assertEquals(Set.of("a.mixins.json:Foo"), c.override("some-mod").skipMixins());
		assertFalse(c.override("other").disabled());
		assertTrue(c.override("some-mod").loadUnsupported());
		assertFalse(c.override("other").loadUnsupported());
		// The retired "logLevel" key is accepted silently.
		assertEquals(4, c.warnings().size(), c.warnings().toString());
	}

	@Test
	void brokenJsonFallsBackToDefaults() {
		FabricatedConfig c = FabricatedConfig.parse("{ not json");
		assertEquals(1, c.warnings().size());
		assertEquals(FabricatedConfig.EntrypointFailure.ERROR, c.onEntrypointFailure());
	}
}
