package io.github.cmdralph.fabricated.core.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.cmdralph.fabricated.core.archive.ArchiveLimits;
import io.github.cmdralph.fabricated.core.cache.TranslationCache;
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
}
