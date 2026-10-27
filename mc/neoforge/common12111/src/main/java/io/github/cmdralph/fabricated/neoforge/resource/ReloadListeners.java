package io.github.cmdralph.fabricated.neoforge.resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import com.google.common.graph.Graph;
import com.google.common.graph.Graphs;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.fabric.api.resource.v1.reloader.ResourceReloaderKeys;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.neoforge.event.SortedReloadListenerEvent;

/**
 * Adds Fabric reload listeners and their orderings to one of NeoForge's sorted reload-listener events.
 *
 * <p>Fabric and NeoForge sort listeners the same way: by explicit "runs before" edges, with listeners that have
 * no link to vanilla running after vanilla. What differs:
 * <ul>
 *   <li>Fabric's virtual phases {@code fabric:before_vanilla} and {@code fabric:after_vanilla} become empty
 *       listeners placed before the first and after the last vanilla listener.</li>
 *   <li>Fabric names vanilla listeners differently ({@code minecraft:recipes}); NeoForge names them after their
 *       class ({@code minecraft:recipe_manager}). The alias tables translate.</li>
 *   <li>An ordering naming a listener that isn't present, or one that would create a cycle, is skipped with a
 *       warning instead of failing the reload (NeoForge rejects cycles by stopping the game).</li>
 * </ul>
 */
final class ReloadListeners {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

	private ReloadListeners() {
	}

	/**
	 * @param setup     listener to run before every other one (prepares Fabric's shared reload state), or null
	 * @param listeners the Fabric listeners for this reload, by id
	 * @param aliases   Fabric vanilla listener id to NeoForge listener id
	 * @param problems  receives each distinct problem once (for the report)
	 */
	static void apply(SortedReloadListenerEvent event, @Nullable PreparableReloadListener setup, Map<Identifier, PreparableReloadListener> listeners,
			List<ResourceRegistry.Ordering> orderings, Map<Identifier, Identifier> aliases, Consumer<String> problems) {
		Identifier firstVanilla = null;
		for (Identifier key : event.getRegistry().keySet()) {
			if ("minecraft".equals(key.getNamespace())) {
				firstVanilla = key;
				break;
			}
		}
		Identifier lastVanilla = event.getNameLookup().apply(event.getLastVanillaListener());
		if (firstVanilla != null) {
			addPhase(event, ResourceReloaderKeys.BEFORE_VANILLA, null, firstVanilla);
			addPhase(event, ResourceReloaderKeys.AFTER_VANILLA, lastVanilla, null);
		}

		listeners.forEach((id, listener) -> {
			try {
				event.addListener(id, listener);
			} catch (IllegalArgumentException e) {
				report(problems, "Fabric reload listener " + id + " was not added: a listener with this id or instance is already registered");
			}
		});

		Graph<PreparableReloadListener> graph = event.getGraph();
		Map<Identifier, PreparableReloadListener> registry = event.getRegistry();
		for (ResourceRegistry.Ordering ordering : orderings) {
			Identifier first = resolve(registry, aliases, ordering.first());
			Identifier second = resolve(registry, aliases, ordering.second());
			if (first == null || second == null) {
				Identifier missing = first == null ? ordering.first() : ordering.second();
				// Orderings against optional listeners of other mods are normal; only note them in the log.
				if (REPORTED.add("missing:" + missing)) {
					LOGGER.debug("Skipped Fabric reload ordering {} -> {}: no listener {} is registered", ordering.first(), ordering.second(), missing);
				}
				continue;
			}
			if (Graphs.reachableNodes(graph, registry.get(second)).contains(registry.get(first))) {
				report(problems, "Skipped Fabric reload ordering " + ordering.first() + " -> " + ordering.second() + ": it would create a cycle");
				continue;
			}
			event.addDependency(first, second);
		}

		if (setup != null) {
			// Runs before every listener that has nothing before it, so it is the single starting point.
			List<Identifier> roots = new ArrayList<>();
			registry.forEach((id, listener) -> {
				if (graph.predecessors(listener).isEmpty()) roots.add(id);
			});
			Identifier setupId = Identifier.fromNamespaceAndPath("fabricated", "fabric_reload_setup");
			event.addListener(setupId, setup);
			for (Identifier root : roots) event.addDependency(setupId, root);
		}
	}

	private static @Nullable Identifier resolve(Map<Identifier, PreparableReloadListener> registry, Map<Identifier, Identifier> aliases, Identifier id) {
		if (registry.containsKey(id)) return id;
		Identifier alias = aliases.get(id);
		return alias != null && registry.containsKey(alias) ? alias : null;
	}

	private static void addPhase(SortedReloadListenerEvent event, Identifier phase, @Nullable Identifier after, @Nullable Identifier before) {
		if (event.getRegistry().containsKey(phase)) return;
		event.addListener(phase, new Phase(phase));
		if (after != null) event.addDependency(after, phase);
		if (before != null) event.addDependency(phase, before);
	}

	private static void report(Consumer<String> problems, String problem) {
		if (REPORTED.add(problem)) {
			LOGGER.warn(problem);
			problems.accept(problem);
		}
	}

	/** Fabric's virtual ordering phases: does nothing, only anchors orderings. */
	private record Phase(Identifier id) implements ResourceManagerReloadListener {
		@Override
		public void onResourceManagerReload(ResourceManager manager) {
		}
	}

	static Identifier vanilla(String path) {
		return Identifier.withDefaultNamespace(path);
	}
}
