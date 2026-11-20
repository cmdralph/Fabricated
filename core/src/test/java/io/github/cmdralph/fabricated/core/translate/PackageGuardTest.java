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
import java.util.function.BiConsumer;
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
 * The package guard's service files (PKG-6), each case in every load order and on each platform's reading of service
 * files ({@link PlatformDescriptor.ServiceFiles}): a nested jar may come before or after its parent, and the jar first in
 * a package split between jars serves it. In every order no mod is disabled and every module is one the platform builds
 * ({@link #assertModulesValid}).
 */
class PackageGuardTest {
	private static final String SERVICE = "META-INF/services/com.api.Bridge";
	private static final PlatformDescriptor.ServiceFiles AS_THEY_ARE = PlatformDescriptor.ServiceFiles.AS_THEY_ARE;
	private static final PlatformDescriptor.ServiceFiles AS_SERVICE_LOADER_DOES = PlatformDescriptor.ServiceFiles.AS_SERVICE_LOADER_DOES;
	private static final PlatformDescriptor.ServiceFiles FROM_THE_GUARD = PlatformDescriptor.ServiceFiles.FROM_THE_GUARD;

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
	 * A file placed by another jar's classes must read as the platform reads it. NeoForge 21.1 (SecureJarHandler 3) reads
	 * it as strict UTF-8, each line trimmed a class name unless blank or a whole-line comment: a class name with a comment
	 * after it or bytes that aren't UTF-8 would stop the module being built, so such a file stays out there. NeoForge's FML
	 * 10 to 12 and Forge cut the comment and read the bytes as ServiceLoader does, so it goes; Forge's reader takes the
	 * bytes strictly, so its module declares the service without the file. A keyword in a name or two names joined by a
	 * line separator BufferedReader doesn't split at (U+2028) names no class, and the file stays out everywhere; one of
	 * comments, blank lines, CRLF ends and nested classes' names goes everywhere.
	 */
	@Test
	void eachPlatformsReadingDecidesWhetherAFileIsMoved(@TempDir Path dir) throws Exception {
		byte[] latin1 = "# café, in Latin-1\ncom.inner.BridgeImpl\n".getBytes(StandardCharsets.ISO_8859_1);
		TranslatedMod outer = mod(dir, "outer", null, TestJars.create()
				.text("META-INF/services/com.api.Inline", "com.inner.BridgeImpl # the bridge\n")
				.bytes("META-INF/services/com.api.Bytes", latin1)
				.text("META-INF/services/com.api.Keyword", "com.inner.int\n")
				.text("META-INF/services/com.api.Separator", "com.inner.BridgeImpl\ncom.inner.BridgeImpl\u2028com.inner.Bridge$Impl\n")
				.text("META-INF/services/com.api.Good", "# the bridges\r\n\r\n  com.inner.BridgeImpl  \r\ncom.inner.Bridge$Impl\n"));
		TranslatedMod inner = mod(dir, "inner", "outer", TestJars.create()
				.bytes("com/inner/BridgeImpl.class", cls("com/inner/BridgeImpl"))
				.bytes("com/inner/Bridge$Impl.class", cls("com/inner/Bridge$Impl"))
				.bytes("com/inner/int.class", cls("com/inner/int")));
		inEveryOrder(List.of(outer, inner), Set.of(), (platform, decisions) -> {
			for (String bad : List.of("Keyword", "Separator")) {
				if (platform != FROM_THE_GUARD || bad.equals("Separator")) assertEquals(List.of(), declaring(decisions, outer, "META-INF/services/com.api." + bad), bad);
				decisions.values().forEach(decision -> assertFalse(decision.provides().containsKey("com.api." + bad), bad));
			}
			if (platform == FROM_THE_GUARD) {
				assertNote(decisions, "outer", "has the service file META-INF/services/com.api.Keyword, none of whose lines names a class (com.inner.int); its"
						+ " module declares no provider of the service");
			} else {
				assertNote(decisions, "outer", "declares the service com.api.Keyword with the line com.inner.int, which isn't a class name; the service file is left out");
			}
			assertTrue(decisions.get("outer").notes().stream().anyMatch(n -> n.startsWith("declares the service com.api.Separator with ")
					&& n.contains("com.inner.BridgeImpl\u2028com.inner.Bridge$Impl") && n.contains("isn't a class name")), decisions.get("outer").notes().toString());
			assertEquals(List.of("inner"), declaring(decisions, outer, "META-INF/services/com.api.Good"));
			if (platform == AS_THEY_ARE) {
				assertEquals(List.of(), declaring(decisions, outer, "META-INF/services/com.api.Inline"));
				assertEquals(List.of(), declaring(decisions, outer, "META-INF/services/com.api.Bytes"));
				assertNote(decisions, "outer", "declares the service com.api.Inline with com.inner.BridgeImpl, which inner's module has, but the platform reads a"
						+ " service file more strictly");
				assertNote(decisions, "outer", "declares the service com.api.Bytes with com.inner.BridgeImpl, which inner's module has, but the platform reads a"
						+ " service file more strictly");
			} else {
				assertEquals(List.of("inner"), declaring(decisions, outer, "META-INF/services/com.api.Inline"));
				assertEquals(platform == FROM_THE_GUARD ? List.of() : List.of("inner"), declaring(decisions, outer, "META-INF/services/com.api.Bytes"),
						"Forge's module declares it without the file");
			}
			if (platform == FROM_THE_GUARD) {
				assertEquals(List.of("com.inner.BridgeImpl"), decisions.get("inner").provides().get("com.api.Bytes"));
				assertEquals(List.of("com.inner.BridgeImpl"), decisions.get("inner").provides().get("com.api.Inline"));
				assertNote(decisions, "inner", "also declares outer's service com.api.Bytes, whose providers it has, without the file, whose bytes aren't all UTF-8");
			}
		});
	}

	/**
	 * A file going with its package to an unrelated mod's module, with a comment after its provider's name: moved on
	 * main, where NeoForge's FML 10 to 12 and Forge read it as Fabric does; kept where its own module serves the package;
	 * left out on NeoForge 21.1, which reads it as it is.
	 */
	@Test
	void aGivenUpPackagesFileWithACommentGoesWhereThePlatformCutsIt(@TempDir Path dir) throws Exception {
		TranslatedMod a = mod(dir, "alpha", null, TestJars.create().bytes("com/p/AThing.class", cls("com/p/AThing")));
		TranslatedMod b = mod(dir, "beta", null, TestJars.create()
				.bytes("com/p/BImpl.class", cls("com/p/BImpl"))
				.text(SERVICE, "com.p.BImpl # c\n"));
		inEveryOrder(List.of(a, b), Set.of(), (platform, decisions) -> {
			List<String> declaring = declaring(decisions, b, SERVICE);
			if (platform == AS_THEY_ARE) {
				assertEquals(List.of(), declaring);
			} else {
				assertEquals(1, declaring.size(), declaring.toString());
			}
		});
	}

	/**
	 * A file whose providers its module serves from a jar it shares a package with reads as the platform reads it too, to
	 * be kept: a comment after the name is cut by NeoForge's FML 10 to 12 and by Forge's module, not by NeoForge 21.1.
	 */
	@Test
	void aFileItsModuleServesFromASplitIsKeptOnlyWhereThePlatformReadsIt(@TempDir Path dir) throws Exception {
		TranslatedMod outer = mod(dir, "outer", null, TestJars.create()
				.bytes("com/split/OuterPart.class", cls("com/split/OuterPart"))
				.text(SERVICE, "com.split.BridgeImpl # the bridge\n")
				.text("META-INF/services/com.api.Good", "com.split.BridgeImpl\n"));
		TranslatedMod inner = mod(dir, "inner", "outer", TestJars.create().bytes("com/split/BridgeImpl.class", cls("com/split/BridgeImpl")));
		inEveryOrder(List.of(outer, inner), Set.of(), (platform, decisions) -> {
			assertEquals(platform == AS_THEY_ARE ? 0 : 1, declaring(decisions, outer, SERVICE).size());
			assertEquals(1, declaring(decisions, outer, "META-INF/services/com.api.Good").size());
		});
		for (PlatformDescriptor.ServiceFiles platform : PlatformDescriptor.ServiceFiles.values()) {
			Map<String, PackageGuard.Decision> outerFirst = check(platform, List.of(outer, inner), Set.of()).decisions();
			assertFalse(outerFirst.get("outer").hides("META-INF/services/com.api.Good"), "its module serves the class");
			assertEquals(platform == AS_THEY_ARE, outerFirst.get("outer").hides(SERVICE), platform.toString());
		}
		assertNote(check(AS_THEY_ARE, List.of(outer, inner), Set.of()).decisions(), "outer", "declares the service com.api.Bridge with com.split.BridgeImpl,"
				+ " which its module serves from another jar's part of a package split with it, but the platform reads a service file more strictly");
	}

	/**
	 * Two other jars' files of one service whose providers one module has, naming other providers: which one Fabric's
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

	/** Two other jars' files naming the same providers serve the service once, from the least mod id's jar, in every order. */
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
	 * A file with a provider of its own and one of a package it shares with its nested jar. Where its jar serves that
	 * package the file stays; where the nested jar does, the providers are in two modules and the file stays out, the note
	 * saying the package split decided it. Either way the mod loads, where a package it gave up used to disable it.
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
		assertNote(nestedFirst, "alpha", "declares the service com.api.Bridge with providers in the modules of alpha and nested; the service file is left out,"
				+ " as a module can only declare its own providers. The package split decided it: com.p, which this mod's jar has classes in too, is served by"
				+ " the module of the jar that came first");
	}

	/** A file of a package given up naming a class no jar has goes nowhere, where it went with the package. */
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
	 * A file going with a package given up, where the module serving that package keeps its own file of the service,
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

	/** A file going with a package given up to a module declaring other providers leaves the mod loading. */
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

	/** A mod's file naming another, unrelated mod's classes isn't declared by that mod's module. */
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
			assertNote(decisions, "unrelated", "declares the service com.api.Bridge with com.inner.BridgeImpl, which inner's module has from inner's jar, not"
					+ " one of this mod's own (the jars nested with it); the service file is left out, as a mod's file is declared only for its own classes");
		});
	}

	/**
	 * A file whose name isn't a class's in a package directly in META-INF/services (a hyphen, a keyword, a subfolder, no
	 * package): Java's modules take only such a name for a service, and the platforms hand a file's name to the module
	 * (SecureJarHandler on NeoForge 21.1 every file's; FML 10 to 12 each file named as a type, "Bridge" too, which the
	 * module refuses: "not a qualified name of a Java class in a named package"), so moving it would stop the module
	 * being built. It stays out everywhere.
	 */
	@Test
	void aFileNotNamedAsAClassIsNotMoved(@TempDir Path dir) throws Exception {
		List<String> bad = List.of("META-INF/services/com.api.Bridge-v2", "META-INF/services/com.api.int", "META-INF/services/sub/com.api.Bridge",
				"META-INF/services/Bridge");
		TestJars outerJar = TestJars.create().bytes("com/shared/OuterPart.class", cls("com/shared/OuterPart"));
		for (String file : bad) outerJar.text(file, "com.inner.BridgeImpl\n");
		TranslatedMod outer = mod(dir, "outer", null, outerJar);
		TranslatedMod inner = mod(dir, "inner", "outer", TestJars.create()
				.bytes("com/inner/BridgeImpl.class", cls("com/inner/BridgeImpl"))
				.bytes("com/shared/InnerPart.class", cls("com/shared/InnerPart")));
		inEveryOrder(List.of(outer, inner), Set.of(), (platform, decisions) -> {
			for (String file : bad) assertEquals(List.of(), declaring(decisions, outer, file), file);
			String why = platform == AS_THEY_ARE ? "the platform reads a service file more strictly" : "the service file is named otherwise than as a class in a package";
			assertEquals(4, decisions.get("outer").notes().stream().filter(n -> n.contains(why)).count(), decisions.get("outer").notes().toString());
		});
	}

	/**
	 * A mod's own file named without a package ({@code META-INF/services/Bridge}), naming its own class: every platform's
	 * module refuses the name, so NeoForge leaves the file out and Forge's module doesn't declare it.
	 */
	@Test
	void anOwnFileNamedWithoutAPackageIsNotDeclared(@TempDir Path dir) throws Exception {
		String file = "META-INF/services/Bridge";
		TranslatedMod solo = mod(dir, "solo", null, TestJars.create()
				.bytes("com/s/Impl.class", cls("com/s/Impl"))
				.text(file, "com.s.Impl\n"));
		inEveryOrder(List.of(solo), Set.of(), (platform, decisions) -> {
			assertEquals(platform != FROM_THE_GUARD, decisions.get("solo").hides(file));
			assertEquals(Map.of(), decisions.get("solo").provides());
			assertTrue(decisions.get("solo").notes().stream().anyMatch(n -> n.startsWith("has the service file " + file + ", ")), decisions.get("solo").notes().toString());
		});
	}

	/**
	 * A file naming a class its nested jar has, where an unrelated mod's jar has the package too: where that jar comes
	 * first its module serves the package, the class with it, and declares the file too, as the class it names is served
	 * from the mod's own jar; otherwise the nested jar's module does. One module declares it in every order.
	 */
	@Test
	void aFileGoesWithItsClassToTheModuleServingItsPackage(@TempDir Path dir) throws Exception {
		TranslatedMod outer = mod(dir, "outer", null, TestJars.create()
				.bytes("com/outer/Main.class", cls("com/outer/Main"))
				.text(SERVICE, "com.x.Impl\n"));
		TranslatedMod inner = mod(dir, "inner", "outer", TestJars.create().bytes("com/x/Impl.class", cls("com/x/Impl")));
		TranslatedMod unrelated = mod(dir, "aaunrel", null, TestJars.create().bytes("com/x/UPart.class", cls("com/x/UPart")));
		inEveryOrder(List.of(outer, inner, unrelated), Set.of(), decisions -> {
			List<String> declaring = declaring(decisions, outer, SERVICE);
			assertEquals(List.of(decisions.get("aaunrel").borrowed().isEmpty() ? "inner" : "aaunrel"), declaring);
		});
	}

	/**
	 * The file of a package given up to an unrelated mod's module (PKG-2's case): its jar's class is lent to that module,
	 * which declares the file with it, as main did and as Fabric's class path finds it; where its own jar comes first, its
	 * own module keeps it. One module declares it in every order.
	 */
	@Test
	void aFileOfAPackageGivenUpToAnUnrelatedModGoesWithIt(@TempDir Path dir) throws Exception {
		TranslatedMod dupa = mod(dir, "dupa", null, TestJars.create().bytes("com/conflict/Thing.class", cls("com/conflict/Thing")));
		TranslatedMod dupe = mod(dir, "dupe", null, TestJars.create()
				.bytes("com/conflict/Provider.class", cls("com/conflict/Provider"))
				.text(SERVICE, "com.conflict.Provider\n"));
		inEveryOrder(List.of(dupa, dupe), Set.of(), decisions -> {
			String first = decisions.get("dupa").borrowed().isEmpty() ? "dupe" : "dupa";
			assertEquals(List.of(first), declaring(decisions, dupe, SERVICE));
		});
		assertNote(check(List.of(dupa, dupe), Set.of()).decisions(), "dupe", "declares the service com.api.Bridge with com.conflict.Provider, which dupa's"
				+ " module has: the service file goes there");
	}

	/**
	 * A file naming a class its nested jar has, where an unrelated mod's jar has its own copy of that class: where that
	 * jar comes first its copy is served, another mod's class, and the file stays out; otherwise the nested jar's module
	 * declares it. It is never declared for the unrelated jar's copy.
	 */
	@Test
	void aFileIsNotDeclaredForAnUnrelatedJarsCopyOfItsClass(@TempDir Path dir) throws Exception {
		TranslatedMod outer = mod(dir, "outer", null, TestJars.create()
				.bytes("com/outer/Main.class", cls("com/outer/Main"))
				.text(SERVICE, "com.x.Impl\n"));
		TranslatedMod inner = mod(dir, "inner", "outer", TestJars.create().bytes("com/x/Impl.class", cls("com/x/Impl")));
		TranslatedMod unrelated = mod(dir, "aaunrel", null, TestJars.create().bytes("com/x/Impl.class", TestJars.emptyClass("com/x/Impl", "java/lang/Number")));
		inEveryOrder(List.of(outer, inner, unrelated), Set.of(), decisions -> {
			List<String> declaring = declaring(decisions, outer, SERVICE);
			assertFalse(declaring.contains("aaunrel"), declaring.toString());
			if (declaring.isEmpty()) {
				assertNote(decisions, "outer", "declares the service com.api.Bridge with com.x.Impl, which aaunrel's module has from aaunrel's jar, not one of this"
						+ " mod's own");
			} else {
				assertEquals(List.of("inner"), declaring);
			}
		});
	}

	/**
	 * A file naming a class only an unrelated mod has, in a package that mod shares with the file's nested jar: where the
	 * nested jar's module serves the package, the class lent to it, the file still stays out, as the class is served from
	 * the other mod's jar; where the unrelated jar's does, the same. Either way the service has no provider, as the mod's
	 * own jars have none.
	 */
	@Test
	void aFileNamingAClassOnlyAnUnrelatedModHasStaysOut(@TempDir Path dir) throws Exception {
		TranslatedMod outer = mod(dir, "outer", null, TestJars.create()
				.bytes("com/outer/Main.class", cls("com/outer/Main"))
				.text(SERVICE, "com.x.Impl\n"));
		TranslatedMod inner = mod(dir, "inner", "outer", TestJars.create().bytes("com/x/InnerPart.class", cls("com/x/InnerPart")));
		TranslatedMod unrelated = mod(dir, "zzunrel", null, TestJars.create().bytes("com/x/Impl.class", cls("com/x/Impl")));
		inEveryOrder(List.of(outer, inner, unrelated), Set.of(), decisions -> {
			assertEquals(List.of(), declaring(decisions, outer, SERVICE));
			assertTrue(decisions.get("outer").notes().stream().anyMatch(n -> n.startsWith("declares the service com.api.Bridge with com.x.Impl, which ")
					&& n.contains("'s module has from zzunrel's jar, not one of this mod's own")), decisions.get("outer").notes().toString());
		});
	}

	/** The family of a mod's jars is its top-level jar's whole nest tree: a jar nested two deep holds a provider its top's file names. */
	@Test
	void aGrandchildsProviderIsTheFamilys(@TempDir Path dir) throws Exception {
		TranslatedMod outer = mod(dir, "outer", null, List.of("middle"), TestJars.create()
				.bytes("com/outer/Main.class", cls("com/outer/Main"))
				.text(SERVICE, "com.leaf.Impl\n"));
		TranslatedMod middle = mod(dir, "middle", "outer", List.of("leaf"), TestJars.create().bytes("com/middle/Part.class", cls("com/middle/Part")));
		TranslatedMod leaf = mod(dir, "leaf", "middle", List.of(), TestJars.create().bytes("com/leaf/Impl.class", cls("com/leaf/Impl")));
		inEveryOrder(List.of(outer, middle, leaf), Set.of(), decisions -> assertEquals(List.of("leaf"), declaring(decisions, outer, SERVICE)));
	}

	/**
	 * A library two mods nest is kept once, under one of them: it is in each one's family, so the other's file naming its
	 * class goes to its module.
	 */
	@Test
	void aLibraryTwoModsNestIsInEachOnesFamily(@TempDir Path dir) throws Exception {
		TranslatedMod first = mod(dir, "first", null, List.of("library"), TestJars.create().bytes("com/first/Main.class", cls("com/first/Main")));
		TranslatedMod second = mod(dir, "second", null, List.of("library"), TestJars.create()
				.bytes("com/second/Main.class", cls("com/second/Main"))
				.text(SERVICE, "com.library.Impl\n"));
		TranslatedMod library = mod(dir, "library", "first", List.of(), TestJars.create().bytes("com/library/Impl.class", cls("com/library/Impl")));
		inEveryOrder(List.of(first, second, library), Set.of(), decisions -> assertEquals(List.of("library"), declaring(decisions, second, SERVICE)));
	}

	/** A holder's own file of the path naming no provider makes way for the file naming its classes: the platforms skip it. */
	@Test
	void aHoldersFileNamingNoProviderMakesWay(@TempDir Path dir) throws Exception {
		TranslatedMod outer = mod(dir, "outer", null, TestJars.create()
				.bytes("com/shared/OuterPart.class", cls("com/shared/OuterPart"))
				.text(SERVICE, "com.inner.BridgeImpl\n"));
		TranslatedMod inner = mod(dir, "inner", "outer", TestJars.create()
				.bytes("com/inner/BridgeImpl.class", cls("com/inner/BridgeImpl"))
				.bytes("com/shared/InnerPart.class", cls("com/shared/InnerPart"))
				.text(SERVICE, "# none yet\n"));
		inEveryOrder(List.of(outer, inner), Set.of(), decisions -> {
			assertEquals(List.of("inner"), declaring(decisions, outer, SERVICE));
			assertTrue(decisions.get("inner").hides(SERVICE));
			assertNote(decisions, "inner", "has the service file " + SERVICE + ", which names no provider and makes way for outer's");
		});
	}

	/** A nested jar's file naming a class its parent has goes to the parent's module: the family reaches up as well as down. */
	@Test
	void aNestedJarsFileNamingItsParentsClassGoesToTheParent(@TempDir Path dir) throws Exception {
		TranslatedMod outer = mod(dir, "outer", null, TestJars.create()
				.bytes("com/outer/Impl.class", cls("com/outer/Impl"))
				.bytes("com/shared/OuterPart.class", cls("com/shared/OuterPart")));
		TranslatedMod inner = mod(dir, "inner", "outer", TestJars.create()
				.bytes("com/shared/InnerPart.class", cls("com/shared/InnerPart"))
				.text(SERVICE, "com.outer.Impl\n"));
		inEveryOrder(List.of(outer, inner), Set.of(), decisions -> assertEquals(List.of("outer"), declaring(decisions, inner, SERVICE)));
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

	/** Runs the guard over {@code mods} in each of their load orders on each platform: {@code check} holds on every one. */
	private static void inEveryOrder(List<TranslatedMod> mods, Set<String> platformPackages, Consumer<Map<String, PackageGuard.Decision>> check) {
		inEveryOrder(mods, platformPackages, (platform, decisions) -> check.accept(decisions));
	}

	/**
	 * Runs the guard over {@code mods} in each of their load orders, on each platform's reading of service files: no mod
	 * is disabled, every module is valid as that platform builds it, and {@code check} holds, given the platform.
	 */
	private static void inEveryOrder(List<TranslatedMod> mods, Set<String> platformPackages,
			BiConsumer<PlatformDescriptor.ServiceFiles, Map<String, PackageGuard.Decision>> check) {
		for (PlatformDescriptor.ServiceFiles platform : PlatformDescriptor.ServiceFiles.values()) {
			for (List<TranslatedMod> order : permutations(mods)) {
				PackageGuard.Result result = check(platform, order, platformPackages);
				List<String> ids = order.stream().map(TranslatedMod::neoForgeId).toList();
				try {
					assertEquals(List.of(), result.conflicts().stream().map(PackageGuard.Conflict::reason).toList(), "no mod disabled");
					assertModulesValid(platform, order, result.decisions());
					check.accept(platform, result.decisions());
				} catch (AssertionError e) {
					throw new AssertionError(platform + ", in the order " + ids + ": " + e.getMessage(), e);
				}
			}
		}
	}

	private static PackageGuard.Result check(List<TranslatedMod> mods, Set<String> platformPackages) {
		return check(AS_SERVICE_LOADER_DOES, mods, platformPackages);
	}

	private static PackageGuard.Result check(PlatformDescriptor.ServiceFiles platform, List<TranslatedMod> mods, Set<String> platformPackages) {
		return new PackageGuard(ArchiveLimits.DEFAULTS, platform, List.of(), List.of()).check(mods, platformPackages, ClassBytesSource.EMPTY);
	}

	/**
	 * Each module as the platform builds it from the files of its jar it keeps and those it serves from other jars, a
	 * module having one file of a service's path:
	 * <ul>
	 *   <li>NeoForge 21.1 (SecureJarHandler 3): each file directly in META-INF/services/ named as a class in a package,
	 *       strict UTF-8, each line trimmed a class the module has unless blank or a whole-line comment;</li>
	 *   <li>NeoForge's FML 10 to 12: each file in META-INF/services/ whose last name FML takes for a type named as a
	 *       class in a package, read as UTF-8 with replacement, a comment cut, each line a class the module has;</li>
	 *   <li>Forge: each file directly in META-INF/services/ strict UTF-8 (securemodules reads them all as it opens the
	 *       jar); what the module declares named as classes in packages, each provider a class it has; and each file it
	 *       has declared as ServiceLoader reads it, in the file's order.</li>
	 * </ul>
	 */
	private static void assertModulesValid(PlatformDescriptor.ServiceFiles platform, List<TranslatedMod> mods, Map<String, PackageGuard.Decision> decisions) {
		for (TranslatedMod mod : mods) {
			String id = mod.neoForgeId();
			PackageGuard.Decision decision = decisions.get(id);
			Map<String, byte[]> files = new HashMap<>();
			entries(mod.jar()).forEach((path, data) -> {
				if (!decision.hides(path)) files.put(path, data);
			});
			decision.borrowed().forEach((jar, paths) -> paths.forEach(path -> {
				byte[] previous = files.put(path, entries(jar).get(path));
				assertFalse(previous != null && path.startsWith(SERVICES), id + "'s module has two files " + path);
			}));
			files.forEach((path, data) -> {
				if (!path.startsWith(SERVICES)) return;
				String name = path.substring(SERVICES.length());
				boolean direct = name.indexOf('/') < 0;
				switch (platform) {
					case AS_THEY_ARE -> {
						if (!direct) return;
						assertTrue(qualified(name), id + "'s module declares " + path + ", whose name Java's modules don't take for a service");
						String text = strict(data);
						assertTrue(text != null, id + "'s module declares " + path + ", which isn't UTF-8");
						for (String line : text.split("\r\n|\r|\n")) {
							String provider = line.trim();
							if (provider.isEmpty() || provider.startsWith("#")) continue;
							assertTrue(qualified(provider) && files.containsKey(provider.replace('.', '/') + ".class"),
									id + "'s module declares " + path + " with " + provider + ", which it hasn't or the platform can't name");
						}
					}
					case AS_SERVICE_LOADER_DOES -> {
						String last = name.substring(name.lastIndexOf('/') + 1);
						if (!last.matches("[\\p{javaJavaIdentifierStart}][\\p{javaJavaIdentifierPart}]*(\\.[\\p{javaJavaIdentifierStart}][\\p{javaJavaIdentifierPart}]*)*")) return;
						assertTrue(direct && qualified(last), id + "'s module declares " + path + ", whose name Java's modules don't take for a service");
						for (String provider : PackageGuard.serviceLines(new String(data, StandardCharsets.UTF_8))) {
							assertTrue(qualified(provider) && files.containsKey(provider.replace('.', '/') + ".class"),
									id + "'s module declares " + path + " with " + provider + ", which it hasn't or the platform can't name");
						}
					}
					case FROM_THE_GUARD -> {
						if (!direct) return;
						assertTrue(strict(data) != null, id + "'s module has " + path + ", which isn't UTF-8, and securemodules reads it strictly");
						List<String> read = PackageGuard.serviceLines(new String(data, StandardCharsets.UTF_8)).stream().filter(PackageGuard::isBinaryName)
								.distinct().toList();
						if (qualified(name) && !read.isEmpty()) {
							assertEquals(read, decision.provides().get(name), id + "'s module declares " + name + " otherwise than its file " + path + " reads");
						}
					}
				}
			});
			if (platform == FROM_THE_GUARD) {
				decision.provides().forEach((service, providers) -> {
					assertTrue(qualified(service), id + "'s module declares the service " + service + ", whose name Java's modules don't take");
					for (String provider : providers) {
						assertTrue(qualified(provider) && files.containsKey(provider.replace('.', '/') + ".class"),
								id + "'s module declares " + service + " with " + provider + ", which it hasn't");
					}
				});
			}
		}
	}

	private static final String SERVICES = "META-INF/services/";

	/** Whether Java's modules take {@code name} for a service or a provider: a binary class name in a package. */
	private static boolean qualified(String name) {
		return name.indexOf('.') > 0 && PackageGuard.isBinaryName(name);
	}

	/** The bytes as strict UTF-8, or null. */
	private static String strict(byte[] data) {
		try {
			return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data)).toString();
		} catch (CharacterCodingException e) {
			return null;
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
		return mod(dir, id, parent, List.of(), jar);
	}

	/** The same, nesting the mods {@code nested} (as the scanner lists them: a library several mods nest is listed by each). */
	private static TranslatedMod mod(Path dir, String id, String parent, List<String> nested, TestJars jar) throws Exception {
		String json = TestJars.modJson(id, "1.0", "");
		Path file = jar.fabricModJson(json).write(dir.resolve(id + ".jar"));
		Candidate c = new Candidate(file, id + "-hash", FabricModJsonParser.parse(json), parent == null ? id + ".jar" : parent + ".jar!/" + id + ".jar", parent,
				parent == null ? 0 : 1, nested, new HashSet<>());
		return new TranslatedMod(c, id, file, dir.resolve(id + "-overlay.jar"), List.of(), 0, List.of(), List.of(), List.of(), Set.of(), Set.of(), Set.of(),
				Map.of(), false, 0);
	}
}
