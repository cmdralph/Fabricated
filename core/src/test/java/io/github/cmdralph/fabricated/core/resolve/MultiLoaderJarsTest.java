package io.github.cmdralph.fabricated.core.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import io.github.cmdralph.fabricated.core.archive.ArchiveLimits;
import io.github.cmdralph.fabricated.core.testutil.TestJars;

class MultiLoaderJarsTest {
	private static final String ENTRYPOINTS = "\"entrypoints\":{\"main\":[\"com.example.Init\"]}";
	private static final String JAVA_MOD = "modLoader = \"javafml\"\nloaderVersion = \"[1,)\"\n[[mods]]\nmodId = \"example\"\n";

	/** Whether NeoForge loads the jar as its own mod. */
	private static boolean forNeoForge(Path jar, ArchiveLimits limits) {
		return MultiLoaderJars.fabricReason(jar, limits) == null;
	}

	@Test
	void aFabricBuildThatKeptItsNeoForgeBuildsMetadataIsFabrics(@TempDir Path dir) throws Exception {
		// MULTI-1: Do a Barrel Roll's 26.x Fabric jars declare a NeoForge mod, with no class for NeoForge to start.
		Path jar = TestJars.create().fabricModJson(TestJars.modJson("example", "1.0", ENTRYPOINTS))
				.text(MultiLoaderJars.MODS_TOML, JAVA_MOD)
				.bytes("com/example/Init.class", TestJars.emptyClass("com/example/Init", "java/lang/Object"))
				.write(dir.resolve("fabric-build.jar"));
		assertFalse(forNeoForge(jar, ArchiveLimits.DEFAULTS));
	}

	@Test
	void aConnectorPlaceholderIsAFabricMod(@TempDir Path dir) throws Exception {
		// Continuity's NeoForge jar: its Fabric build, with NeoForge metadata telling NeoForge users to install Connector.
		// A class with NeoForge's @Mod doesn't change that: the placeholder says the NeoForge side isn't the mod.
		Path jar = TestJars.create().fabricModJson(TestJars.modJson("example", "1.0", ENTRYPOINTS))
				.text(MultiLoaderJars.MODS_TOML, JAVA_MOD.replace("[[mods]]", "[properties]\n\"connector:placeholder\" = true\n\n[[mods]]"))
				.bytes("com/example/NeoForgeInit.class", modClass("com/example/NeoForgeInit"))
				.write(dir.resolve("placeholder.jar"));
		assertEquals(MultiLoaderJars.PLACEHOLDER, MultiLoaderJars.fabricReason(jar, ArchiveLimits.DEFAULTS));
		assertFalse(forNeoForge(jar, ArchiveLimits.DEFAULTS));
	}

	/**
	 * MULTI-2: Origins 1.10.2's Fabric jar ships Forge's {@code META-INF/mods.toml} as a placeholder for Connector
	 * ({@code "connector:placeholder"=true} in its properties, a mandatory dependency on {@code connectormod}), and Forge 47
	 * refused the launch for the missing dependency. On Forge, that placeholder alone makes such a jar a Fabric mod; any
	 * other Forge metadata, even with Fabric's and no Forge mod class, is Forge's, as before. NeoForge doesn't read the file.
	 */
	@Test
	void aConnectorPlaceholderInForgesMetadataIsAFabricMod(@TempDir Path dir) throws Exception {
		String origins = "modLoader=\"javafml\"\nloaderVersion=\"[47,)\" # 47 = Forge 1.20.1+\nlicense=\"MIT\"\n\n[properties]\n"
				+ "\"connector:placeholder\"=true\n\n[[mods]]\nmodId=\"origins\"\nversion=\"${file.jarVersion}\"\n\n[[dependencies.origins]]\n"
				+ "modId=\"connectormod\"\nmandatory=true\nversionRange=\"[1.0.0-beta.46,)\"\nordering=\"NONE\"\nside=\"BOTH\"\n";
		Path placeholder = TestJars.create().fabricModJson(TestJars.modJson("origins", "1.10.2", ENTRYPOINTS))
				.text(MultiLoaderJars.FORGE_MODS_TOML, origins)
				.write(dir.resolve("origins.jar"));
		assertEquals(MultiLoaderJars.FORGE_PLACEHOLDER, MultiLoaderJars.forgeFabricReason(placeholder, ArchiveLimits.DEFAULTS));
		assertFalse(forNeoForge(placeholder, ArchiveLimits.DEFAULTS), "NeoForge reads no mods.toml: a Fabric jar there anyway");

		Path multiLoader = TestJars.create().fabricModJson(TestJars.modJson("example", "1.0", ENTRYPOINTS))
				.text(MultiLoaderJars.FORGE_MODS_TOML, JAVA_MOD)
				.write(dir.resolve("multi-loader.jar"));
		assertNull(MultiLoaderJars.forgeFabricReason(multiLoader, ArchiveLimits.DEFAULTS), "Forge's, as nothing marks it a placeholder");

		Path forgeOnly = TestJars.create().text(MultiLoaderJars.FORGE_MODS_TOML, origins).write(dir.resolve("forge-only.jar"));
		assertNull(MultiLoaderJars.forgeFabricReason(forgeOnly, ArchiveLimits.DEFAULTS), "no Fabric metadata to load instead");

		Path neoForgePlaceholder = TestJars.create().fabricModJson(TestJars.modJson("example", "1.0", ENTRYPOINTS))
				.text(MultiLoaderJars.MODS_TOML, origins)
				.write(dir.resolve("neoforge-placeholder.jar"));
		assertNull(MultiLoaderJars.forgeFabricReason(neoForgePlaceholder, ArchiveLimits.DEFAULTS), "no Forge metadata: a plain Fabric jar on Forge");

		Path broken = Files.write(dir.resolve("broken.jar"), new byte[] {'P', 'K', 3, 4});
		assertNull(MultiLoaderJars.forgeFabricReason(broken, ArchiveLimits.DEFAULTS), "left to Forge, which reports it");
	}

