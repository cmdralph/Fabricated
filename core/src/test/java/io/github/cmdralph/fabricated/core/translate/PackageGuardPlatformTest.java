package io.github.cmdralph.fabricated.core.translate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.cmdralph.fabricated.core.archive.ArchiveLimits;
import io.github.cmdralph.fabricated.core.bytecode.ClassBytesSource;
import io.github.cmdralph.fabricated.core.metadata.FabricModJsonParser;
import io.github.cmdralph.fabricated.core.resolve.Candidate;
import io.github.cmdralph.fabricated.core.testutil.TestJars;

/**
 * A mod's own service files on each platform ({@link PlatformDescriptor.ServiceFiles}): NeoForge 21.1 reads them as they
 * are, so one it would refuse is left out; Forge declares what the package guard decided ({@link PackageGuard.Decision#provides}),
 * read as ServiceLoader reads it on Fabric's class path; NeoForge on FML 10 and later reads them itself as ServiceLoader
 * does, and they stay as they are.
 */
class PackageGuardPlatformTest {
	private static final String INLINE = "META-INF/services/com.api.Inline";
	private static final String BYTES = "META-INF/services/com.api.Bytes";
	private static final String LINES = "META-INF/services/com.api.Lines";
	private static final String NAMED = "META-INF/services/com.api.Bridge-v2";
	private static final String GOOD = "META-INF/services/com.api.Good";

	/** NeoForge 21.1: an own file SecureJarHandler would refuse is left out, so the module is built; a clean one stays. */
	@Test
	void anOwnFileThePlatformReadsAsItIsAndWouldRefuseIsLeftOut(@TempDir Path dir) throws Exception {
		PackageGuard.Decision solo = check(PlatformDescriptor.ServiceFiles.AS_THEY_ARE, solo(dir)).get("solo");
		for (String file : List.of(INLINE, BYTES, LINES, NAMED)) {
			assertTrue(solo.hides(file), file);
			assertTrue(solo.notes().contains("has the service file " + file + ", which the platform reads as it is and would refuse (a class name with a"
					+ " comment after it, a name that isn't a class's, or bytes that aren't UTF-8); the service file is left out, so that its module is built,"
					+ " without the service"), solo.notes().toString());
		}
		assertFalse(solo.hides(GOOD));
	}

	/**
	 * Forge: every own file stays in the jar, and the module declares what ServiceLoader reads of it: a comment cut, a line
	 * naming no class left out, a file not named as a class not declared, each with a note.
	 */
	@Test
	void whereFabricatedDeclaresTheServicesTheyAreReadAsServiceLoaderReadsThem(@TempDir Path dir) throws Exception {
		PackageGuard.Decision solo = check(PlatformDescriptor.ServiceFiles.FROM_THE_GUARD, solo(dir)).get("solo");
		for (String file : List.of(INLINE, BYTES, LINES, NAMED, GOOD)) assertFalse(solo.hides(file), file);
		assertEquals(Map.of("com.api.Inline", List.of("com.s.Impl"), "com.api.Bytes", List.of("com.s.Impl"), "com.api.Lines", List.of("com.s.Impl", "com.s.Other"),
				"com.api.Good", List.of("com.s.Impl")), solo.provides());
		assertTrue(solo.notes().contains("has the service file " + NAMED + ", whose name isn't a class's directly in META-INF/services/; its module doesn't"
				+ " declare it, as Java's modules take only class names"), solo.notes().toString());
		assertTrue(solo.notes().contains("declares the service com.api.Lines with the line not a class, which isn't a class name; its module declares the"
				+ " service without it, as Java's modules take only class names"), solo.notes().toString());
	}

	/**
	 * NeoForge on FML 10 and later: own files stay as they are, as the platform cuts comments and skips a file not named as
	 * a class; one with a line naming no class is left out, as it names no provider the module has.
	 */
	@Test
	void whereThePlatformReadsThemAsServiceLoaderDoesTheyStay(@TempDir Path dir) throws Exception {
		PackageGuard.Decision solo = check(PlatformDescriptor.ServiceFiles.AS_SERVICE_LOADER_DOES, solo(dir)).get("solo");
		for (String file : List.of(INLINE, BYTES, NAMED, GOOD)) assertFalse(solo.hides(file), file);
		assertTrue(solo.hides(LINES));
		assertEquals(List.of("declares the service com.api.Lines with the line not a class, which isn't a class name; the service file is left out, as a"
				+ " module can only declare its own providers"), solo.notes().stream().filter(n -> n.contains("service")).toList());
	}

	/** What a module declares includes the files it serves from other jars: Forge declares a moved file in the module that has its classes. */
	@Test
	void aMovedFileIsWhatItsNewModuleProvides(@TempDir Path dir) throws Exception {
		TranslatedMod outer = mod(dir, "outer", null, TestJars.create().text("META-INF/services/com.api.Bridge", "com.inner.BridgeImpl\n"));
		TranslatedMod inner = mod(dir, "inner", "outer", TestJars.create().bytes("com/inner/BridgeImpl.class", cls("com/inner/BridgeImpl")));
		Map<String, PackageGuard.Decision> decisions = check(PlatformDescriptor.ServiceFiles.FROM_THE_GUARD, List.of(outer, inner));
		assertEquals(Map.of("com.api.Bridge", List.of("com.inner.BridgeImpl")), decisions.get("inner").provides());
		assertEquals(Map.of(), decisions.get("outer").provides());
	}

	/** One mod with five service files of its own provider: an inline comment, Latin-1 bytes, a line naming no class, a name not a class's, a clean one. */
	private static List<TranslatedMod> solo(Path dir) throws Exception {
		return List.of(mod(dir, "solo", null, TestJars.create()
				.bytes("com/s/Impl.class", cls("com/s/Impl"))
				.bytes("com/s/Other.class", cls("com/s/Other"))
				.text(INLINE, "com.s.Impl # the impl\n")
				.bytes(BYTES, "# café\ncom.s.Impl\n".getBytes(StandardCharsets.ISO_8859_1))
				.text(LINES, "com.s.Impl\nnot a class\ncom.s.Other\n")
				.text(NAMED, "com.s.Impl\n")
				.text(GOOD, "com.s.Impl\n")));
	}

	private static Map<String, PackageGuard.Decision> check(PlatformDescriptor.ServiceFiles serviceFiles, List<TranslatedMod> mods) {
		PackageGuard.Result result = new PackageGuard(ArchiveLimits.DEFAULTS, serviceFiles, List.of(), List.of()).check(mods, Set.of(), ClassBytesSource.EMPTY);
		assertEquals(List.of(), result.conflicts());
		return result.decisions();
	}

	private static byte[] cls(String internalName) {
		return TestJars.emptyClass(internalName, "java/lang/Object");
	}

	private static TranslatedMod mod(Path dir, String id, String parent, TestJars jar) throws Exception {
		String json = TestJars.modJson(id, "1.0", "");
		Path file = jar.fabricModJson(json).write(dir.resolve(id + ".jar"));
		Candidate c = new Candidate(file, id + "-hash", FabricModJsonParser.parse(json), parent == null ? id + ".jar" : parent + ".jar!/" + id + ".jar", parent,
				parent == null ? 0 : 1, List.of(), new HashSet<>());
		return new TranslatedMod(c, id, file, dir.resolve(id + "-overlay.jar"), List.of(), 0, List.of(), List.of(), List.of(), Set.of(), Set.of(), Set.of(),
				Map.of(), false, 0);
	}
}
