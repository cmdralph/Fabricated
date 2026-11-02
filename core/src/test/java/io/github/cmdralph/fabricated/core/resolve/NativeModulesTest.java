package io.github.cmdralph.fabricated.core.resolve;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Manifest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.cmdralph.fabricated.core.Pipeline;
import io.github.cmdralph.fabricated.core.archive.ArchiveLimits;
import io.github.cmdralph.fabricated.core.archive.DeterministicJarWriter;
import io.github.cmdralph.fabricated.core.archive.SafeArchive;
import io.github.cmdralph.fabricated.core.json.Json;
import io.github.cmdralph.fabricated.core.mixin.MixinConfigFile;

class NativeModulesTest {
	private static final String INDEX = """
			{"fabricApiVersion": "0.116.17+1.21.1", "modules": {
			  "fabric-api-base": ["net/fabricmc/fabric/api/event", "net/fabricmc/fabric/impl/base/event"],
			  "fabric-screen-handler-api-v1": ["net/fabricmc/fabric/api/screenhandler/v1"],
			  "fabric-transfer-api-v1": ["net/fabricmc/fabric/api/transfer/v1/item", "net/fabricmc/fabric/mixin/transfer"]
			}}""";
	private static final String MIXIN_MODULES = """
			{"configs": {"fabricated-renderer.mixins.json": ["fabric-renderer-api-v1"]},
			 "mixins": {"MenuProviderMixin": ["fabric-menu-api-v1", "fabric-screen-handler-api-v1"], "MappedRegistryPendingHoldersMixin": [],
			            "client.MinecraftEntrypointsMixin": []}}""";

	private static Map<String, MixinConfigFile> configs() throws Exception {
		Map<String, MixinConfigFile> configs = new LinkedHashMap<>();
		configs.put("fabricated.mixins.json", MixinConfigFile.parse("""
				{"package": "io.github.cmdralph.fabricated.neoforge.mixin", "mixins": ["MenuProviderMixin", "MappedRegistryPendingHoldersMixin", "NewMixin"],
				 "client": ["client.MinecraftEntrypointsMixin"]}"""));
		configs.put("fabricated-transfer.mixins.json", MixinConfigFile.parse("{\"package\": \"net.fabricmc.fabric.mixin.transfer\", \"mixins\": [\"ItemMixin\"]}"));
		configs.put("fabricated-renderer.mixins.json", MixinConfigFile.parse("{\"package\": \"io.github.cmdralph.fabricated.neoforge.rendering.mixin\", \"mixins\": []}"));
		return configs;
	}

	@Test
	void aProvidedModuleStandsDownWithItsPackagesConfigsAndMixins() throws Exception {
		NativeModules modules = NativeModules.parse(INDEX, MIXIN_MODULES);
		assertEquals("fabric-transfer-api-v1", modules.owner("net/fabricmc/fabric/mixin/transfer"));
		assertNull(modules.owner("net/fabricmc/fabric/api"));

		NativeModules.StandDown standDown = modules.standDown(Set.of("fabric-screen-handler-api-v1", "fabric-transfer-api-v1", "fabric-renderer-api-v1"), configs());
		assertEquals(Set.of("net/fabricmc/fabric/api/screenhandler/v1", "net/fabricmc/fabric/api/transfer/v1/item", "net/fabricmc/fabric/mixin/transfer"),
				standDown.packages());
		assertEquals(Set.of("fabricated-transfer.mixins.json", "fabricated-renderer.mixins.json"), standDown.mixinConfigs());
		assertEquals(Map.of("fabricated.mixins.json", Set.of("MenuProviderMixin")), standDown.mixins());
		assertEquals(Set.of("fabricated.mixins.json:NewMixin"), standDown.unmapped(), "a mixin no data file maps is reported, and stays");
	}

	@Test
	void nothingStandsDownWithoutAProvider() throws Exception {
		NativeModules.StandDown standDown = NativeModules.parse(INDEX, MIXIN_MODULES).standDown(Set.of(), configs());
		assertTrue(standDown.isEmpty());
	}

