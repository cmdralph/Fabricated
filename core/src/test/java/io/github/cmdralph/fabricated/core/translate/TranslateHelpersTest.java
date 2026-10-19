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
import io.github.cmdralph.fabricated.core.mixin.MixinConfigFile;
import net.fabricmc.loader.api.metadata.ContactInformation;

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
				List.of(new ModsTomlWriter.MixinConfigSpec("my.mixins.json", "0.17.4")), true, List.of("fabric_api", "other"), Map.of(), Map.of()));
		assertTrue(toml.contains("modLoader=\"fabricated\""));
		assertTrue(toml.contains("displayName=\"My \\\"Mod\\\"\""));
		assertTrue(toml.contains("description=\"Line1\\nLine2\\\\end\""));
		assertTrue(toml.contains("authors=\"Alice, Bob\""));
		assertTrue(toml.contains("[[mixins]]\nconfig=\"my.mixins.json\"\nbehaviorVersion=\"0.17.4\""));
		assertTrue(toml.contains("[[accessTransformers]]\nfile=\"META-INF/accesstransformer.cfg\""));
		assertTrue(toml.contains("[[dependencies.my_mod]]\nmodId=\"fabric_api\"\ntype=\"optional\"\nordering=\"AFTER\""));
		assertTrue(toml.contains("license=\"MIT\""));
		assertFalse(toml.contains("issueTrackerURL"));
		assertFalse(toml.contains("modproperties"));
		assertFalse(toml.contains("sodium:options"));
	}

	/** A Fabric mod's Sodium integration, in the form Sodium for NeoForge reads. */
	@Test
	void translatesSodiumIntegration() throws Exception {
		var md = FabricModJsonParser.parse("{\"schemaVersion\":1,\"id\":\"aaa\",\"version\":\"1\","
				+ "\"entrypoints\":{\"sodium:config_api_user\":[\"com.example.Page\",\"com.example.Other\"]},"
				+ "\"custom\":{\"sodium:options\":{\"mixin.features.render.world.sky\":false,\"mixin.bad\":\"no\"}}}");
		List<String> notes = new ArrayList<>();
		assertEquals(Map.of("sodium:config_api_user", "com.example.Page"), ModTranslator.sodiumProperties(md, notes));
		assertEquals(Map.of("mixin.features.render.world.sky", false), ModTranslator.sodiumOptions(md, notes));
		assertEquals(2, notes.size(), notes.toString());

		var kotlin = FabricModJsonParser.parse("{\"schemaVersion\":1,\"id\":\"aaa\",\"version\":\"1\","
				+ "\"entrypoints\":{\"sodium:config_api_user\":[{\"adapter\":\"kotlin\",\"value\":\"com.example.Page\"}]}}");
		notes.clear();
		assertEquals(Map.of(), ModTranslator.sodiumProperties(kotlin, notes));
		assertEquals(Map.of(), ModTranslator.sodiumOptions(kotlin, notes));
		assertEquals(1, notes.size(), notes.toString());
	}

	/** What Sodium for NeoForge reads from a mod: its options page (a mod property) and its overrides of Sodium's mixins. */
	@Test
	void writesWhatSodiumForNeoForgeReads() {
		Map<String, Boolean> options = new LinkedHashMap<>();
		options.put("mixin.features.render.world.sky", false);
		options.put("mixin.core", true);
		String toml = ModsTomlWriter.write(new ModsTomlWriter.Spec("my_mod", "1.0.0", "My Mod", "", List.of(), List.of(), List.of(), null, null, null,
				List.of(), false, List.of(), Map.of("sodium:config_api_user", "com.example.SodiumPage"), options));
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
