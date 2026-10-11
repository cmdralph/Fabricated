package io.github.cmdralph.fabricated.fabricloader.version;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.version.VersionInterval;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;

class VersionSemanticsTest {
	private static Version v(String s) throws VersionParsingException {
		return Version.parse(s);
	}

	private static boolean matches(String predicate, String version) throws VersionParsingException {
		return VersionPredicate.parse(predicate).test(v(version));
	}

	@Test
	void parsesSemanticAndFreeFormVersions() throws Exception {
		assertInstanceOf(SemanticVersion.class, v("1.2.3"));
		assertInstanceOf(SemanticVersion.class, v("1.2.3-beta.1+build.5"));
		assertInstanceOf(StringVersion.class, v("1.2.3_fabric"));
		assertInstanceOf(StringVersion.class, v("snapshot-24w01a!"));
		assertThrows(VersionParsingException.class, () -> v(""));
		assertThrows(VersionParsingException.class, () -> SemanticVersion.parse("1.x"));
	}

	@Test
	void comparesLikeFabric() throws Exception {
		assertTrue(v("1.10.0").compareTo(v("1.9.9")) > 0);
		assertEquals(0, v("1.0").compareTo(v("1.0.0")));
		assertTrue(v("1.0.0-alpha").compareTo(v("1.0.0")) < 0);
		assertTrue(v("1.0.0-alpha.1").compareTo(v("1.0.0-alpha.beta")) < 0, "numeric identifiers sort before alphanumeric");
		assertTrue(v("1.0.0-alpha.2").compareTo(v("1.0.0-alpha.10")) < 0, "numeric identifiers compare numerically");
		assertTrue(v("1.0.0-beta").compareTo(v("1.0.0-alpha")) > 0);
		assertEquals(0, v("1.0.0+a").compareTo(v("1.0.0+b")), "build metadata is ignored");
		assertTrue(v("26.1.2").compareTo(v("26.1")) > 0);
	}

	@Test
	void predicatesFollowFabricSemantics() throws Exception {
		assertTrue(matches("*", "1.0.0"));
		assertTrue(matches("", "1.0.0"));
		assertTrue(matches(">=1.2 <2", "1.5.0"));
		assertFalse(matches(">=1.2 <2", "2.0.0"));
		assertTrue(matches("~1.2.3", "1.2.9"));
		assertFalse(matches("~1.2.3", "1.3.0"));
		assertTrue(matches("^1.2.3", "1.9.0"));
		assertFalse(matches("^1.2.3", "2.0.0"));
		assertTrue(matches("1.2.x", "1.2.7"));
		assertFalse(matches("1.2.x", "1.3.0"));
		assertTrue(matches("1.x", "1.99.0"));
		assertFalse(matches("1.x", "2.0.0"));
		assertTrue(matches("=1.0.0", "1.0.0"));
		assertFalse(matches("1.0.0", "1.0.1"));
		assertTrue(matches("1.21.11", "1.21.11"));
		assertTrue(matches("~26.1", "26.1.2"));
		assertTrue(matches("free_form", "free_form"));
	}

	@Test
	void rejectsInvalidPredicates() {
		assertThrows(VersionParsingException.class, () -> VersionPredicate.parse(">1.x"));
		assertThrows(VersionParsingException.class, () -> VersionPredicate.parse(">not_semver!"));
	}

	@Test
	void wildcardUpperBoundExcludesNextPrereleases() throws Exception {
		assertFalse(matches("1.2.x", "1.3.0-alpha"));
	}

	@Test
	void intervalsCombineCorrectly() throws Exception {
		VersionInterval a = VersionPredicate.parse(">=1.0 <2.0").getInterval();
		VersionInterval b = VersionPredicate.parse(">=1.5").getInterval();
		VersionInterval and = VersionInterval.and(a, b);
		assertEquals(v("1.5"), and.getMin());
		assertTrue(and.isMinInclusive());
		assertEquals(v("2.0"), and.getMax());
		assertFalse(and.isMaxInclusive());

		assertNull(VersionInterval.and(VersionPredicate.parse("<1.0").getInterval(), VersionPredicate.parse(">=2.0").getInterval()));

		List<VersionInterval> merged = VersionInterval.or(List.of(VersionPredicate.parse(">=1 <2").getInterval()),
				VersionPredicate.parse(">=2 <3").getInterval());
		assertEquals(1, merged.size(), "touching intervals merge");

		List<VersionInterval> not = VersionInterval.not(VersionPredicate.parse(">=1.0").getInterval());
		assertEquals(1, not.size());
		assertNull(not.getFirst().getMin());
		assertEquals(v("1.0"), not.getFirst().getMax());
		assertFalse(not.getFirst().isMaxInclusive());
	}
}
