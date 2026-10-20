package io.github.cmdralph.fabricated.core.translate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.cmdralph.fabricated.core.metadata.ModIds;
import io.github.cmdralph.fabricated.core.metadata.ContactInformationImpl.PersonImpl;
import io.github.cmdralph.fabricated.core.metadata.FabricModJsonParser;
import io.github.cmdralph.fabricated.core.metadata.FabricModMetadata;
import io.github.cmdralph.fabricated.core.mixin.MixinConfigFile;
import io.github.cmdralph.fabricated.core.mixin.PreflightFinding;
import net.fabricmc.loader.api.metadata.ContactInformation;
import net.fabricmc.loader.api.metadata.ModEnvironment;

class TranslateHelpersTest {
	@Test
	void normalisesIds() {
		assertEquals("cloth_config", ModIds.toNeoForgeId("cloth-config"));
	}

	@Test
	void writesValidEscapedToml() {
		String toml = ModsTomlWriter.write(new ModsTomlWriter.Spec("my_mod", "1.0.0", "My \"Mod\"", "Line1\nLine2\\end",
				List.of(new PersonImpl("Alice", ContactInformation.EMPTY), new PersonImpl("Bob", ContactInformation.EMPTY)),
				List.of(), List.of("MIT"), "https://example.com", null, "icon.png",
				List.of(new ModsTomlWriter.MixinConfigSpec("my.mixins.json", "0.17.4")), true, true, List.of("fabric_api", "other"), Map.of(), Map.of()));
		assertTrue(toml.contains("modLoader=\"fabricated\""));
		assertTrue(toml.contains("displayName=\"My \\\"Mod\\\"\""));
		assertTrue(toml.contains("description=\"Line1\\nLine2\\\\end\""));
		assertTrue(toml.contains("authors=\"Alice, Bob\""));
		assertTrue(toml.contains("[[mixins]]\nconfig=\"my.mixins.json\"\nbehaviorVersion=\"0.17.4\""));
		assertTrue(toml.contains("enumExtensions=\"META-INF/fabricated/enum_extensions.json\""), toml);
		assertTrue(toml.contains("[[accessTransformers]]\nfile=\"META-INF/accesstransformer.cfg\""));
		assertTrue(toml.contains("[[dependencies.my_mod]]\nmodId=\"fabric_api\"\ntype=\"optional\"\nordering=\"AFTER\""));
		int afterFabricated = toml.indexOf("modId=\"fabricated\"\ntype=\"optional\"\nordering=\"AFTER\"");
		assertTrue(afterFabricated >= 0 && afterFabricated < toml.indexOf("modId=\"fabric_api\""), "every translated mod loads after Fabricated's own mod: " + toml);
		assertTrue(toml.contains("license=\"MIT\""));
		assertFalse(toml.contains("issueTrackerURL"));
		assertFalse(toml.contains("modproperties"));
		assertFalse(toml.contains("sodium:options"));
	}

