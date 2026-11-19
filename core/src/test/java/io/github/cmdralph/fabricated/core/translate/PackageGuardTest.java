package io.github.cmdralph.fabricated.core.translate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.cmdralph.fabricated.core.archive.ArchiveLimits;
import io.github.cmdralph.fabricated.core.bytecode.ClassBytesSource;
import io.github.cmdralph.fabricated.core.metadata.FabricModJsonParser;
import io.github.cmdralph.fabricated.core.resolve.Candidate;
import io.github.cmdralph.fabricated.core.testutil.TestJars;

/**
 * The package guard's service files (PKG-6), each case in every load order: a nested jar may come before or after its
 * parent, and the jar first in a package split between jars serves it. In every order no mod is disabled and every
 * module is one the platforms build ({@link #assertModulesValid}).
 */
class PackageGuardTest {
	private static final String SERVICE = "META-INF/services/com.api.Bridge";

	/** As NeoForge reads a service file: a provider per line, comments and blank lines ignored. */
	@Test
	void readsTheProvidersOfAServiceFile() {
		assertEquals(Set.of("com.example.impl.First", "org.other.Second", "com.example.impl.Third"),
				PackageGuard.providers("# providers\ncom.example.impl.First\r\n\n  org.other.Second # inline\ncom.example.impl.Third"));
		assertEquals(Set.of(), PackageGuard.providers("# none\n\n"));
	}

	/**
	 * OneConfig 1.8.0's shape: its jar declares a service whose provider its nested jar holds, the service's interface in
	 * another nested jar. The nested module with the class declares it, whichever comes first; the outer jar's file is
	 * left out of its own module. The jars split another package, so each order serves it from another module.
	 */
	@Test
	void anOuterJarsServiceIsDeclaredByTheNestedModuleWithItsProvider(@TempDir Path dir) throws Exception {
		TranslatedMod outer = mod(dir, "outer", null, TestJars.create()
				.bytes("com/outer/Main.class", cls("com/outer/Main"))
				.bytes("com/shared/OuterPart.class", cls("com/shared/OuterPart"))
				.text(SERVICE, "com.inner.BridgeImpl\n"));
		TranslatedMod inner = mod(dir, "inner", "outer", TestJars.create()
				.bytes("com/inner/BridgeImpl.class", TestJars.emptyClass("com/inner/BridgeImpl", "java/lang/Object", "com/api/Bridge"))
				.bytes("com/shared/InnerPart.class", cls("com/shared/InnerPart")));
		TranslatedMod api = mod(dir, "api", "outer", TestJars.create()
				.bytes("com/api/Bridge.class", cls("com/api/Bridge"))
				.bytes("com/shared/ApiPart.class", cls("com/shared/ApiPart")));
		inEveryOrder(List.of(outer, inner, api), Set.of(), decisions -> {
			assertTrue(decisions.get("outer").hides(SERVICE), "a module can only declare its own providers");
			assertTrue(decisions.get("inner").borrowed().getOrDefault(outer.jar(), Set.of()).contains(SERVICE), "the module with the provider declares it");
			assertFalse(declaresElsewhere(decisions, "inner"));
			assertNote(decisions, "outer", "declares the service com.api.Bridge with com.inner.BridgeImpl, which inner's module has: the service file goes there,"
					+ " as a module can only declare its own providers");
			assertNote(decisions, "inner", "also declares outer's service com.api.Bridge, whose providers it has");
		});
	}

	/** Where the nested module declares the same service with the same providers, it is served once. */
	@Test
	void aHolderDeclaringTheSameServiceServesItOnce(@TempDir Path dir) throws Exception {
		TranslatedMod outer = mod(dir, "outer", null, TestJars.create()
				.bytes("com/shared/OuterPart.class", cls("com/shared/OuterPart"))
				.text(SERVICE, "com.inner.BridgeImpl\n"));
		TranslatedMod inner = mod(dir, "inner", "outer", TestJars.create()
				.bytes("com/inner/BridgeImpl.class", cls("com/inner/BridgeImpl"))
				.bytes("com/shared/InnerPart.class", cls("com/shared/InnerPart"))
				.text(SERVICE, "# the same\ncom.inner.BridgeImpl\n"));
		inEveryOrder(List.of(outer, inner), Set.of(), decisions -> {
			assertTrue(decisions.get("outer").hides(SERVICE));
			assertFalse(decisions.get("inner").hides(SERVICE), "its own file declares it");
			assertFalse(declaresElsewhere(decisions, null), "no second copy");
			assertNote(decisions, "outer", "declares the service com.api.Bridge with com.inner.BridgeImpl, which inner's module has and declares already;"
					+ " the service file is left out");
		});
	}

