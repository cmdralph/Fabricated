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
 * read as ServiceLoader reads it on Fabric's class path, in the file's order, and leaves out of the module a file whose
 * bytes its reader would refuse; NeoForge on FML 10 and later reads them itself as ServiceLoader does, and they stay as
 * they are where its module takes them.
 */
class PackageGuardPlatformTest {
	private static final String INLINE = "META-INF/services/com.api.Inline";
	private static final String BYTES = "META-INF/services/com.api.Bytes";
	private static final String LINES = "META-INF/services/com.api.Lines";
	private static final String NAMED = "META-INF/services/com.api.Bridge-v2";
	private static final String UNQUALIFIED = "META-INF/services/Bridge";
	private static final String PROPERTIES = "META-INF/services/org.codehaus.groovy.runtime.ExtensionModule";
	private static final String ORDER = "META-INF/services/com.api.Order";
	private static final String GOOD = "META-INF/services/com.api.Good";

	/** NeoForge 21.1: an own file SecureJarHandler would refuse is left out, so the module is built; a clean one stays. */
	@Test
	void anOwnFileThePlatformReadsAsItIsAndWouldRefuseIsLeftOut(@TempDir Path dir) throws Exception {
		PackageGuard.Decision solo = check(PlatformDescriptor.ServiceFiles.AS_THEY_ARE, solo(dir)).get("solo");
		for (String file : List.of(INLINE, BYTES, LINES, NAMED, UNQUALIFIED)) {
			assertTrue(solo.hides(file), file);
			assertTrue(solo.notes().contains("has the service file " + file + ", which the platform reads as it is and would refuse (a class name with a"
					+ " comment after it, a line or a file name that isn't a class's, or bytes that aren't UTF-8); the service file is left out, so that its"
					+ " module is built, without the service"), solo.notes().toString());
		}
		assertTrue(solo.hides(PROPERTIES));
		assertTrue(solo.notes().contains("declares the service org.codehaus.groovy.runtime.ExtensionModule with the line moduleName=fabricated-test, which"
				+ " isn't a class name; the service file is left out, as a module can only declare its own providers"), solo.notes().toString());
		assertFalse(solo.hides(GOOD));
		assertFalse(solo.hides(ORDER));
	}

	/**
	 * Forge: the module declares what ServiceLoader reads of each own file, in its order: a comment cut, a line naming no
	 * class left out, a file not named as a class in a package not declared, each with a note. A file whose bytes aren't
	 * all UTF-8, which securemodules reads strictly as it opens the jar, is left out of the module and declared all the
	 * same; one none of whose lines names a class (Groovy's extension module properties) stays in the jar, declaring nothing.
	 */
	@Test
	void whereFabricatedDeclaresTheServicesTheyAreReadAsServiceLoaderReadsThem(@TempDir Path dir) throws Exception {
		PackageGuard.Decision solo = check(PlatformDescriptor.ServiceFiles.FROM_THE_GUARD, solo(dir)).get("solo");
		for (String file : List.of(INLINE, LINES, NAMED, UNQUALIFIED, PROPERTIES, ORDER, GOOD)) assertFalse(solo.hides(file), file);
		assertTrue(solo.hides(BYTES), "securemodules would refuse the jar");
		assertEquals(Map.of("com.api.Inline", List.of("com.s.Impl"), "com.api.Bytes", List.of("com.s.Impl"), "com.api.Lines", List.of("com.s.Impl", "com.s.Other"),
				"com.api.Order", List.of("com.s.Other", "com.s.Impl"), "com.api.Good", List.of("com.s.Impl")), solo.provides());
		assertEquals(List.of("com.s.Other", "com.s.Impl"), solo.provides().get("com.api.Order"), "the file's order, which ServiceLoader keeps");
		for (String file : List.of(NAMED, UNQUALIFIED)) {
			assertTrue(solo.notes().contains("has the service file " + file + ", whose name isn't a class's in a package, directly in META-INF/services/, which"
					+ " is what Java's modules take for a service; its module doesn't declare it"), solo.notes().toString());
		}
		assertTrue(solo.notes().contains("declares the service com.api.Lines with the line not a class, which isn't a class name; its module declares the"
				+ " service without it, as Java's modules take only class names"), solo.notes().toString());
		assertTrue(solo.notes().contains("has the service file " + PROPERTIES + ", none of whose lines names a class (moduleName=fabricated-test and 2 more);"
				+ " its module declares no provider of the service, as Java's modules take only class names"), solo.notes().toString());
		assertTrue(solo.notes().contains("has the service file " + BYTES + ", whose bytes aren't all UTF-8, which Forge's module reader takes strictly where"
				+ " ServiceLoader on Fabric's class path reads them as UTF-8 with replacement: the service file is left out of its module, which declares the"
				+ " service as ServiceLoader reads the file"), solo.notes().toString());
	}