	@Test
	void aJarWithANeoForgeModClassOrNothingForFabricToStartIsNeoForges(@TempDir Path dir) throws Exception {
		Path withModClass = TestJars.create().fabricModJson(TestJars.modJson("example", "1.0", ENTRYPOINTS))
				.text(MultiLoaderJars.MODS_TOML, JAVA_MOD)
				.bytes("com/example/Init.class", TestJars.emptyClass("com/example/Init", "java/lang/Object"))
				.bytes("com/example/NeoForgeInit.class", modClass("com/example/NeoForgeInit"))
				.write(dir.resolve("multi-loader.jar"));
		assertTrue(forNeoForge(withModClass, ArchiveLimits.DEFAULTS));

		Path mixinsOnly = TestJars.create().fabricModJson(TestJars.modJson("example", "1.0", ""))
				.text(MultiLoaderJars.MODS_TOML, JAVA_MOD)
				.write(dir.resolve("mixins-only.jar"));
		assertTrue(forNeoForge(mixinsOnly, ArchiveLimits.DEFAULTS), "the same mixins load either way");

		Path otherLoader = TestJars.create().fabricModJson(TestJars.modJson("example", "1.0", ENTRYPOINTS))
				.text(MultiLoaderJars.MODS_TOML, "modLoader = \"kotlinforforge\"\nloaderVersion = \"[5,)\"\n")
				.write(dir.resolve("kotlin.jar"));
		assertTrue(forNeoForge(otherLoader, ArchiveLimits.DEFAULTS), "another language loader starts it");

		Path fabricOnly = TestJars.create().fabricModJson(TestJars.modJson("example", "1.0", ENTRYPOINTS)).write(dir.resolve("fabric.jar"));
		assertFalse(forNeoForge(fabricOnly, ArchiveLimits.DEFAULTS));
	}

	/** A class with NeoForge's {@code @Mod("example")}. */
	private static byte[] modClass(String name) {
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, name, null, "java/lang/Object", null);
		var mod = cw.visitAnnotation("Lnet/neoforged/fml/common/Mod;", true);
		mod.visit("value", "example");
		mod.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** The mods.toml is untrusted: lines of blanks were each rescanned to the end, so a run of them took minutes. */
	@Test
	void manyBlankLinesAreReadQuickly(@TempDir Path dir) throws Exception {
		Path jar = TestJars.create().fabricModJson(TestJars.modJson("example", "1.0", ENTRYPOINTS))
				.text(MultiLoaderJars.MODS_TOML, "\n".repeat(200_000) + JAVA_MOD)
				.write(dir.resolve("blank-lines.jar"));
		assertTimeoutPreemptively(Duration.ofSeconds(10), () -> assertFalse(forNeoForge(jar, ArchiveLimits.DEFAULTS)));
	}
}
