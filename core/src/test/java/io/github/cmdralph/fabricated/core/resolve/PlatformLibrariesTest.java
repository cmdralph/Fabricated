package io.github.cmdralph.fabricated.core.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.cmdralph.fabricated.core.bytecode.ClassBytesSource;

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

	/** Forge 61's fmlloader jarjar_options.json, as it reads (a nested MixinExtras dependency, then the offered jar). */
	@Test
	void findsTheMixinExtrasForgeOffers() throws Exception {
		String options = """
				{"options": [{
				  "resource": "com/llamalad7/mixinextras/platform/forge/MixinExtrasConfigPlugin.class",
				  "layer": "GAMELIBRARY", "id": "mixinextras",
				  "deps": [{"identifier": {"group": "com.github.LlamaLad7", "artifact": "MixinExtras"},
				            "version": {"range": "[0.5.4,)", "artifactVersion": "0.5.4"}, "path": "", "isObfuscated": false}],
				  "meta": {"identifier": {"group": "io.github.llamalad7", "artifact": "mixinextras-forge"},
				           "version": {"artifactVersion": "0.5.4"}, "path": "", "isObfuscated": false},
				  "nested": true
				}]}""";
		assertEquals("0.5.4", PlatformLibraries.offeredMixinExtras(options));
		assertNull(PlatformLibraries.offeredMixinExtras("{\"options\": [{\"meta\": {\"identifier\": {\"group\": \"x\", \"artifact\": \"y\"},"
				+ " \"version\": {\"artifactVersion\": \"1\"}}}]}"));
		assertNull(PlatformLibraries.offeredMixinExtras("{}"));
	}

	/** Only what the platform ships: OneConfig's night-config JSON module is not NeoForge's (which has core and TOML). */
	@Test
	void providesOnlyThePlatformsModulesOfALibrary() {
		assertTrue(PlatformLibraries.isProvided("com/electronwill/nightconfig/core/file/FileConfig.class"));
		assertTrue(PlatformLibraries.isProvided("com/electronwill/nightconfig/toml/TomlFormat.class"));
		assertFalse(PlatformLibraries.isProvided("com/electronwill/nightconfig/json/JsonFormat.class"));
		assertFalse(PlatformLibraries.isProvided("com/electronwill/nightconfig/yaml/YamlFormat.class"));
	}

	/**
	 * In a library the platform ships some modules of, a mod's package is the platform's only where the platform has its
	 * classes: LWJGL's NanoVG and Netty's HTTP codec are the mod's own, the game's LWJGL core and Netty buffers aren't
	 * (PLAT-2). Whole libraries stay the platform's whether or not its loaders show them.
	 */
	@Test
	void aLibrarysModuleThePlatformLacksIsTheMods() {
		Set<String> platform = Set.of("org/lwjgl/system/MemoryUtil", "io/netty/buffer/ByteBuf");
		ClassBytesSource classes = name -> platform.contains(name) ? new byte[0] : null;
		assertFalse(PlatformLibraries.isProvided("org/lwjgl/nanovg", List.of("org/lwjgl/nanovg/NanoVG.class"), classes));
		assertFalse(PlatformLibraries.isProvided("io/netty/handler/codec/http",
				List.of("io/netty/handler/codec/http/HttpObject.class", "io/netty/handler/codec/http/mime.types"), classes));
		assertTrue(PlatformLibraries.isProvided("org/lwjgl/system",
				List.of("org/lwjgl/system/Added.class", "org/lwjgl/system/MemoryUtil.class"), classes));
		assertTrue(PlatformLibraries.isProvided("io/netty/buffer", List.of("io/netty/buffer/ByteBuf.class"), classes));
		assertTrue(PlatformLibraries.isProvided("com/google/gson", List.of("com/google/gson/Gson.class"), ClassBytesSource.EMPTY));
		assertFalse(PlatformLibraries.isProvided("com/example/lib", List.of("com/example/lib/A.class"), classes));
		assertFalse(PlatformLibraries.isProvided("com/electronwill/nightconfig/json", List.of("com/electronwill/nightconfig/json/JsonFormat.class"), classes));
		// A class the platform has but can't read is still the platform's.
		ClassBytesSource unreadable = name -> {
			throw new IOException("unreadable");
		};
		assertTrue(PlatformLibraries.isProvided("org/lwjgl/system", List.of("org/lwjgl/system/MemoryUtil.class"), unreadable));
	}
}
