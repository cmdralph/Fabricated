package io.github.cmdralph.fabricated.core.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class LoadOrderingsTest {
	private static LoadOrderings.Result run(Object... pairs) {
		Map<String, List<String>> after = new LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			@SuppressWarnings("unchecked")
			List<String> targets = (List<String>) pairs[i + 1];
			after.put((String) pairs[i], targets);
		}
		return LoadOrderings.withoutCycles(after);
	}

	@Test
	void aPairLosesOneOrdering() {
		LoadOrderings.Result r = run("a", List.of("b"), "b", List.of("a"));
		assertEquals(List.of(new LoadOrderings.LeftOut("b", "a")), r.leftOut());
		assertEquals(Map.of("a", List.of("b"), "b", List.of()), r.after());
	}

	@Test
	void aRingLosesOneOrdering() {
		LoadOrderings.Result r = run("a", List.of("b"), "b", List.of("c"), "c", List.of("a"));
		assertEquals(List.of(new LoadOrderings.LeftOut("c", "a")), r.leftOut());
		assertEquals(List.of("b"), r.after().get("a"));
		assertEquals(List.of("c"), r.after().get("b"));
	}

	@Test
	void orderingsOutsideACycleStay() {
		// A diamond has no cycle; a mod depending on a pair keeps its ordering, whichever way its id sorts.
		LoadOrderings.Result diamond = run("a", List.of("b", "c"), "b", List.of("d"), "c", List.of("d"), "d", List.of());
		assertTrue(diamond.leftOut().isEmpty(), diamond.toString());
		LoadOrderings.Result tail = run("aaa", List.of("pair_a"), "pair_a", List.of("pair_b"), "pair_b", List.of("pair_a"),
				"zzz", List.of("pair_b"));
		assertEquals(List.of("pair_a"), tail.after().get("aaa"));
		assertEquals(List.of("pair_b"), tail.after().get("zzz"));
		assertEquals(1, tail.leftOut().size(), tail.toString());
	}

	@Test
	void whatIsLeftHasNoCycle() {
		// Two rings sharing a mod, and a pair inside one of them.
		Map<String, List<String>> after = new LinkedHashMap<>();
		after.put("a", List.of("b", "d"));
		after.put("b", List.of("c", "a"));
		after.put("c", List.of("a"));
		after.put("d", List.of("e"));
		after.put("e", List.of("a"));
		LoadOrderings.Result r = LoadOrderings.withoutCycles(after);
		assertTrue(acyclic(r.after()), r.toString());
		int kept = r.after().values().stream().mapToInt(List::size).sum();
		assertEquals(7, kept + r.leftOut().size(), "every ordering is kept or left out");
	}

	@Test
	void unknownTargetsAndSelfOrderingsAreHandled() {
		LoadOrderings.Result r = run("a", List.of("a", "neoforge_mod", "b", "b"), "b", List.of());
		assertEquals(List.of("b", "neoforge_mod"), r.after().get("a"), "kept, without the duplicate");
		assertEquals(List.of(new LoadOrderings.LeftOut("a", "a")), r.leftOut());
	}

	@Test
	void aLongChainDoesNotExhaustTheStack() {
		// The jars are untrusted: a chain of dependencies as long as a mods folder allows is walked without recursion.
		Map<String, List<String>> after = new HashMap<>();
		int n = 200_000;
		for (int i = 0; i < n; i++) after.put(String.format("m%07d", i), i + 1 < n ? List.of(String.format("m%07d", i + 1)) : List.of("m0000000"));
		LoadOrderings.Result r = LoadOrderings.withoutCycles(after);
		assertEquals(1, r.leftOut().size());
		assertTrue(acyclic(r.after()));
	}

	private static boolean acyclic(Map<String, List<String>> after) {
		// Kahn's algorithm over the kept orderings.
		Map<String, Integer> indegree = new HashMap<>();
		after.keySet().forEach(id -> indegree.put(id, 0));
		after.values().forEach(targets -> targets.forEach(t -> indegree.merge(t, 1, Integer::sum)));
		List<String> ready = new ArrayList<>();
		indegree.forEach((id, n) -> {
			if (n == 0) ready.add(id);
		});
		int seen = 0;
		while (!ready.isEmpty()) {
			String id = ready.remove(ready.size() - 1);
			seen++;
			for (String t : after.getOrDefault(id, List.of())) {
				if (indegree.merge(t, -1, Integer::sum) == 0) ready.add(t);
			}
		}
		return seen == indegree.size();
	}
}
