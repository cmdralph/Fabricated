package io.github.cmdralph.fabricated.core.resolve;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import io.github.cmdralph.fabricated.core.archive.ArchiveLimits;
import io.github.cmdralph.fabricated.core.testutil.TestJars;

class MultiLoaderJarsTest {
	private static final String ENTRYPOINTS = "\"entrypoints\":{\"main\":[\"com.example.Init\"]}";
	private static final String JAVA_MOD = "modLoader = \"javafml\"\nloaderVersion = \"[1,)\"\n[[mods]]\nmodId = \"example\"\n";

	@Test
	void aFabricBuildThatKeptItsNeoForgeBuildsMetadataIsFabrics(@TempDir Path dir) throws Exception {
		// MULTI-1: Do a Barrel Roll's 26.x Fabric jars declare a NeoForge mod, with no class for NeoForge to start.
		Path jar = TestJars.create().fabricModJson(TestJars.modJson("example", "1.0", ENTRYPOINTS))
				.text(MultiLoaderJars.MODS_TOML, JAVA_MOD)
				.bytes("com/example/Init.class", TestJars.emptyClass("com/example/Init", "java/lang/Object"))
				.write(dir.resolve("fabric-build.jar"));
		assertFalse(MultiLoaderJars.forNeoForge(jar, ArchiveLimits.DEFAULTS));
	}

	@Test
	void aJarWithANeoForgeModClassOrNothingForFabricToStartIsNeoForges(@TempDir Path dir) throws Exception {
		Path withModClass = TestJars.create().fabricModJson(TestJars.modJson("example", "1.0", ENTRYPOINTS))
				.text(MultiLoaderJars.MODS_TOML, JAVA_MOD)
				.bytes("com/example/Init.class", TestJars.emptyClass("com/example/Init", "java/lang/Object"))
				.bytes("com/example/NeoForgeInit.class", modClass("com/example/NeoForgeInit"))
				.write(dir.resolve("multi-loader.jar"));
		assertTrue(MultiLoaderJars.forNeoForge(withModClass, ArchiveLimits.DEFAULTS));

		Path mixinsOnly = TestJars.create().fabricModJson(TestJars.modJson("example", "1.0", ""))
				.text(MultiLoaderJars.MODS_TOML, JAVA_MOD)
				.write(dir.resolve("mixins-only.jar"));
		assertTrue(MultiLoaderJars.forNeoForge(mixinsOnly, ArchiveLimits.DEFAULTS), "the same mixins load either way");

		Path otherLoader = TestJars.create().fabricModJson(TestJars.modJson("example", "1.0", ENTRYPOINTS))
				.text(MultiLoaderJars.MODS_TOML, "modLoader = \"kotlinforforge\"\nloaderVersion = \"[5,)\"\n")
				.write(dir.resolve("kotlin.jar"));
		assertTrue(MultiLoaderJars.forNeoForge(otherLoader, ArchiveLimits.DEFAULTS), "another language loader starts it");

		Path fabricOnly = TestJars.create().fabricModJson(TestJars.modJson("example", "1.0", ENTRYPOINTS)).write(dir.resolve("fabric.jar"));
		assertFalse(MultiLoaderJars.forNeoForge(fabricOnly, ArchiveLimits.DEFAULTS));
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
}
