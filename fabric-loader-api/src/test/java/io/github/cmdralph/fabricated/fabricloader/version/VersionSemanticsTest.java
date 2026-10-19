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
	void repeatedTrailingWildcardsMeanOne() throws Exception {
		assertTrue(matches("1.x.x", "1.5.0"));
		assertFalse(matches("1.x.x", "2.0.0"));
		assertEquals(VersionPredicate.parse("1.x").getInterval(), VersionPredicate.parse("1.x.x").getInterval());
	}

	@Test
	void prereleasesCompareLikeFabric() throws Exception {
		// Only 0 and numbers without leading zeros are numeric; numeric parts sort before others.
		assertTrue(v("1.0.0-beta.01").compareTo(v("1.0.0-beta.1")) > 0);
		assertTrue(v("1.0.0-beta.2").compareTo(v("1.0.0-beta.10")) < 0);
		assertTrue(v("1.0.0-beta").compareTo(v("1.0.0-beta.1")) < 0);
		assertFalse(matches("^1.0", "2.0.0-0"));
	}

	@Test
	void complementOfAFreeFormVersionExcludesIt() throws Exception {
		Version foo = v("foo_bar");
		List<VersionInterval> rest = VersionInterval.not(VersionPredicate.parse("foo_bar").getInterval());
		assertEquals(2, rest.size());
		assertTrue(rest.stream().noneMatch(i -> i.getMin() == null && i.getMax() == null), rest.toString());
		assertEquals(foo, rest.getFirst().getMax());
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

	private static VersionInterval interval(String min, boolean minInclusive, String max, boolean maxInclusive) throws VersionParsingException {
		return new VersionIntervalImpl(min == null ? null : v(min), minInclusive, max == null ? null : v(max), maxInclusive);
	}

	@Test
	void intersectionPicksTighterBounds() throws Exception {
		// Unbounded sides take the other interval's bound.
		assertEquals(interval("1", true, "2", false), VersionInterval.and(interval("1", true, null, false), interval(null, false, "2", false)));
		// Equal bounds are inclusive only if both are.
		assertEquals(interval("1", false, "2", false), VersionInterval.and(interval("1", true, "2", true), interval("1", false, "2", false)));
		assertEquals(interval("1", true, "2", true), VersionInterval.and(interval("1", true, "2", true), interval("1", true, "3", false)));
		// Lower min / higher max lose.
		assertEquals(interval("1.5", false, "2", true), VersionInterval.and(interval("1", true, "3", true), interval("1.5", false, "2", true)));
		// A single shared point survives only if both include it.
		assertEquals(interval("1", true, "1", true), VersionInterval.and(interval(null, false, "1", true), interval("1", true, null, false)));
		assertNull(VersionInterval.and(interval(null, false, "1", false), interval("1", true, null, false)));
	}

	@Test
	void unionKeepsLooserUpperBound() throws Exception {
		assertEquals(List.of(interval("1", true, "3", false)),
				VersionInterval.or(List.of(interval("1", true, "2", true)), interval("1.5", true, "3", false)));
		assertEquals(List.of(interval("1", true, "2", true)),
				VersionInterval.or(List.of(interval("1", true, "2", false)), interval("1.5", true, "2", true)));
		assertEquals(List.of(interval("1", true, "2", false)),
				VersionInterval.or(List.of(interval("1", true, "2", false)), interval("1.5", true, "1.8", true)));
		assertEquals(List.of(interval("1", true, null, false)),
				VersionInterval.or(List.of(interval("1", true, "2", false)), interval("1.5", true, null, false)));
		assertEquals(2, VersionInterval.or(List.of(interval("1", true, "2", false)), interval("2", false, "3", false)).size(),
				"intervals that only meet at an excluded point stay separate");
	}
}