	/** Where it declares the service with other providers, it keeps its own file, and the outer one stays out. */
	@Test
	void aHolderDeclaringOtherProvidersKeepsItsOwn(@TempDir Path dir) throws Exception {
		TranslatedMod outer = mod(dir, "outer", null, TestJars.create()
				.bytes("com/shared/OuterPart.class", cls("com/shared/OuterPart"))
				.text(SERVICE, "com.inner.BridgeImpl\n"));
		TranslatedMod inner = mod(dir, "inner", "outer", TestJars.create()
				.bytes("com/inner/BridgeImpl.class", cls("com/inner/BridgeImpl"))
				.bytes("com/inner/OtherImpl.class", cls("com/inner/OtherImpl"))
				.bytes("com/shared/InnerPart.class", cls("com/shared/InnerPart"))
				.text(SERVICE, "com.inner.OtherImpl\n"));
		inEveryOrder(List.of(outer, inner), Set.of(), decisions -> {
			assertTrue(decisions.get("outer").hides(SERVICE));
			assertFalse(decisions.get("inner").hides(SERVICE));
			assertFalse(declaresElsewhere(decisions, null));
			assertNote(decisions, "outer", "declares the service com.api.Bridge with com.inner.BridgeImpl, which inner's module has but declares with other"
					+ " providers in its own file; the service file is left out, as a module can only declare its own providers");
		});
	}

	/** Providers in two modules: neither can declare the other's, so the file stays out. */
	@Test
	void providersInTwoModulesStayOut(@TempDir Path dir) throws Exception {
		TranslatedMod outer = mod(dir, "outer", null, TestJars.create()
				.bytes("com/shared/OuterPart.class", cls("com/shared/OuterPart"))
				.text(SERVICE, "com.inner.BridgeImpl\ncom.second.SecondImpl\n"));
		TranslatedMod inner = mod(dir, "inner", "outer", TestJars.create()
				.bytes("com/inner/BridgeImpl.class", cls("com/inner/BridgeImpl"))
				.bytes("com/shared/InnerPart.class", cls("com/shared/InnerPart")));
		TranslatedMod second = mod(dir, "second", "outer", TestJars.create()
				.bytes("com/second/SecondImpl.class", cls("com/second/SecondImpl")));
		inEveryOrder(List.of(outer, inner, second), Set.of(), decisions -> {
			assertTrue(decisions.get("outer").hides(SERVICE));
			assertFalse(declaresElsewhere(decisions, null));
			assertNote(decisions, "outer", "declares the service com.api.Bridge with providers in the modules of inner and second; the service file is left"
					+ " out, as a module can only declare its own providers");
		});
	}

	/**
	 * A provider no Fabric mod's module has (none at all, or the platform's: its package left out of the jar holding it)
	 * keeps the file out, even where a nested module has the others.
	 */
	@Test
	void aProviderNoFabricModsModuleHasKeepsTheFileOut(@TempDir Path dir) throws Exception {
		String platformService = "META-INF/services/com.api.PlatformBridge";
		TranslatedMod outer = mod(dir, "outer", null, TestJars.create()
				.bytes("com/shared/OuterPart.class", cls("com/shared/OuterPart"))
				.text(SERVICE, "com.inner.BridgeImpl\ncom.gone.Missing\n")
				.text(platformService, "com.platform.PlatformImpl\n"));
		TranslatedMod inner = mod(dir, "inner", "outer", TestJars.create()
				.bytes("com/inner/BridgeImpl.class", cls("com/inner/BridgeImpl"))
				.bytes("com/shared/InnerPart.class", cls("com/shared/InnerPart"))
				.bytes("com/platform/PlatformImpl.class", cls("com/platform/PlatformImpl")));
		inEveryOrder(List.of(outer, inner), Set.of("com/platform"), decisions -> {
			assertTrue(decisions.get("outer").hides(SERVICE) && decisions.get("outer").hides(platformService));
			assertTrue(decisions.get("inner").excludedPackages().contains("com/platform"));
			assertFalse(declaresElsewhere(decisions, null), "nothing is written into a module Fabricated doesn't build");
			assertNote(decisions, "outer", "declares the service com.api.Bridge with com.gone.Missing, which no Fabric mod's module has; the service file is"
					+ " left out, as a module can only declare its own providers");
			assertNote(decisions, "outer", "declares the service com.api.PlatformBridge with com.platform.PlatformImpl, which no Fabric mod's module has");
		});
	}

