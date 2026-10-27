package io.github.cmdralph.fabricated.loader;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.cmdralph.fabricated.core.accesswidener.AccessWidenerFile.InjectedInterface;

/**
 * Whether each interface Fabric mods inject into game classes is a class of the game's module layers, checked once they
 * exist (the first class Fabricated transforms). An interface that no module has, or whose package two modules have
 * (the package's classes then load from one of them only), fails the game class it is injected into when that class
 * loads, as Forge's corpus runs did (ForgeTestMarker, FabricBlockGetter, FabricRegistry: LAYER-1). Each such interface is
 * logged with the modules that have its package and whether each has the class, which the crash doesn't say.
 */
final class GameLayerCheck {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");

	private GameLayerCheck() {
	}

	/** @param injected game classes' internal names to the interfaces Fabric mods inject into them */
	static void run(ModuleLayer game, Map<String, List<InjectedInterface>> injected) {
		Map<String, List<Module>> owners = new HashMap<>();
		for (ModuleLayer layer : withParents(game)) {
			for (Module module : layer.modules()) {
				for (String pkg : module.getPackages()) owners.computeIfAbsent(pkg, k -> new ArrayList<>()).add(module);
			}
		}
		Map<String, Set<String>> targets = new HashMap<>();
		injected.forEach((target, list) -> list.forEach(i -> targets.computeIfAbsent(i.interfaceName(), k -> new TreeSet<>()).add(target)));
		int problems = 0;
		for (Map.Entry<String, Set<String>> e : new TreeMap<>(targets).entrySet()) {
			String itf = e.getKey();
			int slash = itf.lastIndexOf('/');
			List<Module> modules = owners.getOrDefault(slash < 0 ? "" : itf.substring(0, slash).replace('/', '.'), List.of());
			List<String> described = new ArrayList<>();
			int withClass = 0;
			for (Module module : modules) {
				boolean has = hasClass(module, itf);
				if (has) withClass++;
				described.add(module.getName() + (has ? "" : " (without it)"));
			}
			if (modules.size() == 1 && withClass == 1) continue;
			problems++;
			LOGGER.warn("Fabricated injects {} into {}, but {}: loading those classes will fail", itf.replace('/', '.'),
					String.join(", ", e.getValue()).replace('/', '.'),
					modules.isEmpty() ? "no module of the game's layers has its package" : "the modules with its package are " + String.join(", ", described));
		}
		LOGGER.debug("Checked the {} interface(s) Fabric mods inject against the game's layers: {} problem(s)", targets.size(), problems);
	}

	private static boolean hasClass(Module module, String internalName) {
		try (InputStream in = module.getResourceAsStream(internalName + ".class")) {
			return in != null;
		} catch (IOException | RuntimeException e) {
			return false;
		}
	}

	private static Set<ModuleLayer> withParents(ModuleLayer layer) {
		Set<ModuleLayer> layers = new LinkedHashSet<>();
		Deque<ModuleLayer> pending = new ArrayDeque<>(List.of(layer));
		while (!pending.isEmpty()) {
			ModuleLayer next = pending.pop();
			if (layers.add(next)) pending.addAll(next.parents());
		}
		return layers;
	}
}
