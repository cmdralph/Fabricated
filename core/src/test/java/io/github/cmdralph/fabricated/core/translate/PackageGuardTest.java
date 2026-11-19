package io.github.cmdralph.fabricated.core.translate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.cmdralph.fabricated.core.archive.ArchiveLimits;
import io.github.cmdralph.fabricated.core.bytecode.ClassBytesSource;
import io.github.cmdralph.fabricated.core.metadata.FabricModJsonParser;
import io.github.cmdralph.fabricated.core.resolve.Candidate;
import io.github.cmdralph.fabricated.core.testutil.TestJars;

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
	 * left out of its own module.
	 */
	@Test
	void anOuterJarsServiceIsDeclaredByTheNestedModuleWithItsProvider(@TempDir Path dir) throws Exception {
		TranslatedMod outer = mod(dir, "outer", null, TestJars.create()
				.bytes("com/outer/Main.class", TestJars.emptyClass("com/outer/Main", "java/lang/Object"))
				.text(SERVICE, "com.inner.BridgeImpl\n"));
		TranslatedMod inner = mod(dir, "inner", "outer", TestJars.create()
				.bytes("com/inner/BridgeImpl.class", TestJars.emptyClass("com/inner/BridgeImpl", "java/lang/Object", "com/api/Bridge")));
		TranslatedMod api = mod(dir, "api", "outer", TestJars.create()
				.bytes("com/api/Bridge.class", TestJars.emptyClass("com/api/Bridge", "java/lang/Object")));
		inEveryOrder(List.of(outer, inner, api), Set.of(), decisions -> {
			assertTrue(decisions.get("outer").hides(SERVICE), "a module can only declare its own providers");
			assertEquals(Map.of(outer.jar(), Set.of(SERVICE)), decisions.get("inner").borrowed(), "the module with the provider declares it");
			assertEquals(Map.of(), decisions.get("api").borrowed());
			assertNote(decisions, "outer", "declares the service com.api.Bridge with com.inner.BridgeImpl, which inner's module has: the service file goes there,"
					+ " as a module can only declare its own providers");
			assertNote(decisions, "inner", "also declares outer's service com.api.Bridge, whose providers it has");
		});
	}

	/** Where the nested module declares the same service with the same providers, it is served once. */
	@Test
	void aHolderDeclaringTheSameServiceServesItOnce(@TempDir Path dir) throws Exception {
		TranslatedMod outer = mod(dir, "outer", null, TestJars.create().text(SERVICE, "com.inner.BridgeImpl\n"));
		TranslatedMod inner = mod(dir, "inner", "outer", TestJars.create()
				.bytes("com/inner/BridgeImpl.class", TestJars.emptyClass("com/inner/BridgeImpl", "java/lang/Object"))
				.text(SERVICE, "# the same\ncom.inner.BridgeImpl\n"));
		inEveryOrder(List.of(outer, inner), Set.of(), decisions -> {
			assertTrue(decisions.get("outer").hides(SERVICE));
			assertFalse(decisions.get("inner").hides(SERVICE), "its own file declares it");
			assertEquals(Map.of(), decisions.get("inner").borrowed(), "no second copy");
			assertNote(decisions, "outer", "declares the service com.api.Bridge with com.inner.BridgeImpl, which inner's module has and declares already;"
					+ " the service file is left out");
		});
	}

	/** Where it declares the service with other providers, it keeps its own file, and the outer one stays out. */
	@Test
	void aHolderDeclaringOtherProvidersKeepsItsOwn(@TempDir Path dir) throws Exception {
		TranslatedMod outer = mod(dir, "outer", null, TestJars.create().text(SERVICE, "com.inner.BridgeImpl\n"));
		TranslatedMod inner = mod(dir, "inner", "outer", TestJars.create()
				.bytes("com/inner/BridgeImpl.class", TestJars.emptyClass("com/inner/BridgeImpl", "java/lang/Object"))
				.bytes("com/inner/OtherImpl.class", TestJars.emptyClass("com/inner/OtherImpl", "java/lang/Object"))
				.text(SERVICE, "com.inner.OtherImpl\n"));
		inEveryOrder(List.of(outer, inner), Set.of(), decisions -> {
			assertTrue(decisions.get("outer").hides(SERVICE));
			assertFalse(decisions.get("inner").hides(SERVICE));
			assertEquals(Map.of(), decisions.get("inner").borrowed());
			assertNote(decisions, "outer", "declares the service com.api.Bridge with com.inner.BridgeImpl, which inner's module has but declares with other"
					+ " providers; the service file is left out, as a module can only declare its own providers");
		});
	}

	/** Providers in two modules: neither can declare the other's, so the file stays out. */
	@Test
	void providersInTwoModulesStayOut(@TempDir Path dir) throws Exception {
		TranslatedMod outer = mod(dir, "outer", null, TestJars.create().text(SERVICE, "com.inner.BridgeImpl\ncom.second.SecondImpl\n"));
		TranslatedMod inner = mod(dir, "inner", "outer", TestJars.create()
				.bytes("com/inner/BridgeImpl.class", TestJars.emptyClass("com/inner/BridgeImpl", "java/lang/Object")));
		TranslatedMod second = mod(dir, "second", "outer", TestJars.create()
				.bytes("com/second/SecondImpl.class", TestJars.emptyClass("com/second/SecondImpl", "java/lang/Object")));
		inEveryOrder(List.of(outer, inner, second), Set.of(), decisions -> {
			assertTrue(decisions.get("outer").hides(SERVICE));
			assertEquals(Map.of(), decisions.get("inner").borrowed());
			assertEquals(Map.of(), decisions.get("second").borrowed());
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
				.text(SERVICE, "com.inner.BridgeImpl\ncom.gone.Missing\n")
				.text(platformService, "com.platform.PlatformImpl\n"));
		TranslatedMod inner = mod(dir, "inner", "outer", TestJars.create()
				.bytes("com/inner/BridgeImpl.class", TestJars.emptyClass("com/inner/BridgeImpl", "java/lang/Object"))
				.bytes("com/platform/PlatformImpl.class", TestJars.emptyClass("com/platform/PlatformImpl", "java/lang/Object")));
		inEveryOrder(List.of(outer, inner), Set.of("com/platform"), decisions -> {
			assertTrue(decisions.get("outer").hides(SERVICE) && decisions.get("outer").hides(platformService));
			assertTrue(decisions.get("inner").excludedPackages().contains("com/platform"));
			assertEquals(Map.of(), decisions.get("inner").borrowed(), "nothing is written into a module Fabricated doesn't build");
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
				.bytes("com/outer/OwnImpl.class", TestJars.emptyClass("com/outer/OwnImpl", "java/lang/Object"))
				.text(SERVICE, "com.outer.OwnImpl\ncom.inner.BridgeImpl\n"));
		TranslatedMod inner = mod(dir, "inner", "outer", TestJars.create()
				.bytes("com/inner/BridgeImpl.class", TestJars.emptyClass("com/inner/BridgeImpl", "java/lang/Object")));
		inEveryOrder(List.of(outer, inner), Set.of(), decisions -> {
			assertTrue(decisions.get("outer").hides(SERVICE));
			assertEquals(Map.of(), decisions.get("inner").borrowed());
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
				.bytes("com/split/OuterPart.class", TestJars.emptyClass("com/split/OuterPart", "java/lang/Object"))
				.text(SERVICE, "com.split.BridgeImpl\n"));
		TranslatedMod inner = mod(dir, "inner", "outer", TestJars.create()
				.bytes(provider, TestJars.emptyClass("com/split/BridgeImpl", "java/lang/Object")));
		TranslatedMod api = mod(dir, "api", "outer", TestJars.create()
				.bytes("com/split/Bridge.class", TestJars.emptyClass("com/split/Bridge", "java/lang/Object")));
		inEveryOrder(List.of(outer, inner, api), Set.of(), decisions -> {
			List<String> declaring = new ArrayList<>();
			if (!decisions.get("outer").hides(SERVICE)) declaring.add("outer");
			decisions.forEach((id, d) -> {
				if (d.borrowed().values().stream().anyMatch(files -> files.contains(SERVICE))) declaring.add(id);
			});
			assertEquals(1, declaring.size(), "declared by " + declaring + ": " + decisions);
			String module = declaring.get(0);
			PackageGuard.Decision d = decisions.get(module);
			boolean serves = module.equals("inner") ? !d.excludedPackages().contains("com/split")
					: d.borrowed().getOrDefault(inner.jar(), Set.of()).contains(provider);
			assertTrue(serves, module + " declares the service and serves its provider: " + decisions);
		});
	}

	/** Runs the guard over {@code mods} in each of their load orders (a nested jar before its parent, or after): {@code check} holds in each. */
	private static void inEveryOrder(List<TranslatedMod> mods, Set<String> platformPackages, Consumer<Map<String, PackageGuard.Decision>> check) {
		for (List<TranslatedMod> order : permutations(mods)) {
			PackageGuard.Result result = new PackageGuard(ArchiveLimits.DEFAULTS, List.of()).check(order, platformPackages, ClassBytesSource.EMPTY);
			List<String> ids = order.stream().map(TranslatedMod::neoForgeId).toList();
			assertEquals(List.of(), result.conflicts(), ids.toString());
			try {
				check.accept(result.decisions());
			} catch (AssertionError e) {
				throw new AssertionError("in the order " + ids + ": " + e.getMessage(), e);
			}
		}
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