	@Test
	void theModsTomlLosesOnlyTheDroppedConfigsEntries() {
		String toml = """
				modLoader="javafml"

				[[mixins]]
				config="fabricated.mixins.json"

				# Comment kept.
				[[mixins]]
				config="fabricated-renderer.mixins.json"
				requiredMods=["fabric_renderer_api_v1"]

				[[mixins]]
				config="fabricated-transfer.mixins.json"
				[[accessTransformers]]
				file="META-INF/accesstransformer.cfg"
				""";
		assertEquals("""
				modLoader="javafml"

				[[mixins]]
				config="fabricated.mixins.json"

				# Comment kept.

				[[accessTransformers]]
				file="META-INF/accesstransformer.cfg"
				""", NativeModules.withoutMixinConfigs(toml, Set.of("fabricated-renderer.mixins.json", "fabricated-transfer.mixins.json")));
	}

	/** A mod bundling a module's packages under its own id provides the module; one with the module's id already does. */
	@Test
	void aModWithAModulesPackagesProvidesIt() throws Exception {
		NativeModules modules = NativeModules.parse(INDEX, MIXIN_MODULES);
		Pipeline.NeoForgeMod bundler = new Pipeline.NeoForgeMod("bundler", "1.0.0", "Bundler", Map.of(), List.of(Path.of("bundler.jar")));
		Pipeline.NeoForgeMod module = new Pipeline.NeoForgeMod("fabric_api_base", "1.0.0", "Fabric API Base", Map.of(), List.of(Path.of("base.jar")));
		Pipeline.NeoForgeMod other = new Pipeline.NeoForgeMod("other", "1.0.0", "Other", Map.of(), List.of(Path.of("other.jar")));
		List<NativeModules.Provider> providers = List.of(
				new NativeModules.Provider(bundler, "bundler.jar!/META-INF/jarjar/screens.jar", Set.of("net/fabricmc/fabric/api/screenhandler/v1", "bundler")),
				new NativeModules.Provider(module, "base.jar", Set.of("net/fabricmc/fabric/api/event")),
				new NativeModules.Provider(other, "other.jar", Set.of("other")));
		List<String> notes = new ArrayList<>();

		List<Pipeline.NeoForgeMod> mods = modules.providingByPackages(List.of(bundler, module, other), providers, notes);

		assertEquals(List.of("fabric-screen-handler-api-v1"), mods.get(0).provides());
		assertSame(module, mods.get(1));
		assertSame(other, mods.get(2));
		assertEquals(List.of("bundler.jar!/META-INF/jarjar/screens.jar has the package net/fabricmc/fabric/api/screenhandler/v1 of fabric-screen-handler-api-v1:"
				+ " Bundler provides that module"), notes);
		List<Pipeline.NeoForgeMod> none = List.of(other);
		assertSame(none, modules.providingByPackages(none, List.of(providers.get(2)), notes), "no copy of a module's packages, no change");
	}

	/** Forge reads a mod's mixin configs from its manifest: only those that stand down leave it. */
	@Test
	void theManifestLosesOnlyTheDroppedMixinConfigs() throws Exception {
		byte[] manifest = ("Manifest-Version: 1.0\r\nImplementation-Title: Fabricated (in-game mod)\r\n"
				+ "MixinConfigs: fabricated-forge.mixins.json,fabricated-loot.mixins.json, fabricated-forge-loot.mixins.json\r\n\r\n")
				.getBytes(StandardCharsets.UTF_8);

		Manifest kept = new Manifest(new ByteArrayInputStream(NativeModules.withoutManifestMixinConfigs(manifest,
				Set.of("fabricated-loot.mixins.json", "fabricated-forge-loot.mixins.json"))));
		assertEquals("fabricated-forge.mixins.json", kept.getMainAttributes().getValue(NativeModules.MIXIN_CONFIGS));
		assertEquals("Fabricated (in-game mod)", kept.getMainAttributes().getValue("Implementation-Title"));

		Manifest none = new Manifest(new ByteArrayInputStream(NativeModules.withoutManifestMixinConfigs(manifest,
				Set.of("fabricated-forge.mixins.json", "fabricated-loot.mixins.json", "fabricated-forge-loot.mixins.json"))));
		assertNull(none.getMainAttributes().getValue(NativeModules.MIXIN_CONFIGS));
		assertArrayEquals(manifest, NativeModules.withoutManifestMixinConfigs(manifest, Set.of()));
	}