	/**
	 * A file listing a provider its own module has and one a nested module has: each module could declare only its own,
	 * which needs a file neither jar has, so it stays out, as one naming a provider nowhere does.
	 */
	@Test
	void aServiceFileWithProvidersOfItsOwnAndAnothersStaysOut(@TempDir Path dir) throws Exception {
		TranslatedMod outer = mod(dir, "outer", null, TestJars.create()
				.bytes("com/outer/OwnImpl.class", cls("com/outer/OwnImpl"))
				.bytes("com/shared/OuterPart.class", cls("com/shared/OuterPart"))
				.text(SERVICE, "com.outer.OwnImpl\ncom.inner.BridgeImpl\n"));
		TranslatedMod inner = mod(dir, "inner", "outer", TestJars.create()
				.bytes("com/inner/BridgeImpl.class", cls("com/inner/BridgeImpl"))
				.bytes("com/shared/InnerPart.class", cls("com/shared/InnerPart")));
		inEveryOrder(List.of(outer, inner), Set.of(), decisions -> {
			assertTrue(decisions.get("outer").hides(SERVICE));
			assertFalse(declaresElsewhere(decisions, null));
			assertNote(decisions, "outer", "declares the service com.api.Bridge with providers in the modules of inner and outer; the service file is left"
					+ " out, as a module can only declare its own providers");
		});
	}

	/**
	 * OneConfig 1.8.0's package: the provider's package is split between the jar declaring the service, the nested jar
	 * with the provider and the one with the interface. Whichever comes first serves the package, the provider with it,
	 * and declares the service: exactly one module does, in every order.
	 */
	@Test
	void aProviderInAPackageSplitWithTheDeclaringJarIsDeclaredOnceInEveryOrder(@TempDir Path dir) throws Exception {
		String provider = "com/split/BridgeImpl.class";
		TranslatedMod outer = mod(dir, "outer", null, TestJars.create()
				.bytes("com/split/OuterPart.class", cls("com/split/OuterPart"))
				.text(SERVICE, "com.split.BridgeImpl\n"));
		TranslatedMod inner = mod(dir, "inner", "outer", TestJars.create().bytes(provider, cls("com/split/BridgeImpl")));
		TranslatedMod api = mod(dir, "api", "outer", TestJars.create().bytes("com/split/Bridge.class", cls("com/split/Bridge")));
		inEveryOrder(List.of(outer, inner, api), Set.of(), decisions -> assertEquals(1, declaring(decisions, outer, SERVICE).size(),
				"declared by " + declaring(decisions, outer, SERVICE) + ": " + decisions));
	}

	/**
	 * M1: a file placed by another jar's classes must read as the platforms read it (NeoForge 21.1's and Forge's module
	 * readers: strict UTF-8, each line trimmed a class name unless blank or a whole-line comment). A class name with a
	 * comment after it, bytes that aren't UTF-8 or a keyword in a name would stop the module being built, so such a file
	 * stays out; one of comments, blank lines, CRLF ends and nested classes' names goes.
	 */
	@Test
	void aFileThePlatformsReadOtherwiseIsNotMoved(@TempDir Path dir) throws Exception {
		byte[] latin1 = "# café, in Latin-1\ncom.inner.BridgeImpl\n".getBytes(StandardCharsets.ISO_8859_1);
		TranslatedMod outer = mod(dir, "outer", null, TestJars.create()
				.text("META-INF/services/com.api.Inline", "com.inner.BridgeImpl # the bridge\n")
				.bytes("META-INF/services/com.api.Bytes", latin1)
				.text("META-INF/services/com.api.Keyword", "com.inner.int\n")
				.text("META-INF/services/com.api.Good", "# the bridges\r\n\r\n  com.inner.BridgeImpl  \r\ncom.inner.Bridge$Impl\n"));
		TranslatedMod inner = mod(dir, "inner", "outer", TestJars.create()
				.bytes("com/inner/BridgeImpl.class", cls("com/inner/BridgeImpl"))
				.bytes("com/inner/Bridge$Impl.class", cls("com/inner/Bridge$Impl"))
				.bytes("com/inner/int.class", cls("com/inner/int")));
		inEveryOrder(List.of(outer, inner), Set.of(), decisions -> {
			for (String bad : List.of("Inline", "Bytes", "Keyword")) {
				assertEquals(List.of(), declaring(decisions, outer, "META-INF/services/com.api." + bad), bad);
				assertNote(decisions, "outer", "declares the service com.api." + bad + " with ");
			}
			assertTrue(decisions.get("outer").notes().stream().filter(n -> n.contains("the platforms read a service file more strictly")).count() == 3,
					decisions.get("outer").notes().toString());
			assertEquals(List.of("inner"), declaring(decisions, outer, "META-INF/services/com.api.Good"));
		});
	}

