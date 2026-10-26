package io.github.cmdralph.fabricated.core.resolve;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The load orderings translated mods' descriptors declare, without cycles (ORDER-1). Fabric lets mods depend on each
 * other, in pairs or rings, and orders nothing by dependencies; NeoForge's and Forge's sorters stop the game at a cycle
 * of orderings ("Mod Sorting failed", "Detected Cycles").
 */
public final class LoadOrderings {
	/** An ordering left out: {@code mod} no longer loads after {@code after}. */
	public record LeftOut(String mod, String after) {
	}

	/** The orderings kept, per mod in the input's order, and those left out. */
	public record Result(Map<String, List<String>> after, List<LeftOut> leftOut) {
	}

	private LoadOrderings() {
	}

	/**
	 * Leaves out the orderings that close a cycle. A depth-first walk, in id order, leaves out each ordering back to a mod
	 * still on its path: such an ordering is always part of a cycle, and once all of them are gone none is left, so a pair
	 * loses one ordering, a ring one, and an ordering outside a cycle stays. The walk keeps its own stack, as the mods are
	 * untrusted input and their chain of dependencies may be long.
	 *
	 * @param after each mod's id to the ids it should load after; ids that aren't keys are kept and never walked
	 */
	public static Result withoutCycles(Map<String, List<String>> after) {
		Map<String, List<String>> sorted = new TreeMap<>();
		after.forEach((id, targets) -> sorted.put(id, targets.stream().distinct().sorted().toList()));
		Map<String, Boolean> onPath = new HashMap<>();
		Map<String, List<String>> kept = new HashMap<>();
		List<LeftOut> leftOut = new ArrayList<>();
		for (String root : sorted.keySet()) {
			if (onPath.containsKey(root)) continue;
			Deque<Map.Entry<String, Iterator<String>>> path = new ArrayDeque<>();
			onPath.put(root, true);
			path.push(Map.entry(root, sorted.get(root).iterator()));
			while (!path.isEmpty()) {
				String mod = path.peek().getKey();
				Iterator<String> targets = path.peek().getValue();
				if (!targets.hasNext()) {
					onPath.put(mod, false);
					path.pop();
					continue;
				}
				String target = targets.next();
				Boolean state = onPath.get(target);
				if (Boolean.TRUE.equals(state)) {
					leftOut.add(new LeftOut(mod, target));
					continue;
				}
				kept.computeIfAbsent(mod, k -> new ArrayList<>()).add(target);
				if (state == null && sorted.containsKey(target)) {
					onPath.put(target, true);
					path.push(Map.entry(target, sorted.get(target).iterator()));
				}
			}
		}
		Map<String, List<String>> result = new LinkedHashMap<>();
		for (String id : after.keySet()) result.put(id, List.copyOf(kept.getOrDefault(id, List.of())));
		return new Result(result, List.copyOf(leftOut));
	}
}
