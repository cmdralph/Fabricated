package io.github.cmdralph.fabricated.core.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.cmdralph.fabricated.core.metadata.FabricModJsonParser;
import io.github.cmdralph.fabricated.core.testutil.TestJars;
import net.fabricmc.loader.api.Version;

class DependencyResolverTest {
	private static Candidate mod(String id, String version, String extra) throws Exception {
		return new Candidate(Path.of(id + ".jar"), id, FabricModJsonParser.parse(TestJars.modJson(id, version, extra)), id + ".jar", null, 0, List.of(), Set.of());
	}

	private static DependencyResolver resolver() throws Exception {
		return new DependencyResolver()
				.builtin("minecraft", Version.parse("1.21.11"), "Minecraft")
				.builtin("fabricloader", Version.parse("0.19.5"), "Fabric Loader")
				.builtin("fabric-api-base", Version.parse("1.0.0"), "fabric-api-base")
				.neoForgeMod("cloth_config", Version.parse("15.0.0"), "Cloth Config");
	}

	@Test
	void disablesUnsatisfiedModsAndTheirDependents() throws Exception {
		Candidate lib = mod("needs-missing", "1.0", "\"depends\":{\"not-installed\":\"*\"}");
		Candidate user = mod("uses-lib", "1.0", "\"depends\":{\"needs-missing\":\">=1\"}");
		Candidate fine = mod("fine", "1.0", "\"depends\":{\"minecraft\":\"~1.21.11\",\"fabricloader\":\">=0.18\",\"fabric-api-base\":\"*\"}");
		DependencyResolver.Result r = resolver().resolve(List.of(user, lib, fine), Map.of(), Map.of("not-installed", "Install it."), Map.of());
		assertEquals(List.of("fine"), r.ordered().stream().map(Candidate::id).toList());
		assertEquals(2, r.disabled().size());
		DependencyResolver.Disabled missing = r.disabled().stream().filter(d -> d.candidate().id().equals("needs-missing")).findFirst().orElseThrow();
		assertTrue(missing.reason().contains("not-installed"));
		assertEquals("Install it.", missing.suggestion());
	}

	@Test
	void reportsVersionMismatchAndBreaks() throws Exception {
		Candidate tooNew = mod("wants-new-mc", "1.0", "\"depends\":{\"minecraft\":\">=26.1\"}");
		Candidate breaker = mod("breaker", "1.0", "\"breaks\":{\"fine\":\"*\"}");
		Candidate fine = mod("fine", "1.0", "");
		DependencyResolver.Result r = resolver().resolve(List.of(tooNew, breaker, fine), Map.of(), Map.of(), Map.of());
		assertEquals(List.of("fine"), r.ordered().stream().map(Candidate::id).toList());
		assertTrue(r.disabled().stream().anyMatch(d -> d.reason().contains("1.21.11 is installed")));
		assertTrue(r.disabled().stream().anyMatch(d -> d.reason().contains("incompatible")));
	}

	@Test
	void usesProvidesAliasesAndNeoForgeMods() throws Exception {
		Candidate api = mod("real-api", "2.0", "\"provides\":[\"api-alias\"]");
		Candidate user = mod("user", "1.0", "\"depends\":{\"api-alias\":\">=2\",\"cloth-config\":\">=15\",\"config-alias\":\"*\"}");
		Candidate aliased = mod("aliased", "1.0", "");
		DependencyResolver.Result r = resolver().resolve(List.of(user, api, aliased), Map.of("aliased", List.of("config-alias")), Map.of(), Map.of());
		assertTrue(r.disabled().isEmpty(), r.disabled().toString());
		assertEquals(List.of("cloth_config"), r.crossLoaderLinks().get("user"));
	}

	@Test
	void ordersDependenciesFirstDeterministically() throws Exception {
		Candidate c = mod("ccc", "1", "\"depends\":{\"bbb\":\"*\"}");
		Candidate b = mod("bbb", "1", "\"depends\":{\"aaa\":\"*\"}");
		Candidate a = mod("aaa", "1", "");
		Candidate z = mod("zzz", "1", "");
		DependencyResolver.Result r = resolver().resolve(List.of(z, c, b, a), Map.of(), Map.of(), Map.of());
		assertEquals(List.of("aaa", "bbb", "ccc", "zzz"), r.ordered().stream().map(Candidate::id).toList());
	}

	@Test
	void warnsAboutRecommendationsAndConflicts() throws Exception {
		Candidate x = mod("xxx", "1", "\"recommends\":{\"absent\":\"*\"},\"conflicts\":{\"yyy\":\"*\"}");
		Candidate y = mod("yyy", "1", "");
		DependencyResolver.Result r = resolver().resolve(List.of(x, y), Map.of(), Map.of(), Map.of());
		assertEquals(2, r.ordered().size());
		assertEquals(2, r.warnings().size());
	}

	@Test
	void aDependencyThatWasDisabledIsNotReportedAsMissing() throws Exception {
		Candidate user = mod("user", "1.0", "\"depends\":{\"object-builder\":\"*\"}");
		DependencyResolver.Result r = resolver().resolve(List.of(user), Map.of(), Map.of("object-builder", "Install Fabric API."),
				Map.of("object-builder", "Mixin pre-flight failed"));
		DependencyResolver.Disabled d = r.disabled().getFirst();
		assertTrue(d.reason().contains("installed but was not loaded: Mixin pre-flight failed"), d.reason());
		assertTrue(d.suggestion().startsWith("See object-builder"), d.suggestion());
	}
}
