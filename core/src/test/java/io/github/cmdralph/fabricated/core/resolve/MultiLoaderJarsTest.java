package io.github.cmdralph.fabricated.core.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