	/** M1, kept: a file whose providers its module serves from a jar it shares a package with reads as the platforms do too. */
	@Test
	void aFileItsModuleServesFromASplitIsKeptOnlyWhereThePlatformsReadIt(@TempDir Path dir) throws Exception {
		TranslatedMod outer = mod(dir, "outer", null, TestJars.create()
				.bytes("com/split/OuterPart.class", cls("com/split/OuterPart"))
				.text(SERVICE, "com.split.BridgeImpl # the bridge\n")
				.text("META-INF/services/com.api.Good", "com.split.BridgeImpl\n"));
		TranslatedMod inner = mod(dir, "inner", "outer", TestJars.create().bytes("com/split/BridgeImpl.class", cls("com/split/BridgeImpl")));
		inEveryOrder(List.of(outer, inner), Set.of(), decisions -> {
			assertEquals(List.of(), declaring(decisions, outer, SERVICE));
			assertEquals(1, declaring(decisions, outer, "META-INF/services/com.api.Good").size());
		});
		Map<String, PackageGuard.Decision> outerFirst = check(List.of(outer, inner), Set.of()).decisions();
		assertFalse(outerFirst.get("outer").hides("META-INF/services/com.api.Good"), "its module serves the class");
		assertNote(outerFirst, "outer", "declares the service com.api.Bridge with com.split.BridgeImpl, which its module serves from another jar's part of a"
				+ " package split with it, but the platforms read a service file more strictly");
	}

	/**
	 * M2: two other jars' files of one service whose providers one module has, naming other providers: which one Fabric's
	 * class path finds first is a matter of order, so neither goes there, in any order.
	 */
	@Test
	void twoJarsFilesNamingOtherProvidersForOneModuleBothStayOut(@TempDir Path dir) throws Exception {
		List<TranslatedMod> mods = family(dir, "com.h.A1", "com.h.A2");
		inEveryOrder(mods, Set.of(), decisions -> {
			assertEquals(List.of(), declaring(decisions, mods.get(2), SERVICE));
			assertEquals(List.of(), declaring(decisions, mods.get(3), SERVICE));
			assertNote(decisions, "j1", "declares the service com.api.Bridge with com.h.A1, which holder's module has, as the files of that service of j2 would go there"
					+ " with other providers; each is left out");
			assertNote(decisions, "j2", "declares the service com.api.Bridge with com.h.A2, which holder's module has, as the files of that service of j1 would go");
		});
	}

	/** M2: two other jars' files naming the same providers serve the service once, from the least mod id's jar, in every order. */
	@Test
	void twoJarsFilesNamingTheSameProvidersServeItOnce(@TempDir Path dir) throws Exception {
		List<TranslatedMod> mods = family(dir, "com.h.A1", "com.h.A1");
		inEveryOrder(mods, Set.of(), decisions -> {
			assertEquals(List.of("holder"), declaring(decisions, mods.get(2), SERVICE), "j1's file");
			assertEquals(List.of(), declaring(decisions, mods.get(3), SERVICE));
			assertNote(decisions, "j1", "declares the service com.api.Bridge with com.h.A1, which holder's module has: the service file goes there");
			assertNote(decisions, "j2", "declares the service com.api.Bridge with com.h.A1, which holder's module has and declares already, from j1's file of it");
		});
	}

