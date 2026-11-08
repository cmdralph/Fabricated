package io.github.cmdralph.fabricated.core.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import net.fabricmc.loader.api.metadata.ModEnvironment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.cmdralph.fabricated.core.archive.ArchiveLimits;
import io.github.cmdralph.fabricated.core.cache.TranslationCache;
import io.github.cmdralph.fabricated.core.metadata.FabricModMetadata.MixinEntry;
import io.github.cmdralph.fabricated.core.testutil.TestJars;

class CandidateScannerTest {
	private static byte[] library(String version) throws Exception {
		return TestJars.create().fabricModJson(TestJars.modJson("lib", version, "")).toBytes();
	}

	private static Path holder(Path dir, String file, String version, byte[] nested) throws Exception {
		return TestJars.create()
				.fabricModJson(TestJars.modJson("holder", version, "\"jars\":[{\"file\":\"META-INF/jars/lib.jar\"}]"))
				.bytes("META-INF/jars/lib.jar", nested)
				.write(dir.resolve(file));
	}

	@Test
	void nestedJarsOfAnUnusedCopyAreNotLoaded(@TempDir Path game) throws Exception {
		// The older holder bundles a newer library; only the newer holder (and its own library) may load.
		Path older = holder(game, "holder-1.jar", "1.0", library("9.0"));
		Path newer = holder(game, "holder-2.jar", "2.0", library("1.0"));
		CandidateScanner.Result result = new CandidateScanner(new TranslationCache(game, true, true), ArchiveLimits.DEFAULTS)
				.scan(List.of(older, newer));
		Map<String, Candidate> byId = result.candidates().stream().collect(Collectors.toMap(Candidate::id, Function.identity()));
		assertEquals("2.0", byId.get("holder").metadata().getVersion().getFriendlyString());
		assertEquals("1.0", byId.get("lib").metadata().getVersion().getFriendlyString(), "the library comes from the chosen holder");
		assertTrue(byId.get("lib").origin().startsWith("holder-2.jar!/"), byId.get("lib").origin());
	}

	@Test
	void highestVersionWinsAcrossTopLevelAndNested(@TempDir Path game) throws Exception {
		Path holder = holder(game, "holder.jar", "1.0", library("1.0"));
		Path standalone = TestJars.create().fabricModJson(TestJars.modJson("lib", "2.0", "")).write(game.resolve("lib.jar"));
		CandidateScanner.Result result = new CandidateScanner(new TranslationCache(game, true, true), ArchiveLimits.DEFAULTS)
				.scan(List.of(holder, standalone));
		Candidate lib = result.candidates().stream().filter(c -> c.id().equals("lib")).findFirst().orElseThrow();
		assertEquals("2.0", lib.metadata().getVersion().getFriendlyString());
		assertTrue(result.notes().stream().anyMatch(n -> n.contains("instead of older")), result.notes().toString());
	}

	/** As adventure-platform-fabric declares its nested shared jar's config: on Fabric, Mixin finds it on the class path. */
	@Test
	void aMixinConfigInANestedJarIsAppliedByThatJar(@TempDir Path game) throws Exception {
		byte[] shared = TestJars.create().fabricModJson(TestJars.modJson("shared", "1.0", ""))
				.text("shared.mixins.json", "{\"package\":\"com.shared.mixin\",\"mixins\":[]}")
				.toBytes();
		Path holder = TestJars.create()
				.fabricModJson(TestJars.modJson("holder", "1.0", "\"jars\":[{\"file\":\"META-INF/jars/shared.jar\"}],"
						+ "\"mixins\":[\"own.mixins.json\",{\"config\":\"shared.mixins.json\",\"environment\":\"client\"},\"absent.mixins.json\"]"))
				.text("own.mixins.json", "{\"package\":\"com.holder.mixin\",\"mixins\":[]}")
				.bytes("META-INF/jars/shared.jar", shared)
				.write(game.resolve("holder.jar"));
		CandidateScanner.Result result = new CandidateScanner(new TranslationCache(game, true, true), ArchiveLimits.DEFAULTS).scan(List.of(holder));
		Map<String, Candidate> byId = result.candidates().stream().collect(Collectors.toMap(Candidate::id, Function.identity()));
		assertEquals(List.of(new MixinEntry("own.mixins.json", ModEnvironment.UNIVERSAL), new MixinEntry("absent.mixins.json", ModEnvironment.UNIVERSAL)),
				byId.get("holder").metadata().mixins(), "a config no jar holds stays, for translation to report");
		assertEquals(List.of(new MixinEntry("shared.mixins.json", ModEnvironment.CLIENT)), byId.get("shared").metadata().mixins());
		assertTrue(result.notes().contains("holder's mixin config shared.mixins.json is in its nested jar shared, which applies it"), result.notes().toString());
	}

