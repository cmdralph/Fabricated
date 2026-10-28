package io.github.cmdralph.fabricated.core.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

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
}
