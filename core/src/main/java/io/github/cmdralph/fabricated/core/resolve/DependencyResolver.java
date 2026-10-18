package io.github.cmdralph.fabricated.core.resolve;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;

import io.github.cmdralph.fabricated.core.metadata.ModDependencyImpl;
import io.github.cmdralph.fabricated.core.metadata.ModIds;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ModDependency;

/**
 * Resolves Fabric dependencies before anything is handed to NeoForge.
 *
 * <p>NeoForge treats an unmet required dependency as a fatal error for the whole game. Fabricated
 * instead disables just the affected mod (and, transitively, the mods that depend on it) and explains
 * why. Providers are, in order: other active Fabric candidates (by id or {@code provides}), built-ins
 * ({@code minecraft}, {@code java}, {@code fabricloader}, native Fabric API modules), and NeoForge mods
 * (matched by exact id or by the Fabric id with {@code -} replaced by {@code _}).
 */
public final class DependencyResolver {
	/** Something that can satisfy a dependency. */
	public record Provider(String id, Version version, String description, boolean crossLoader) {
	}

	/** A candidate that will not load, with the reason and a suggested fix. */
	public record Disabled(Candidate candidate, String reason, String suggestion) {
	}

	/** Resolution output. {@code ordered} is the load order (dependencies first). */
	public record Result(List<Candidate> ordered, List<Disabled> disabled, List<String> warnings,
			Map<String, List<String>> crossLoaderLinks) {
	}

	private final Map<String, Provider> builtins = new HashMap<>();
	private final Map<String, Provider> neoForgeMods = new HashMap<>();

	/** Registers a built-in provider (Minecraft, Java, Fabric Loader, native Fabric API modules). */
	public DependencyResolver builtin(String id, Version version, String description) {
		builtins.put(id, new Provider(id, version, description, false));
		return this;
	}

	/** Registers a loaded NeoForge mod. */
	public DependencyResolver neoForgeMod(String id, Version version, String displayName) {
		neoForgeMods.put(id, new Provider(id, version, displayName + " (NeoForge mod)", true));
		return this;
	}

	/**
	 * @param candidates   candidates that survived config, environment and Fabric API filtering
	 * @param aliases      extra ids each candidate provides (from config), keyed by candidate id
	 * @param missingHints optional install hints for ids that no provider offers (e.g. Fabric API)
	 * @param unavailable  mods that are installed but were disabled earlier (pre-flight, linkage...), by id, with the
	 *                     reason: a mod requiring one is told so, instead of being told to install it
	 */
	public Result resolve(List<Candidate> candidates, Map<String, List<String>> aliases, Map<String, String> missingHints,
			Map<String, String> unavailable) {
		Map<String, Candidate> active = new LinkedHashMap<>();
		for (Candidate c : candidates) active.put(c.id(), c);
		List<Disabled> disabled = new ArrayList<>();
		List<String> warnings = new ArrayList<>();
		Map<String, List<String>> crossLoader = new LinkedHashMap<>();
		// Mods disabled here join those disabled earlier, so their dependents are told why, not to install them.
		Map<String, String> notLoaded = new HashMap<>(unavailable);

		boolean changed = true;
		while (changed) {
			changed = false;
			Map<String, Provider> providers = providersFor(active, aliases);
			for (Candidate c : new ArrayList<>(active.values())) {
				String problem = null;
				String suggestion = null;
				for (ModDependency dep : c.metadata().getDependencies()) {
					if (dep.getKind() == ModDependency.Kind.DEPENDS) {
						Provider p = find(providers, dep.getModId());
						if (p == null) {
							String why = notLoaded.get(dep.getModId());
							if (why != null) {
								if (why.length() > 200) why = why.substring(0, 197) + "...";
								problem = "requires " + describe(dep) + ", which is installed but was not loaded: " + why;
								suggestion = "See " + dep.getModId() + " in this report; " + c.id() + " can load once it does.";
							} else {
								problem = "requires " + describe(dep) + ", which is not installed";
								suggestion = missingHints.getOrDefault(dep.getModId(),
										"Install " + dep.getModId() + " (a Fabric or NeoForge build) or remove " + c.id() + ".");
							}
							break;
						}
						if (!dep.matches(p.version())) {
							problem = "requires " + describe(dep) + ", but " + p.description() + " " + p.version().getFriendlyString() + " is installed";
							suggestion = "Install a matching version of " + dep.getModId() + ", or a version of " + c.id() + " made for it.";
							break;
						}
						if (p.crossLoader()) {
							crossLoader.computeIfAbsent(c.id(), k -> new ArrayList<>()).add(p.id());
						}
					} else if (dep.getKind() == ModDependency.Kind.BREAKS) {
						Provider p = find(providers, dep.getModId());
						if (p != null && dep.matches(p.version()) && !p.id().equals(c.id())) {
							problem = "is incompatible with " + p.description() + " " + p.version().getFriendlyString();
							suggestion = "Remove one of " + c.id() + " or " + dep.getModId() + ".";
							break;
						}
					}
				}
				if (problem != null) {
					active.remove(c.id());
					disabled.add(new Disabled(c, problem, suggestion));
					String reason = c.id() + " " + problem;
					notLoaded.putIfAbsent(c.id(), reason);
					for (String provided : c.metadata().getProvides()) notLoaded.putIfAbsent(provided, reason);
					changed = true;
				}
			}
		}

		Map<String, Provider> providers = providersFor(active, aliases);
		for (Candidate c : active.values()) {
			for (ModDependency dep : c.metadata().getDependencies()) {
				Provider p = find(providers, dep.getModId());
				if (dep.getKind() == ModDependency.Kind.DEPENDS && p != null && p.crossLoader()) {
					List<String> links = crossLoader.computeIfAbsent(c.id(), k -> new ArrayList<>());
					if (!links.contains(p.id())) links.add(p.id());
				} else if (dep.getKind() == ModDependency.Kind.RECOMMENDS && (p == null || !dep.matches(p.version()))) {
					warnings.add(c.id() + " recommends " + describe(dep));
				} else if (dep.getKind() == ModDependency.Kind.CONFLICTS && p != null && dep.matches(p.version())) {
					warnings.add(c.id() + " conflicts with " + p.description() + "; it may not work correctly");
				}
			}
		}

		return new Result(order(active, aliases), disabled, warnings, crossLoader);
	}