	/**
	 * L3: a file with a provider of its own and one of a package it shares with its nested jar. Where its jar serves that
	 * package the file stays; where the nested jar does, the providers are in two modules and the file stays out. Either
	 * way the mod loads, where a package it gave up used to disable it.
	 */
	@Test
	void ownAndSplitProvidersNeverDisableTheMod(@TempDir Path dir) throws Exception {
		TranslatedMod a = mod(dir, "alpha", null, TestJars.create()
				.bytes("com/a/Own.class", cls("com/a/Own"))
				.bytes("com/p/APart.class", cls("com/p/APart"))
				.text(SERVICE, "com.a.Own\ncom.p.X\n"));
		TranslatedMod n = mod(dir, "nested", "alpha", TestJars.create().bytes("com/p/X.class", cls("com/p/X")));
		inEveryOrder(List.of(a, n), Set.of(), decisions -> { });
		assertFalse(check(List.of(a, n), Set.of()).decisions().get("alpha").hides(SERVICE), "a's module serves both");
		Map<String, PackageGuard.Decision> nestedFirst = check(List.of(n, a), Set.of()).decisions();
		assertTrue(nestedFirst.get("alpha").hides(SERVICE));
		assertNote(nestedFirst, "alpha", "declares the service com.api.Bridge with providers in the modules of alpha and nested");
	}

	/** L3: a file of a package given up naming a class no jar has goes nowhere, where it went with the package. */
	@Test
	void aFileOfAPackageGivenUpNamingAClassNoJarHasGoesNowhere(@TempDir Path dir) throws Exception {
		TranslatedMod a = mod(dir, "alpha", null, TestJars.create().bytes("com/p/AThing.class", cls("com/p/AThing")));
		TranslatedMod b = mod(dir, "beta", null, TestJars.create()
				.bytes("com/p/BThing.class", cls("com/p/BThing"))
				.text(SERVICE, "com.p.Missing\n"));
		inEveryOrder(List.of(a, b), Set.of(), decisions -> {
			assertTrue(decisions.get("beta").hides(SERVICE));
			assertFalse(declaresElsewhere(decisions, null));
			assertNote(decisions, "beta", "declares the service com.api.Bridge with com.p.Missing, which no Fabric mod's module has");
		});
	}

	/**
	 * L3: a file going with a package given up, where the module serving that package keeps its own file of the service,
	 * whose provider it serves from its nested jar: its own stays, the other goes nowhere, and no mod is disabled.
	 */
	@Test
	void aFileOfAPackageGivenUpYieldsToTheModulesOwn(@TempDir Path dir) throws Exception {
		TranslatedMod a = mod(dir, "alpha", null, TestJars.create()
				.bytes("com/p/APart.class", cls("com/p/APart"))
				.text(SERVICE, "com.p.X\n"));
		TranslatedMod n = mod(dir, "nested", "alpha", TestJars.create().bytes("com/p/X.class", cls("com/p/X")));
		TranslatedMod b = mod(dir, "beta", null, TestJars.create()
				.bytes("com/p/Y.class", cls("com/p/Y"))
				.text(SERVICE, "com.p.Y\n"));
		inEveryOrder(List.of(a, n, b), Set.of(), decisions -> { });
		Map<String, PackageGuard.Decision> decisions = check(List.of(a, n, b), Set.of()).decisions();
		assertFalse(decisions.get("alpha").hides(SERVICE), "a's own file, which its module serves");
		assertTrue(decisions.get("beta").hides(SERVICE));
		assertEquals(Map.of(n.jar(), Set.of("com/p/X.class"), b.jar(), Set.of("com/p/Y.class")), decisions.get("alpha").borrowed());
		assertNote(decisions, "beta", "declares the service com.api.Bridge with com.p.Y, which alpha's module has but declares with other providers in its own file");
	}

	/** L3: a file going with a package given up to a module declaring other providers leaves the mod loading. */
	@Test
	void aFileOfAPackageGivenUpToAModuleWithOtherProvidersLeavesTheModLoading(@TempDir Path dir) throws Exception {
		TranslatedMod a = mod(dir, "alpha", null, TestJars.create()
				.bytes("com/p/X.class", cls("com/p/X"))
				.text(SERVICE, "com.p.X\n"));
		TranslatedMod b = mod(dir, "beta", null, TestJars.create()
				.bytes("com/p/Y.class", cls("com/p/Y"))
				.text(SERVICE, "com.p.Y\n"));
		inEveryOrder(List.of(a, b), Set.of(), decisions -> {
			String first = decisions.get("alpha").borrowed().isEmpty() ? "beta" : "alpha";
			String later = first.equals("alpha") ? "beta" : "alpha";
			assertFalse(decisions.get(first).hides(SERVICE));
			assertTrue(decisions.get(later).hides(SERVICE));
			assertNote(decisions, later, "declares the service com.api.Bridge with com.p." + (later.equals("alpha") ? "X" : "Y") + ", which " + first
					+ "'s module has but declares with other providers in its own file");
		});
	}