	/** As Polymer's bundle nests its packet library: bundled, core, networking, common, then the library (LIMIT-2). */
	@Test
	void aLibraryNestedFourJarsDeepLoads(@TempDir Path game) throws Exception {
		byte[] jar = library("1.0");
		for (String id : List.of("common", "networking", "core")) {
			jar = TestJars.create().fabricModJson(TestJars.modJson(id, "1.0", "\"jars\":[{\"file\":\"META-INF/jars/inner.jar\"}]"))
					.bytes("META-INF/jars/inner.jar", jar).toBytes();
		}
		Path bundled = TestJars.create().fabricModJson(TestJars.modJson("bundled", "1.0", "\"jars\":[{\"file\":\"META-INF/jars/inner.jar\"}]"))
				.bytes("META-INF/jars/inner.jar", jar).write(game.resolve("bundled.jar"));
		CandidateScanner.Result result = new CandidateScanner(new TranslationCache(game, true, true), ArchiveLimits.DEFAULTS).scan(List.of(bundled));
		assertEquals(List.of("bundled", "common", "core", "lib", "networking"), result.candidates().stream().map(Candidate::id).sorted().toList());
		assertTrue(result.notes().stream().noneMatch(n -> n.contains("deeper than the limit")), result.notes().toString());
		// A lower limit, which a player may set, still holds.
		ArchiveLimits shallow = new ArchiveLimits(256L << 20, 128L << 20, 512L << 20, 65_536, 100, 3);
		CandidateScanner.Result limited = new CandidateScanner(new TranslationCache(game, true, true), shallow).scan(List.of(bundled));
		assertEquals(List.of("bundled", "common", "core", "networking"), limited.candidates().stream().map(Candidate::id).sorted().toList());
		assertTrue(limited.notes().contains("common nests jars deeper than the limit of 3; they were ignored"), limited.notes().toString());
	}

	@Test
	void nestedJarsShareOneBudgetPerTopLevelJar(@TempDir Path game) throws Exception {
		java.util.Random random = new java.util.Random(1);
		byte[] payload = new byte[3000];
		random.nextBytes(payload);
		byte[] libA = TestJars.create().fabricModJson(TestJars.modJson("lib-a", "1.0", "")).bytes("a.bin", payload).toBytes();
		random.nextBytes(payload);
		byte[] libB = TestJars.create().fabricModJson(TestJars.modJson("lib-b", "1.0", "")).bytes("b.bin", payload).toBytes();
		byte[] mid = TestJars.create()
				.fabricModJson(TestJars.modJson("mid", "1.0", "\"jars\":[{\"file\":\"a.jar\"},{\"file\":\"b.jar\"}]"))
				.bytes("a.jar", libA).bytes("b.jar", libB).toBytes();
		Path top = TestJars.create().fabricModJson(TestJars.modJson("top", "1.0", "\"jars\":[{\"file\":\"mid.jar\"}]"))
				.bytes("mid.jar", mid).write(game.resolve("top.jar"));
		// Each archive fits on its own; the tree does not.
		long perArchive = mid.length + libA.length + 512;
		ArchiveLimits limits = new ArchiveLimits(1 << 20, 1 << 20, perArchive, 100, 100, 3);
		CandidateScanner.Result result = new CandidateScanner(new TranslationCache(game, true, true), limits).scan(List.of(top));
		List<String> ids = result.candidates().stream().map(Candidate::id).sorted().toList();
		assertEquals(List.of("lib-a", "mid", "top"), ids);
		assertTrue(result.rejected().stream().anyMatch(r -> r.origin().endsWith("!/b.jar") && r.reason().contains("in all")), result.rejected().toString());
	}

	/** SEC-5: each top-level jar's nested jars fit its own budget, and many such jars still share the launch's. */
	@Test
	void nestedJarsOfAllTopLevelJarsShareTheLaunchsBudget(@TempDir Path game) throws Exception {
		java.util.Random random = new java.util.Random(2);
		long perArchive = 64 << 10;
		List<Path> tops = new java.util.ArrayList<>();
		for (int i = 0; i < 14; i++) {
			byte[] payload = new byte[40 << 10];
			random.nextBytes(payload);
			byte[] lib = TestJars.create().fabricModJson(TestJars.modJson("lib-" + i, "1.0", "")).bytes("p.bin", payload).toBytes();
			tops.add(TestJars.create().fabricModJson(TestJars.modJson("top-" + i, "1.0", "\"jars\":[{\"file\":\"lib.jar\"}]"))
					.bytes("lib.jar", lib).write(game.resolve(String.format("top-%02d.jar", i))));
		}
		ArchiveLimits limits = new ArchiveLimits(1 << 20, 1 << 20, perArchive, 100, 100, 3);
		CandidateScanner.Result result = new CandidateScanner(new TranslationCache(game, true, true), limits).scan(tops);
		// Eight archives' worth, 512 KiB, holds twelve of the 40 KiB libraries; the thirteenth and fourteenth are left out.
		long libs = result.candidates().stream().filter(c -> c.id().startsWith("lib-")).count();
		assertEquals(12, libs, result.rejected().toString());
		assertEquals(List.of("top-12.jar!/lib.jar", "top-13.jar!/lib.jar"), result.rejected().stream().map(CandidateScanner.Rejected::origin).toList());
		assertTrue(result.rejected().stream().allMatch(r -> r.reason().startsWith("nested jars of this launch's mods exceed the limit of 8192 jars")),
				result.rejected().toString());
		assertEquals(14, result.candidates().stream().filter(c -> c.id().startsWith("top-")).count(), "the jars nesting them still load");
	}
}
