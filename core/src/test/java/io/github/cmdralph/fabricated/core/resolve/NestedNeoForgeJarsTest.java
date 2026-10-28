package io.github.cmdralph.fabricated.core.resolve;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.cmdralph.fabricated.core.archive.ArchiveLimits;
import io.github.cmdralph.fabricated.core.testutil.TestJars;

class NestedNeoForgeJarsTest {
	private static final String TOML = "modLoader=\"javafml\"\n[[mods]]\nmodId=\"%s\"\nversion=\"${file.jarVersion}\"\n";

	private static String jarJar(String... paths) {
		StringBuilder jars = new StringBuilder();
		for (String path : paths) {
			if (!jars.isEmpty()) jars.append(',');
			jars.append("{\"identifier\":{\"group\":\"g\",\"artifact\":\"a\"},\"version\":{\"range\":\"[1,)\",\"artifactVersion\":\"2.0.0\"},\"path\":\"")
					.append(path).append("\",\"isObfuscated\":false}");
		}
		return "{\"jars\":[" + jars + "]}";
	}

	/** Sodium for NeoForge: a service jar with no mods of its own, nesting the mod. */
	@Test
	void readsTheModsTomlOfEveryListedNestedJar(@TempDir Path dir) throws Exception {
		byte[] deepest = TestJars.create().text("META-INF/neoforge.mods.toml", TOML.formatted("deep")).toBytes();
		byte[] mod = TestJars.create()
				.text("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\nImplementation-Version: 0.9.2+mc26.3\n")
				.text("META-INF/neoforge.mods.toml", TOML.formatted("sodium"))
				.bytes("net/caffeinemc/Renderer.class", TestJars.emptyClass("net/caffeinemc/Renderer", "java/lang/Object"))
				.text(NestedNeoForgeJars.METADATA, jarJar("META-INF/jarjar/deep.jar"))
				.bytes("META-INF/jarjar/deep.jar", deepest)
				.toBytes();
		byte[] library = TestJars.create().text("lib/Thing.txt", "not a mod").toBytes();
		Path service = TestJars.create()
				.text(NestedNeoForgeJars.METADATA, jarJar("META-INF/jarjar/sodium-mod.jar", "META-INF/jarjar/library.jar", "META-INF/jarjar/missing.jar"))
				.bytes("META-INF/jarjar/sodium-mod.jar", mod)
				.bytes("META-INF/jarjar/library.jar", library)
				.bytes("META-INF/jarjar/unlisted.jar", mod)
				.write(dir.resolve("sodium-neoforge.jar"));

		List<NestedNeoForgeJars.ModsToml> found = NestedNeoForgeJars.read(service, ArchiveLimits.DEFAULTS);

		assertEquals(List.of("sodium-neoforge.jar!/META-INF/jarjar/sodium-mod.jar",
				"sodium-neoforge.jar!/META-INF/jarjar/sodium-mod.jar!/META-INF/jarjar/deep.jar"), found.stream().map(NestedNeoForgeJars.ModsToml::origin).toList());
		assertTrue(found.get(0).text().contains("modId=\"sodium\""));
		assertEquals("0.9.2+mc26.3", found.get(0).jarVersion(), "from the nested jar's manifest");
		assertEquals("2.0.0", found.get(1).jarVersion(), "from the jar-in-jar list, without a manifest version");

		// Fabric mixins may target the nested mod's classes: they are listed, and read in place.
		assertEquals(Set.of("net/caffeinemc/Renderer"), found.get(0).classes());
		assertEquals(List.of("META-INF/jarjar/sodium-mod.jar", "META-INF/jarjar/deep.jar"), found.get(1).chain());
		assertArrayEquals(TestJars.emptyClass("net/caffeinemc/Renderer", "java/lang/Object"),
				NestedNeoForgeJars.readClass(service, found.get(0).chain(), "net/caffeinemc/Renderer", ArchiveLimits.DEFAULTS));
		assertNull(NestedNeoForgeJars.readClass(service, found.get(1).chain(), "net/caffeinemc/Renderer", ArchiveLimits.DEFAULTS));
	}