	/** L4: a mod's file naming another, unrelated mod's classes isn't declared by that mod's module. */
	@Test
	void anUnrelatedModsFileIsNotDeclaredForAnotherModsClasses(@TempDir Path dir) throws Exception {
		TranslatedMod outer = mod(dir, "outer", null, TestJars.create().bytes("com/shared/OuterPart.class", cls("com/shared/OuterPart")));
		TranslatedMod inner = mod(dir, "inner", "outer", TestJars.create()
				.bytes("com/inner/BridgeImpl.class", cls("com/inner/BridgeImpl"))
				.bytes("com/shared/InnerPart.class", cls("com/shared/InnerPart")));
		TranslatedMod unrelated = mod(dir, "unrelated", null, TestJars.create()
				.bytes("com/unrelated/Main.class", cls("com/unrelated/Main"))
				.text(SERVICE, "com.inner.BridgeImpl\n"));
		inEveryOrder(List.of(outer, inner, unrelated), Set.of(), decisions -> {
			assertEquals(List.of(), declaring(decisions, unrelated, SERVICE));
			assertNote(decisions, "unrelated", "declares the service com.api.Bridge with com.inner.BridgeImpl, which inner's module has from another mod's jar,"
					+ " none of this mod's own (it, its parent and the jars nested in them) having the class");
		});
	}

	/** A parent, its nested holder of com.h.A1 and com.h.A2, and two nested jars declaring the service with these providers. */
	private static List<TranslatedMod> family(Path dir, String first, String second) throws Exception {
		TranslatedMod outer = mod(dir, "outer", null, TestJars.create().bytes("com/shared/OuterPart.class", cls("com/shared/OuterPart")));
		TranslatedMod h = mod(dir, "holder", "outer", TestJars.create()
				.bytes("com/h/A1.class", cls("com/h/A1"))
				.bytes("com/h/A2.class", cls("com/h/A2"))
				.bytes("com/shared/HPart.class", cls("com/shared/HPart")));
		TranslatedMod j1 = mod(dir, "j1", "outer", TestJars.create()
				.bytes("com/shared/J1Part.class", cls("com/shared/J1Part"))
				.text(SERVICE, first + "\n"));
		TranslatedMod j2 = mod(dir, "j2", "outer", TestJars.create()
				.bytes("com/shared/J2Part.class", cls("com/shared/J2Part"))
				.text(SERVICE, "# the second\n" + second + "\n"));
		return List.of(outer, h, j1, j2);
	}

	/** Runs the guard over {@code mods} in each of their load orders: no mod is disabled, every module is valid, and {@code check} holds. */
	private static void inEveryOrder(List<TranslatedMod> mods, Set<String> platformPackages, Consumer<Map<String, PackageGuard.Decision>> check) {
		for (List<TranslatedMod> order : permutations(mods)) {
			PackageGuard.Result result = check(order, platformPackages);
			List<String> ids = order.stream().map(TranslatedMod::neoForgeId).toList();
			try {
				assertEquals(List.of(), result.conflicts().stream().map(PackageGuard.Conflict::reason).toList(), "no mod disabled");
				assertModulesValid(order, result.decisions());
				check.accept(result.decisions());
			} catch (AssertionError e) {
				throw new AssertionError("in the order " + ids + ": " + e.getMessage(), e);
			}
		}
	}

	private static PackageGuard.Result check(List<TranslatedMod> mods, Set<String> platformPackages) {
		return new PackageGuard(ArchiveLimits.DEFAULTS, List.of()).check(mods, platformPackages, ClassBytesSource.EMPTY);
	}

