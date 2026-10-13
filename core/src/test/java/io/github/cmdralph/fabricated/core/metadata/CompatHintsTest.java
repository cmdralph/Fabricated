package io.github.cmdralph.fabricated.core.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.cmdralph.fabricated.core.metadata.FabricModMetadata.EntrypointSpec;

class CompatHintsTest {
	private static CompatHints hints(String block) throws Exception {
		return FabricModJsonParser.parse("{\"schemaVersion\":1,\"id\":\"mm\",\"version\":\"1\",\"entrypoints\":{\"main\":[\"a.Main\"],\"client\":[\"a.Client\"]},"
				+ "\"custom\":{\"fabricated\":" + block + "}}").compatHints();
	}

	@Test
	void parsesEveryKey() throws Exception {
		CompatHints h = hints("""
				{"schema": 1, "skipMixins": ["m.mixins.json:client.Tooltip", "Other"], "mixins": ["m.neoforge.mixins.json"],
				 "entrypoints": {"main": ["a.NeoForgeInit"]}, "skipEntrypoints": ["client"]}""");
		assertEquals(Set.of("m.mixins.json:client.Tooltip", "Other"), h.skipMixins());
		assertEquals("m.neoforge.mixins.json", h.mixins().getFirst().config());
		assertTrue(h.warnings().isEmpty(), h.warnings().toString());
		Map<String, List<EntrypointSpec>> own = Map.of("main", List.of(new EntrypointSpec("default", "a.Main")),
				"client", List.of(new EntrypointSpec("default", "a.Client")));
		Map<String, List<EntrypointSpec>> effective = h.entrypointsOf(own);
		assertEquals(List.of("a.Main", "a.NeoForgeInit"), effective.get("main").stream().map(EntrypointSpec::value).toList());
		assertTrue(!effective.containsKey("client"));
	}

	@Test
	void problemsAreWarningsNotFailures() throws Exception {
		CompatHints h = hints("""
				{"skipMixins": ["ok.Mixin"], "mixins": ["../escape.json"], "entrypoints": "nope", "ignorePreflight": true}""");
		assertEquals(Set.of("ok.Mixin"), h.skipMixins(), "a valid key still applies");
		assertNull(h.mixins(), "an invalid path is ignored");
		assertTrue(h.entrypoints().isEmpty());
		assertEquals(3, h.warnings().size(), h.warnings().toString());
		assertTrue(h.warnings().stream().anyMatch(w -> w.contains("ignorePreflight")), "players' settings are not for mods to set");

		assertTrue(hints("{\"schema\": 2, \"skipMixins\": [\"A\"]}").skipMixins().isEmpty(), "newer schemas are ignored");
		assertEquals(1, hints("[]").warnings().size());
		assertSame(CompatHints.NONE, FabricModJsonParser.parse("{\"schemaVersion\":1,\"id\":\"mm\",\"version\":\"1\"}").compatHints());
	}
}