	/**
	 * Forge loads the in-game mod from one path: once parts of it stand down, the jar handed over has neither their
	 * packages nor their mixin configs, its manifest names the configs that stay, and those keep only the mixins that do.
	 */
	@Test
	void theInGameModsJarLeavesOutWhatStandsDown(@TempDir Path dir) throws Exception {
		Map<String, byte[]> original = new LinkedHashMap<>();
		original.put("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\nMixinConfigs: fabricated.mixins.json,fabricated-transfer.mixins.json\r\n\r\n"
				.getBytes(StandardCharsets.UTF_8));
		original.put("fabricated.mixins.json", ("{\"package\": \"io.github.cmdralph.fabricated.neoforge.mixin\", \"mixins\": [\"MenuProviderMixin\","
				+ " \"MappedRegistryPendingHoldersMixin\"]}").getBytes(StandardCharsets.UTF_8));
		original.put("fabricated-transfer.mixins.json", "{\"package\": \"net.fabricmc.fabric.mixin.transfer\", \"mixins\": [\"ItemMixin\"]}"
				.getBytes(StandardCharsets.UTF_8));
		original.put("net/fabricmc/fabric/api/event/Event.class", new byte[] {1});
		original.put("net/fabricmc/fabric/mixin/transfer/ItemMixin.class", new byte[] {2});
		original.put("io/github/cmdralph/fabricated/neoforge/mixin/MenuProviderMixin.class", new byte[] {3});
		original.put("META-INF/jarjar/metadata.json", "{\"jars\": []}".getBytes(StandardCharsets.UTF_8));
		Path jar = dir.resolve("game-mod.jar");
		DeterministicJarWriter.write(jar, original);
		Map<String, MixinConfigFile> configs = new LinkedHashMap<>();
		configs.put("fabricated.mixins.json", MixinConfigFile.parse(new String(original.get("fabricated.mixins.json"), StandardCharsets.UTF_8)));
		configs.put("fabricated-transfer.mixins.json", MixinConfigFile.parse(new String(original.get("fabricated-transfer.mixins.json"), StandardCharsets.UTF_8)));

		Map<String, byte[]> files;
		try (SafeArchive archive = SafeArchive.open(jar, ArchiveLimits.DEFAULTS)) {
			NativeModules.StandDown standDown = NativeModules.parse(INDEX, MIXIN_MODULES).standDown(Set.of("fabric-transfer-api-v1", "fabric-screen-handler-api-v1"), configs);
			files = NativeModules.withoutStandingDown(archive, standDown, configs);
		}

		assertEquals(Set.of("META-INF/MANIFEST.MF", "fabricated.mixins.json", "net/fabricmc/fabric/api/event/Event.class",
				"io/github/cmdralph/fabricated/neoforge/mixin/MenuProviderMixin.class", "META-INF/jarjar/metadata.json"), files.keySet());
		Manifest manifest = new Manifest(new ByteArrayInputStream(files.get("META-INF/MANIFEST.MF")));
		assertEquals("fabricated.mixins.json", manifest.getMainAttributes().getValue(NativeModules.MIXIN_CONFIGS));
		assertEquals(List.of("MappedRegistryPendingHoldersMixin"),
				Json.asList(Json.parseObject(new String(files.get("fabricated.mixins.json"), StandardCharsets.UTF_8)).get("mixins")));
		assertArrayEquals(original.get("META-INF/jarjar/metadata.json"), files.get("META-INF/jarjar/metadata.json"));
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		DeterministicJarWriter.write(out, files);
		assertTrue(out.size() > 0);
	}
}