	/**
	 * Each module as the platforms build it: the files of its jar it keeps and those it serves from other jars. A module
	 * has one file of a service's path, and a service file declares only classes the module has, read as the platforms
	 * read it (strict UTF-8, each line trimmed a class name unless blank or a whole-line comment).
	 */
	private static void assertModulesValid(List<TranslatedMod> mods, Map<String, PackageGuard.Decision> decisions) {
		for (TranslatedMod mod : mods) {
			PackageGuard.Decision decision = decisions.get(mod.neoForgeId());
			Map<String, byte[]> files = new HashMap<>();
			entries(mod.jar()).forEach((path, data) -> {
				if (!decision.hides(path)) files.put(path, data);
			});
			decision.borrowed().forEach((jar, paths) -> paths.forEach(path -> {
				byte[] previous = files.put(path, entries(jar).get(path));
				assertFalse(previous != null && path.startsWith("META-INF/services/"), mod.neoForgeId() + "'s module has two files " + path);
			}));
			files.forEach((path, data) -> {
				if (!path.startsWith("META-INF/services/")) return;
				String text;
				try {
					text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data)).toString();
				} catch (CharacterCodingException e) {
					throw new AssertionError(mod.neoForgeId() + "'s module declares " + path + ", which isn't UTF-8");
				}
				for (String line : text.split("\r\n|\r|\n")) {
					String name = line.trim();
					if (name.isEmpty() || name.startsWith("#")) continue;
					assertTrue(files.containsKey(name.replace('.', '/') + ".class") && !List.of(name.split("\\.")).contains("int"),
							mod.neoForgeId() + "'s module declares " + path + " with " + name + ", which it hasn't or the platforms can't name");
				}
			});
		}
	}

	/** The modules that declare {@code mod}'s file {@code path}: its own, or those serving it from its jar. */
	private static List<String> declaring(Map<String, PackageGuard.Decision> decisions, TranslatedMod mod, String path) {
		List<String> modules = new ArrayList<>();
		decisions.forEach((id, decision) -> {
			if (id.equals(mod.neoForgeId()) ? !decision.hides(path) : decision.borrowed().getOrDefault(mod.jar(), Set.of()).contains(path)) modules.add(id);
		});
		Collections.sort(modules);
		return modules;
	}

	/** Whether a module (other than {@code except}) serves another mod's service file. */
	private static boolean declaresElsewhere(Map<String, PackageGuard.Decision> decisions, String except) {
		return decisions.entrySet().stream().filter(e -> !e.getKey().equals(except))
				.anyMatch(e -> e.getValue().borrowed().values().stream().anyMatch(files -> files.stream().anyMatch(f -> f.startsWith("META-INF/services/"))));
	}

	private static Map<String, byte[]> entries(Path jar) {
		Map<String, byte[]> entries = new HashMap<>();
		try (ZipFile zip = new ZipFile(jar.toFile())) {
			for (ZipEntry entry : Collections.list(zip.entries())) {
				if (!entry.isDirectory()) entries.put(entry.getName(), zip.getInputStream(entry).readAllBytes());
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return entries;
	}

	private static List<List<TranslatedMod>> permutations(List<TranslatedMod> mods) {
		if (mods.size() <= 1) return List.of(mods);
		List<List<TranslatedMod>> all = new ArrayList<>();
		for (TranslatedMod head : mods) {
			List<TranslatedMod> rest = new ArrayList<>(mods);
			rest.remove(head);
			for (List<TranslatedMod> tail : permutations(rest)) {
				List<TranslatedMod> order = new ArrayList<>(List.of(head));
				order.addAll(tail);
				all.add(order);
			}
		}
		return all;
	}

	private static void assertNote(Map<String, PackageGuard.Decision> decisions, String id, String note) {
		List<String> notes = decisions.get(id).notes();
		assertTrue(notes.stream().anyMatch(n -> n.startsWith(note)), id + "'s notes " + notes + " lack: " + note);
	}

	private static byte[] cls(String internalName) {
		return TestJars.emptyClass(internalName, "java/lang/Object");
	}

	/** A translated mod of {@code jar}'s files, nested in {@code parent} (null for a jar of the mods folder). */
	private static TranslatedMod mod(Path dir, String id, String parent, TestJars jar) throws Exception {
		String json = TestJars.modJson(id, "1.0", "");
		Path file = jar.fabricModJson(json).write(dir.resolve(id + ".jar"));
		Candidate c = new Candidate(file, id + "-hash", FabricModJsonParser.parse(json), parent == null ? id + ".jar" : parent + ".jar!/" + id + ".jar", parent,
				parent == null ? 0 : 1, List.of(), new HashSet<>());
		return new TranslatedMod(c, id, file, dir.resolve(id + "-overlay.jar"), List.of(), 0, List.of(), List.of(), List.of(), Set.of(), Set.of(), Set.of(),
				Map.of(), false, 0);
	}
}