	/** Kotlin for Forge: its language provider nests Kotlin's libraries, which NeoForge loads above the game's mods. */
	@Test
	void readsThePackagesOfTheNestedLibraries(@TempDir Path dir) throws Exception {
		byte[] coroutines = TestJars.create()
				.bytes("kotlinx/coroutines/Job.class", TestJars.emptyClass("kotlinx/coroutines/Job", "java/lang/Object"))
				.bytes("META-INF/versions/9/module-info.class", new byte[0])
				.toBytes();
		byte[] stdlib = TestJars.create()
				.bytes("kotlin/Unit.class", TestJars.emptyClass("kotlin/Unit", "java/lang/Object"))
				.bytes("kotlin/collections/ArraysKt.class", TestJars.emptyClass("kotlin/collections/ArraysKt", "java/lang/Object"))
				.text(NestedNeoForgeJars.METADATA, jarJar("META-INF/jarjar/coroutines.jar"))
				.bytes("META-INF/jarjar/coroutines.jar", coroutines)
				.toBytes();
		byte[] mod = TestJars.create()
				.text("META-INF/neoforge.mods.toml", TOML.formatted("kotlinforforge"))
				.bytes("thedarkcolour/kotlinforforge/KotlinForForge.class", TestJars.emptyClass("thedarkcolour/kotlinforforge/KotlinForForge", "java/lang/Object"))
				.toBytes();
		Path root = TestJars.create()
				.text(NestedNeoForgeJars.METADATA, jarJar("META-INF/jarjar/stdlib.jar", "META-INF/jarjar/mod.jar"))
				.bytes("META-INF/jarjar/stdlib.jar", stdlib)
				.bytes("META-INF/jarjar/mod.jar", mod)
				.write(dir.resolve("kotlinforforge-all.jar"));

		NestedNeoForgeJars.Contents contents = NestedNeoForgeJars.readAll(root, ArchiveLimits.DEFAULTS);

		assertEquals(Set.of("kotlin", "kotlin/collections", "kotlinx/coroutines"), contents.libraryPackages(), "a mod jar's packages are its mod's");
		assertEquals(List.of("kotlinforforge-all.jar!/META-INF/jarjar/mod.jar"), contents.mods().stream().map(NestedNeoForgeJars.ModsToml::origin).toList());
		assertEquals(contents.mods(), NestedNeoForgeJars.read(root, ArchiveLimits.DEFAULTS));
	}

	@Test
	void nestsNoDeeperThanTheLimits(@TempDir Path dir) throws Exception {
		byte[] level2 = TestJars.create().text("META-INF/neoforge.mods.toml", TOML.formatted("level2")).toBytes();
		byte[] level1 = TestJars.create().text("META-INF/neoforge.mods.toml", TOML.formatted("level1"))
				.text(NestedNeoForgeJars.METADATA, jarJar("META-INF/jarjar/level2.jar")).bytes("META-INF/jarjar/level2.jar", level2).toBytes();
		Path root = TestJars.create().text(NestedNeoForgeJars.METADATA, jarJar("META-INF/jarjar/level1.jar")).bytes("META-INF/jarjar/level1.jar", level1)
				.write(dir.resolve("root.jar"));
		ArchiveLimits oneLevel = new ArchiveLimits(256L << 20, 64L << 20, 512L << 20, 65_536, 100, 1);

		assertEquals(1, NestedNeoForgeJars.read(root, oneLevel).size());
		assertEquals(2, NestedNeoForgeJars.read(root, ArchiveLimits.DEFAULTS).size());
	}

	@Test
	void refusesNestedJarsOverTheEntryLimit(@TempDir Path dir) throws Exception {
		byte[] padding = new byte[4096];
		new Random(1).nextBytes(padding);
		byte[] big = TestJars.create().text("META-INF/neoforge.mods.toml", TOML.formatted("big")).bytes("padding.bin", padding).toBytes();
		Path root = TestJars.create().text(NestedNeoForgeJars.METADATA, jarJar("META-INF/jarjar/big.jar")).bytes("META-INF/jarjar/big.jar", big)
				.write(dir.resolve("root.jar"));
		ArchiveLimits small = new ArchiveLimits(256L << 20, 1024, 512L << 20, 65_536, 100, 3);

		IOException e = assertThrows(IOException.class, () -> NestedNeoForgeJars.read(root, small));
		assertTrue(e.getMessage().contains("root.jar!/META-INF/jarjar/big.jar is larger than 1024 bytes"), e.getMessage());
	}

	@Test
	void aFileWithoutNestedJarsHasNone(@TempDir Path dir) throws Exception {
		Path plain = TestJars.create().text("META-INF/neoforge.mods.toml", TOML.formatted("plain")).write(dir.resolve("plain.jar"));
		assertEquals(List.of(), NestedNeoForgeJars.read(plain, ArchiveLimits.DEFAULTS));
	}
}