	/**
	 * NeoForge on FML 10 and later: own files stay as they are where the platform takes them, as it cuts comments and
	 * reads the bytes as ServiceLoader does; one with a line naming no class is left out, as it names no provider the
	 * module has, and one not named as a class in a package, which the module would refuse.
	 */
	@Test
	void whereThePlatformReadsThemAsServiceLoaderDoesTheyStay(@TempDir Path dir) throws Exception {
		PackageGuard.Decision solo = check(PlatformDescriptor.ServiceFiles.AS_SERVICE_LOADER_DOES, solo(dir)).get("solo");
		for (String file : List.of(INLINE, BYTES, ORDER, GOOD)) assertFalse(solo.hides(file), file);
		for (String file : List.of(LINES, NAMED, UNQUALIFIED, PROPERTIES)) assertTrue(solo.hides(file), file);
		assertEquals(List.of("declares the service com.api.Lines with the line not a class, which isn't a class name; the service file is left out, as a"
				+ " module can only declare its own providers"), solo.notes().stream().filter(n -> n.contains("com.api.Lines")).toList());
		assertTrue(solo.notes().contains("has the service file " + UNQUALIFIED + ", whose name isn't a class's in a package, directly in META-INF/services/,"
				+ " which is what Java's modules take for a service; the service file is left out, so that its module is built, without the service"),
				solo.notes().toString());
	}

	/**
	 * What a module declares includes the files it serves from other jars, in their order: Forge declares a moved file in
	 * the module that has its classes, and one whose bytes its reader would refuse there without the file.
	 */
	@Test
	void aMovedFileIsWhatItsNewModuleProvides(@TempDir Path dir) throws Exception {
		TranslatedMod outer = mod(dir, "outer", null, TestJars.create()
				.text("META-INF/services/com.api.Bridge", "com.inner.Second\ncom.inner.BridgeImpl\n")
				.bytes("META-INF/services/com.api.Latin", "# café\ncom.inner.BridgeImpl\n".getBytes(StandardCharsets.ISO_8859_1)));
		TranslatedMod inner = mod(dir, "inner", "outer", TestJars.create()
				.bytes("com/inner/BridgeImpl.class", cls("com/inner/BridgeImpl"))
				.bytes("com/inner/Second.class", cls("com/inner/Second")));
		Map<String, PackageGuard.Decision> decisions = check(PlatformDescriptor.ServiceFiles.FROM_THE_GUARD, List.of(outer, inner));
		assertEquals(Map.of("com.api.Bridge", List.of("com.inner.Second", "com.inner.BridgeImpl"), "com.api.Latin", List.of("com.inner.BridgeImpl")),
				decisions.get("inner").provides());
		assertEquals(Set.of("META-INF/services/com.api.Bridge"), decisions.get("inner").borrowed().get(outer.jar()), "the Latin-1 file isn't in the module");
		assertEquals(Map.of(), decisions.get("outer").provides());
		assertTrue(decisions.get("outer").hides("META-INF/services/com.api.Latin"));
	}

	/**
	 * One mod with service files of its own providers: an inline comment, Latin-1 bytes in a comment, a line naming no
	 * class, a name not a class's, a name without a package, Groovy's extension module properties, two providers out of
	 * alphabetical order, a clean one.
	 */
	private static List<TranslatedMod> solo(Path dir) throws Exception {
		return List.of(mod(dir, "solo", null, TestJars.create()
				.bytes("com/s/Impl.class", cls("com/s/Impl"))
				.bytes("com/s/Other.class", cls("com/s/Other"))
				.text(INLINE, "com.s.Impl # the impl\n")
				.bytes(BYTES, "# café\ncom.s.Impl\n".getBytes(StandardCharsets.ISO_8859_1))
				.text(LINES, "com.s.Impl\nnot a class\ncom.s.Other\n")
				.text(NAMED, "com.s.Impl\n")
				.text(UNQUALIFIED, "com.s.Impl\n")
				.text(PROPERTIES, "moduleName=fabricated-test\nmoduleVersion=1.0\nextensionClasses=com.s.Impl\n")
				.text(ORDER, "com.s.Other\ncom.s.Impl\n")
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
