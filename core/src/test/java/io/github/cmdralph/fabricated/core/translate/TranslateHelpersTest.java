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

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlParser;

import io.github.cmdralph.fabricated.core.metadata.ModIds;
import io.github.cmdralph.fabricated.core.metadata.ContactInformationImpl.PersonImpl;
import io.github.cmdralph.fabricated.core.metadata.FabricModJsonParser;
import io.github.cmdralph.fabricated.core.metadata.FabricModMetadata;
import io.github.cmdralph.fabricated.core.mixin.MixinConfigFile;
import io.github.cmdralph.fabricated.core.mixin.MixinPreflight;
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

	/**
	 * Forge refuses a dependency without {@code mandatory} (Forge 47's and 66's {@code ModInfo}), reads mixin configs from
	 * the manifest and access transformers from their default path, and has no enum extension files.
	 */
	@Test
	void writesForgesDialect() {
		ModsTomlWriter.Spec spec = new ModsTomlWriter.Spec("my_mod", "1.0.0", "My Mod", "", List.of(), List.of(), List.of("MIT"), null, null, null,
				List.of(new ModsTomlWriter.MixinConfigSpec("my.mixins.json", "0.17.4"), new ModsTomlWriter.MixinConfigSpec("my.client.mixins.json", "0.17.4")),
				true, true, List.of("other"), Map.of("jade", Map.of("fastScroll", true)), Map.of());
		String toml = ModsTomlWriter.writeForge(spec);

		CommentedConfig file = new TomlParser().parse(toml);
		assertEquals("fabricated", file.get("modLoader"));
		Config mod = file.<List<Config>>get("mods").getFirst();
		assertEquals("my_mod", mod.get("modId"));
		assertFalse(mod.contains("enumExtensions"), toml);
		assertFalse(file.contains("mixins"), toml);
		assertFalse(file.contains("accessTransformers"), "Forge reads META-INF/accesstransformer.cfg by default: " + toml);
		assertEquals(Map.of("fastScroll", true), table(file.get(List.of("modproperties", "my_mod", "jade"))));
		List<Config> dependencies = file.get(List.of("dependencies", "my_mod"));
		assertEquals(List.of(ModIds.FABRICATED, "other"), dependencies.stream().map(d -> d.<String>get("modId")).toList());
		for (Config dependency : dependencies) {
			assertEquals(Boolean.FALSE, dependency.get("mandatory"), toml);
			assertEquals("*", dependency.get("versionRange"), "Forge's default range holds no version: " + toml);
			assertEquals("AFTER", dependency.get("ordering"));
			assertFalse(dependency.contains("type"), toml);
		}

		assertEquals(ModsTomlWriter.FORGE_PATH, PlatformDescriptor.FORGE.path());
		assertEquals(toml, PlatformDescriptor.FORGE.write(spec));
		assertEquals(Map.of("MixinConfigs", "my.mixins.json,my.client.mixins.json"), PlatformDescriptor.FORGE.manifestAttributes(spec));
		assertFalse(PlatformDescriptor.FORGE.enumExtensions());
		assertTrue(new String(PlatformDescriptor.FORGE.packMetadata(spec), java.nio.charset.StandardCharsets.UTF_8).contains("\"description\": \"My Mod\""));
		assertEquals(null, PlatformDescriptor.NEOFORGE.packMetadata(spec), "NeoForge makes a mod's pack metadata itself");
		assertEquals(Map.of(), PlatformDescriptor.FORGE.manifestAttributes(new ModsTomlWriter.Spec("my_mod", "1.0.0", "My Mod", "", List.of(), List.of(),
				List.of(), null, null, null, List.of(), false, false, List.of(), Map.of(), Map.of())), "no MixinConfigs without mixin configs");
	}

	/**
	 * NeoForge's loader refuses a version not starting with a digit, and stops the launch; a Fabric library's
	 * {@code animated-gif-lib-1.7} did (run 381).
	 */
	@Test
	void writesVersionsNeoForgeAccepts() {
		assertEquals("1.0.0", ModsTomlWriter.neoForgeVersion("1.0.0"));
		assertEquals("0+animated-gif-lib-1.7", ModsTomlWriter.neoForgeVersion("animated-gif-lib-1.7"));
		assertEquals("0+v2.1", ModsTomlWriter.neoForgeVersion("v2.1"));
		assertEquals("0+", ModsTomlWriter.neoForgeVersion(""));
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

	/**
	 * DATA-1: Tectonic's data carries its NeoForge build's conditions next to its Fabric build's; Fabric reads only its
	 * own, so the NeoForge ones (on the file, and on entries within it) are left out.
	 */
	@Test
	void dataFilesLoseOnlyNeoForgeConditions() throws Exception {
		String both = """
				{"fabric:load_conditions": [{"condition": "tectonic:config", "key": "underground_rivers"}],
				 "neoforge:conditions": [{"type": "tectonic:config", "key": "underground_rivers"}],
				 "type": "lithostitched:add_features", "step": 0.5,
				 "entries": [{"id": "a", "neoforge:conditions": [{"type": "neoforge:false"}]}, "neoforge:conditions"]}""";
		byte[] fabric = ModTranslator.withoutNeoForgeConditions(both.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		Map<String, Object> read = io.github.cmdralph.fabricated.core.json.Json.parseObject(new String(fabric, java.nio.charset.StandardCharsets.UTF_8));
		Map<String, Object> expected = io.github.cmdralph.fabricated.core.json.Json.parseObject("""
				{"fabric:load_conditions": [{"condition": "tectonic:config", "key": "underground_rivers"}],
				 "type": "lithostitched:add_features", "step": 0.5, "entries": [{"id": "a"}, "neoforge:conditions"]}""");
		assertEquals(expected, read);
		assertEquals(null, ModTranslator.withoutNeoForgeConditions("{\"values\": [\"neoforge:conditions\"]}".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
				"a value naming the key is no condition: the jar's own file is used");
		assertEquals(null, ModTranslator.withoutNeoForgeConditions("{\"neoforge:conditions\": [] // lenient".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
	}

	/**
	 * A Fabric mod's custom values and Sodium options page, where NeoForge mods read them: mod properties, and the
	 * namespaced values also as keys of the file (Lithium) and of the mod's entry (Sodium).
	 */
	@Test
	void givesCustomValuesAndPropertiesToNeoForgeMods() throws Exception {
		var md = FabricModJsonParser.parse("{\"schemaVersion\":1,\"id\":\"aaa\",\"version\":\"1\","
				+ "\"entrypoints\":{\"sodium:config_api_user\":[\"com.example.Page\",\"com.example.Other\"]},"
				+ "\"custom\":{\"sodium:options\":{\"mixin.features.render.world.sky\":false},"
				+ "\"jade\":{\"disableItemModNameTooltip\":true},\"modmenu\":{\"links\":[\"a\",null],\"parent\":null},\"gone\":null}}");
		List<String> notes = new ArrayList<>();
		Map<String, Object> properties = ModTranslator.modProperties(md, notes);
		assertEquals(Map.of("sodium:options", Map.of("mixin.features.render.world.sky", false), "jade", Map.of("disableItemModNameTooltip", true),
				"modmenu", Map.of("links", List.of("a")), "sodium:config_api_user", "com.example.Page"), properties, "nulls left out");
		assertEquals(List.of("Sodium for NeoForge takes one sodium:config_api_user class per mod: com.example.Page is used, the others are left out"), notes);
		assertEquals(Map.of("sodium:options", Map.of("mixin.features.render.world.sky", false)), ModTranslator.options(md));

		var kotlin = FabricModJsonParser.parse("{\"schemaVersion\":1,\"id\":\"aaa\",\"version\":\"1\","
				+ "\"entrypoints\":{\"sodium:config_api_user\":[{\"adapter\":\"kotlin\",\"value\":\"com.example.Page\"}]}}");
		notes.clear();
		assertEquals(Map.of(), ModTranslator.modProperties(kotlin, notes));
		assertEquals(1, notes.size(), notes.toString());
	}

	/** The overlay's mods.toml, read by the parser NeoForge uses, has each value where its NeoForge mod reads it. */
	@Test
	void writesValuesWhereNeoForgeModsReadThem() {
		Map<String, Object> sodium = new LinkedHashMap<>();
		sodium.put("mixin.features.render.world.sky", false);
		sodium.put("mixin.core", true);
		Map<String, Object> options = new LinkedHashMap<>();
		options.put("sodium:options", sodium);
		options.put("lithium:options", Map.of("mixin.ai", false));
		options.put("a:huge", Double.POSITIVE_INFINITY); // "1e400" in fabric.mod.json
		Map<String, Object> properties = new LinkedHashMap<>(options);
		properties.put("sodium:config_api_user", "com.example.SodiumPage");
		properties.put("jade", Map.of("fastScroll", true));
		properties.put("mixed", List.of(1, 2.5, "three \"quoted\"\n", List.of(true), Map.of("k.with.dots", 9_000_000_000L)));
		properties.put("tiny", List.of(Double.NEGATIVE_INFINITY, Double.NaN, 1.5e300));
		String toml = ModsTomlWriter.write(new ModsTomlWriter.Spec("my_mod", "1.0.0", "My Mod", "", List.of(), List.of(), List.of(), null, null, null,
				List.of(), false, false, List.of(), properties, options));

		CommentedConfig file = new TomlParser().parse(toml);
		assertEquals("fabricated", file.get("modLoader"));
		assertEquals(Map.of("mixin.ai", false), table(file.get(List.of("lithium:options"))), "Lithium reads the file's top level");
		Config mod = file.<List<Config>>get("mods").getFirst();
		assertEquals("my_mod", mod.get("modId"));
		assertEquals(sodium, table(mod.get(List.of("sodium:options"))), "Sodium reads the mod's entry");
		Config modProperties = file.get(List.of("modproperties", "my_mod"));
		assertEquals("com.example.SodiumPage", modProperties.get(List.of("sodium:config_api_user")));
		assertEquals(Map.of("fastScroll", true), table(modProperties.get(List.of("jade"))));
		List<?> mixed = modProperties.get(List.of("mixed"));
		assertEquals(List.of(1, 2.5, "three \"quoted\"\n", List.of(true)), mixed.subList(0, 4));
		assertEquals(Map.of("k.with.dots", 9_000_000_000L), table(mixed.get(4)));
		assertEquals(Double.POSITIVE_INFINITY, file.<Double>get(List.of("a:huge")), "a number past a double's range still parses");
		assertEquals(List.of(Double.NEGATIVE_INFINITY, Double.NaN, 1.5e300), modProperties.get(List.of("tiny")));
	}

	private static Map<String, Object> table(Object config) {
		Map<String, Object> table = new LinkedHashMap<>();
		for (UnmodifiableConfig.Entry entry : ((UnmodifiableConfig) config).entrySet()) table.put(entry.getKey(), entry.getValue());
		return table;
	}

	@Test
	void computesFabricMixinBehaviourAndClampsForNeoForge() throws Exception {
		var newestMod = FabricModJsonParser.parse("{\"schemaVersion\":1,\"id\":\"aaa\",\"version\":\"1\",\"depends\":{\"fabricloader\":\">=0.20.1\"}}");
		assertEquals("0.17.5", MixinCompat.fabricBehaviorVersion(newestMod.getDependencies()));
		assertEquals("0.17.4", MixinCompat.clampForNeoForge("0.17.5", "0.17.4+mixin.0.8.7", "0.17.1"), "lowered to the running Mixin on 26.x");
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
	void knowsWhatSpongePoweredMixinReleasesTake() {
		// LEVEL-1: from the CompatibilityLevel enums of Mixin 0.8.5, 0.8.6 and 0.8.7.
		assertEquals(18, MixinCompat.lastCompatibilityLevel("0.8.5"), "Forge 47");
		assertEquals(21, MixinCompat.lastCompatibilityLevel("0.8.7"), "Forge 61 to 66");
		assertEquals(18, MixinCompat.lastCompatibilityLevel("0.8.5+Jenkins-b310.git-155314e6e91465dad727e621a569906a410cd6f4"),
				"as Forge 47 reports it, with build metadata");
		assertEquals(0, MixinCompat.lastCompatibilityLevel("0.17.4+mixin.0.8.7"), "Fabric's Mixin isn't limited here");
		assertEquals(0, MixinCompat.lastCompatibilityLevel("0.8.8"), "a later release isn't known");
		assertEquals(0, MixinCompat.lastCompatibilityLevel(null));

		// INTERFACE-1: injectors in interface mixins came with 0.8.6.
		assertFalse(MixinCompat.interfaceInjectors("0.8.5"), "Forge 47");
		assertFalse(MixinCompat.interfaceInjectors("0.8.5+Jenkins-b310.git-155314e6e91465dad727e621a569906a410cd6f4"), "as Forge 47 reports it");
		assertTrue(MixinCompat.interfaceInjectors("0.8.7"), "Forge 61 to 66");
		assertTrue(MixinCompat.interfaceInjectors("0.17.4+mixin.0.8.7"), "Fabric's Mixin");
		assertTrue(MixinCompat.interfaceInjectors(null));

		// CTOR-2: where an @Inject into a constructor goes.
		assertEquals(MixinPreflight.ConstructorInjects.RETURNS_ONLY, MixinCompat.constructorInjects("0.8.5+Jenkins-b310.git-1"), "Forge 47");
		assertEquals(MixinPreflight.ConstructorInjects.UNSAFE_POINTS, MixinCompat.constructorInjects("0.8.7+Jenkins-b321.git-4"), "Forge 61 to 66");
		assertEquals(MixinPreflight.ConstructorInjects.AFTER_DELEGATE, MixinCompat.constructorInjects("0.17.4+mixin.0.8.7"), "Fabric's Mixin");
		// ARGS-1: whether a @ModifyVariable taking arguments only knows them by their names.
		assertFalse(MixinCompat.argumentNames("0.8.5+Jenkins-b310.git-1"), "Forge 47");
		assertFalse(MixinCompat.argumentNames("0.8.7+Jenkins-b321.git-4"), "Forge 61 to 66");
		assertTrue(MixinCompat.argumentNames("0.17.4+mixin.0.8.7"), "Fabric's Mixin");
		// LAMBDA-6: whether an @Inject handler must be static exactly where its target is.
		assertTrue(MixinCompat.exactStaticInjects("0.8.5+Jenkins-b310.git-1"), "Forge 47");
		assertFalse(MixinCompat.exactStaticInjects("0.8.7+Jenkins-b321.git-4"), "Forge 61 to 66");
		assertFalse(MixinCompat.exactStaticInjects("0.17.4+mixin.0.8.7"), "Fabric's Mixin");
		assertFalse(MixinCompat.exactStaticInjects(null));
		// SUPER-1: whether a detached mixin's calls to an interface's super method are rebound to the target.
		assertTrue(MixinCompat.rebindsInterfaceSuperCalls("0.8.5+Jenkins-b310.git-1"), "Forge 47");
		assertTrue(MixinCompat.rebindsInterfaceSuperCalls("0.8.7+Jenkins-b321.git-4"), "Forge 61 to 66");
		assertFalse(MixinCompat.rebindsInterfaceSuperCalls("0.17.4+mixin.0.8.7"), "Fabric's Mixin, from 0.15.0");
		assertFalse(MixinCompat.rebindsInterfaceSuperCalls(null));

		assertEquals(25, MixinCompat.compatibilityLevel(" java_25 "), "as Mixin reads it");
		assertEquals(8, MixinCompat.compatibilityLevel("JAVA_8"));
		assertEquals(0, MixinCompat.compatibilityLevel("JAVA_LATEST"));
		assertEquals(0, MixinCompat.compatibilityLevel(null));
		assertEquals(0, MixinCompat.compatibilityLevel(25));
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