	private Map<String, Provider> providersFor(Map<String, Candidate> active, Map<String, List<String>> aliases) {
		Map<String, Provider> providers = new HashMap<>(builtins);
		for (Candidate c : active.values()) {
			Provider p = new Provider(c.id(), c.metadata().getVersion(), c.metadata().getName(), false);
			providers.put(c.id(), p);
			for (String provided : c.metadata().getProvides()) providers.putIfAbsent(provided, p);
			for (String alias : aliases.getOrDefault(c.id(), List.of())) providers.putIfAbsent(alias, p);
		}
		return providers;
	}

	private Provider find(Map<String, Provider> providers, String id) {
		Provider p = providers.get(id);
		if (p != null) return p;
		if (id.equals("fabric")) {
			p = providers.get("fabric-api");
			if (p != null) return p;
		}
		p = neoForgeMods.get(id);
		if (p != null) return p;
		return neoForgeMods.get(ModIds.toNeoForgeId(id));
	}

	private static String describe(ModDependency dep) {
		String req = dep instanceof ModDependencyImpl impl ? impl.requirementString() : dep.getVersionRequirements().toString();
		return dep.getModId() + " " + req;
	}

	/** Dependencies first; ties broken by id so the order is deterministic. Cycles are appended by id. */
	static List<Candidate> order(Map<String, Candidate> active, Map<String, List<String>> aliases) {
		Map<String, Integer> indegree = new HashMap<>();
		Map<String, List<String>> dependents = new HashMap<>();
		Map<String, String> providerOf = new HashMap<>();
		for (Candidate c : active.values()) providerOf.put(c.id(), c.id());
		for (Candidate c : active.values()) {
			for (String p : c.metadata().getProvides()) providerOf.putIfAbsent(p, c.id());
			for (String alias : aliases.getOrDefault(c.id(), List.of())) providerOf.putIfAbsent(alias, c.id());
		}
		for (Candidate c : active.values()) {
			Set<String> d = new TreeSet<>();
			for (ModDependency dep : c.metadata().getDependencies()) {
				if (dep.getKind() != ModDependency.Kind.DEPENDS && dep.getKind() != ModDependency.Kind.RECOMMENDS
						&& dep.getKind() != ModDependency.Kind.SUGGESTS) continue;
				String target = providerOf.get(dep.getModId());
				if (target != null && !target.equals(c.id())) d.add(target);
			}
			indegree.put(c.id(), d.size());
			for (String t : d) dependents.computeIfAbsent(t, k -> new ArrayList<>()).add(c.id());
		}
		PriorityQueue<String> ready = new PriorityQueue<>(Comparator.naturalOrder());
		indegree.forEach((id, n) -> {
			if (n == 0) ready.add(id);
		});
		List<Candidate> ordered = new ArrayList<>();
		while (!ready.isEmpty()) {
			String id = ready.poll();
			ordered.add(active.get(id));
			for (String dependent : dependents.getOrDefault(id, List.of())) {
				int n = indegree.merge(dependent, -1, Integer::sum);
				if (n == 0) ready.add(dependent);
			}
		}
		if (ordered.size() < active.size()) {
			Set<String> remaining = new TreeSet<>(active.keySet());
			for (Candidate c : ordered) remaining.remove(c.id());
			for (String id : remaining) ordered.add(active.get(id));
		}
		return ordered;
	}
}