	/** A jar built for both loaders: its NeoForge build's pack metadata is left out, as Fabric ignores it (CORPUS-1). */
	@Test
	void packMetadataLosesOnlyNeoForgeSections() throws Exception {
		String both = """
				{"pack": {"description": "Terralith", "pack_format": 48},
				 "overlays": {"entries": [{"formats": [48, 60], "directory": "overlay_48"}]},
				 "neoforge:overlays": {"entries": [{"formats": [48, 60], "directory": "neoforge", "neoforge:conditions": [{"type": "terralith:config"}]}]}}""";
		byte[] fabric = ModTranslator.withoutNeoForgeSections(both.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		Map<String, Object> read = io.github.cmdralph.fabricated.core.json.Json.parseObject(new String(fabric, java.nio.charset.StandardCharsets.UTF_8));
		assertEquals(Set.of("pack", "overlays"), read.keySet());
		assertEquals(io.github.cmdralph.fabricated.core.json.Json.parseObject(both).get("overlays"), read.get("overlays"), "Fabric's own overlays stay");
		assertEquals(null, ModTranslator.withoutNeoForgeSections("{\"pack\": {\"pack_format\": 48}}".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
				"nothing to leave out: the jar's own file is used");
		assertEquals(null, ModTranslator.withoutNeoForgeSections("not json".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
	}

	/** A Fabric mod's Sodium and Jade integration, in the form Sodium and Jade for NeoForge read. */
	@Test
	void translatesSodiumAndJadeIntegration() throws Exception {
		var md = FabricModJsonParser.parse("{\"schemaVersion\":1,\"id\":\"aaa\",\"version\":\"1\","
				+ "\"entrypoints\":{\"sodium:config_api_user\":[\"com.example.Page\",\"com.example.Other\"]},"
				+ "\"custom\":{\"sodium:options\":{\"mixin.features.render.world.sky\":false,\"mixin.bad\":\"no\"},"
				+ "\"jade\":{\"disableItemModNameTooltip\":true}}}");
		List<String> notes = new ArrayList<>();
		assertEquals(Map.of("sodium:config_api_user", "com.example.Page", "jade", Map.of("disableItemModNameTooltip", true)),
				ModTranslator.modProperties(md, notes));
		assertEquals(Map.of("mixin.features.render.world.sky", false), ModTranslator.booleans(md, "sodium:options", notes));
		assertEquals(2, notes.size(), notes.toString());

		var kotlin = FabricModJsonParser.parse("{\"schemaVersion\":1,\"id\":\"aaa\",\"version\":\"1\","
				+ "\"entrypoints\":{\"sodium:config_api_user\":[{\"adapter\":\"kotlin\",\"value\":\"com.example.Page\"}]}}");
		notes.clear();
		assertEquals(Map.of(), ModTranslator.modProperties(kotlin, notes));
		assertEquals(Map.of(), ModTranslator.booleans(kotlin, "sodium:options", notes));
		assertEquals(1, notes.size(), notes.toString());
	}

	/** What Sodium for NeoForge reads from a mod: its options page (a mod property) and its overrides of Sodium's mixins. */
	@Test
	void writesWhatSodiumForNeoForgeReads() {
		Map<String, Boolean> options = new LinkedHashMap<>();
		options.put("mixin.features.render.world.sky", false);
		options.put("mixin.core", true);
		String toml = ModsTomlWriter.write(new ModsTomlWriter.Spec("my_mod", "1.0.0", "My Mod", "", List.of(), List.of(), List.of(), null, null, null,
				List.of(), false, false, List.of(), Map.of("sodium:config_api_user", "com.example.SodiumPage"), options));
		String jade = ModsTomlWriter.write(new ModsTomlWriter.Spec("my_mod", "1.0.0", "My Mod", "", List.of(), List.of(), List.of(), null, null, null,
				List.of(), false, false, List.of(), Map.of("jade", Map.of("fastScroll", true)), Map.of()));
		assertTrue(jade.contains("[modproperties.my_mod]\n\"jade\"={\"fastScroll\"=true}\n"), jade);
		assertTrue(toml.contains("[[mods]]\nmodId=\"my_mod\"\nversion=\"1.0.0\"\ndisplayName=\"My Mod\"\ndescription=\"\"\n"
				+ "\"sodium:options\"={\"mixin.features.render.world.sky\"=false, \"mixin.core\"=true}\n"), toml);
		assertTrue(toml.contains("[modproperties.my_mod]\n\"sodium:config_api_user\"=\"com.example.SodiumPage\"\n"), toml);
	}

	@Test
	void computesFabricMixinBehaviourAndClampsForNeoForge() throws Exception {
		var newMod = FabricModJsonParser.parse("{\"schemaVersion\":1,\"id\":\"aaa\",\"version\":\"1\",\"depends\":{\"fabricloader\":\">=0.19.4\"}}");
		assertEquals("0.17.4", MixinCompat.fabricBehaviorVersion(newMod.getDependencies()));
		var midMod = FabricModJsonParser.parse("{\"schemaVersion\":1,\"id\":\"aaa\",\"version\":\"1\",\"depends\":{\"fabricloader\":\">=0.18.4\"}}");
		assertEquals("0.17.0", MixinCompat.fabricBehaviorVersion(midMod.getDependencies()));
		var oldMod = FabricModJsonParser.parse("{\"schemaVersion\":1,\"id\":\"aaa\",\"version\":\"1\",\"depends\":{\"fabricloader\":\">=0.14.0\"}}");
		assertEquals("0.10.0", MixinCompat.fabricBehaviorVersion(oldMod.getDependencies()));
		var noDep = FabricModJsonParser.parse("{\"schemaVersion\":1,\"id\":\"aaa\",\"version\":\"1\"}");
		assertEquals("0.9.2", MixinCompat.fabricBehaviorVersion(noDep.getDependencies()));

		assertEquals("0.16.5", MixinCompat.clampForNeoForge("0.10.0", "0.16.5+mixin.0.8.7", "0.16.5"), "raised to FML 10's minimum");
		assertEquals("0.17.1", MixinCompat.clampForNeoForge("0.17.0", "0.17.4+mixin.0.8.7", "0.17.1"), "raised to FML 11/12's minimum");
		assertEquals("0.16.5", MixinCompat.clampForNeoForge("0.17.4", "0.16.5+mixin.0.8.7", "0.16.5"), "lowered to the running Mixin on 1.21.11");
		assertEquals("0.17.1", MixinCompat.clampForNeoForge("0.17.1", "0.17.4+mixin.0.8.7", "0.17.1"));
	}

	@Test
	void parsesSkipMixinOverrides() throws Exception {
		MixinConfigFile cfg = MixinConfigFile.parse("{\"package\":\"com.example.mixin\",\"mixins\":[\"A\",\"B\"],\"client\":[\"C\"]}");
		ModTranslator.Skips skips = ModTranslator.skipsFor("x.mixins.json", cfg, Set.of("A", "x.mixins.json:C", "other.json:B", "Missing", "B#handler", "A#ignored"));
		assertEquals(Set.of("A", "C"), skips.mixins());
		assertEquals(java.util.Map.of("B", Set.of("handler")), skips.members(), "a member of a mixin left out whole adds nothing");
		assertEquals(Set.of("A", "x.mixins.json:C", "B#handler", "A#ignored"), skips.matched());

		// The entries naming no mixin are noted, where every config they could apply to was read (CFG-1).
		List<PreflightFinding> findings = new ArrayList<>();
		List<FabricModMetadata.MixinEntry> declared = List.of(new FabricModMetadata.MixinEntry("x.mixins.json", ModEnvironment.UNIVERSAL),
				new FabricModMetadata.MixinEntry("client.mixins.json", ModEnvironment.CLIENT));
		ModTranslator.unmatchedSkips(Set.of("A", "Missing", "other.json:B", "client.mixins.json:Gone", "x.mixins.json:Typo"), Set.of("A"), declared,
				Set.of("x.mixins.json"), findings);
		assertEquals(List.of("The skipMixins entry \"other.json:B\" names no mixin of this mod; check its spelling",
				"The skipMixins entry \"x.mixins.json:Typo\" names no mixin of this mod; check its spelling"),
				findings.stream().map(PreflightFinding::message).toList(), "the client config wasn't read on this side");
		String filtered = cfg.withoutMixins(Set.of("A"));
		MixinConfigFile reparsed = MixinConfigFile.parse(filtered);
		assertEquals(List.of("B"), reparsed.mixinsFor(false));
		assertEquals("com/example/mixin/B", reparsed.internalName("B"));
	}

	@Test
	void contactUrlsNeoForgeWouldRejectAreLeftOut() {
		java.util.List<String> notes = new java.util.ArrayList<>();
		org.junit.jupiter.api.Assertions.assertEquals("https://github.com/x/y/issues", ModTranslator.url("https://github.com/x/y/issues", "issues", notes));
		org.junit.jupiter.api.Assertions.assertNull(ModTranslator.url("github.com/x/y/issues", "issues", notes));
		org.junit.jupiter.api.Assertions.assertNull(ModTranslator.url("discord.gg/abc def", "homepage", notes));
		org.junit.jupiter.api.Assertions.assertNull(ModTranslator.url("  ", "homepage", notes));
		org.junit.jupiter.api.Assertions.assertEquals(2, notes.size());
	}
}
