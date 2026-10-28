package io.github.cmdralph.fabricated.core.resolve;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.cmdralph.fabricated.core.json.Json;
import io.github.cmdralph.fabricated.core.json.JsonException;
import io.github.cmdralph.fabricated.core.mixin.MixinConfigFile;

/**
 * Which parts of Fabricated's in-game mod belong to which Fabric API module, so that a module another mod provides can
 * stand down (the provider policy: an installed provider of a module wins, as only one copy of its packages can load).
 * Two data files say it: the version's {@code native-modules.json} (each native module's packages, written by
 * {@code scripts/native-module-index.py} from the official Fabric API release of the version's plan) and the in-game
 * mod's {@code mixin-modules.json} (the module each of Fabricated's own mixins, and each mixin config in Fabricated's
 * packages, serves; an empty list for those serving none, which always apply). A mixin config in a Fabric API package
 * belongs to the module owning that package.
 */
public final class NativeModules {
	/** The version's index of native modules' packages, in Fabricated's own resources. */
	public static final String INDEX = "native-modules.json";
	/** The in-game mod's map of its mixins to modules, at the root of its jar. */
	public static final String MIXIN_MODULES = "META-INF/fabricated/mixin-modules.json";
	public static final NativeModules NONE = new NativeModules(Map.of(), Map.of(), Map.of());

	private final Map<String, Set<String>> packagesByModule;
	private final Map<String, String> moduleByPackage = new HashMap<>();
	/** Mixin config → the modules it serves (any one provided elsewhere drops it). */
	private final Map<String, List<String>> configModules;
	/** Mixin (as the config names it) → the modules it serves. */
	private final Map<String, List<String>> mixinModules;

	private NativeModules(Map<String, Set<String>> packagesByModule, Map<String, List<String>> configModules, Map<String, List<String>> mixinModules) {
		this.packagesByModule = packagesByModule;
		this.configModules = configModules;
		this.mixinModules = mixinModules;
		// scripts/native-module-index.py gives each package one module; should a file list one twice, the first wins.
		packagesByModule.forEach((module, packages) -> packages.forEach(p -> moduleByPackage.putIfAbsent(p, module)));
	}

	/**
	 * @param index        the version's {@code native-modules.json}
	 * @param mixinModules the in-game mod's {@code mixin-modules.json}, or null when it has none
	 */
	public static NativeModules parse(String index, String mixinModules) throws JsonException {
		Map<String, Set<String>> packages = new LinkedHashMap<>();
		if (Json.parseObject(index).get("modules") instanceof Map<?, ?> modules) {
			modules.forEach((module, list) -> packages.put(String.valueOf(module), Set.copyOf(strings(list))));
		}
		Map<String, List<String>> configs = new LinkedHashMap<>();
		Map<String, List<String>> mixins = new LinkedHashMap<>();
		if (mixinModules != null) {
			Map<String, Object> tree = Json.parseObject(mixinModules);
			if (tree.get("configs") instanceof Map<?, ?> map) map.forEach((k, v) -> configs.put(String.valueOf(k), strings(v)));
			if (tree.get("mixins") instanceof Map<?, ?> map) map.forEach((k, v) -> mixins.put(String.valueOf(k), strings(v)));
		}
		// In the file's order (Map.copyOf's isn't stable), for the first-wins rule above.
		return new NativeModules(Collections.unmodifiableMap(packages), Map.copyOf(configs), Map.copyOf(mixins));
	}

	/** The native module a package (internal form) belongs to, or null. */
	public String owner(String internalPackage) {
		return moduleByPackage.get(internalPackage);
	}

	/** The packages (internal form) of the given modules. */
	public Set<String> packages(Collection<String> modules) {
		Set<String> packages = new LinkedHashSet<>();
		for (String module : modules) packages.addAll(packagesByModule.getOrDefault(module, Set.of()));
		return packages;
	}

	/**
	 * What of the in-game mod stands down for the modules {@code provided} elsewhere.
	 *
	 * @param configs the in-game mod's mixin configs, by file name
	 */
	public StandDown standDown(Set<String> provided, Map<String, MixinConfigFile> configs) {
		Set<String> packages = packages(provided);
		Set<String> droppedConfigs = new LinkedHashSet<>();
		Map<String, Set<String>> droppedMixins = new LinkedHashMap<>();
		Set<String> unmapped = new LinkedHashSet<>();
		for (Map.Entry<String, MixinConfigFile> config : configs.entrySet()) {
			String name = config.getKey();
			String mixinPackage = config.getValue().mixinPackage() == null ? "" : config.getValue().mixinPackage().replace('.', '/');
			List<String> modules = configModules.containsKey(name) ? configModules.get(name)
					: owner(mixinPackage) != null ? List.of(owner(mixinPackage)) : null;
			if (modules != null) {
				if (modules.stream().anyMatch(provided::contains)) droppedConfigs.add(name);
				continue;
			}
			// A config of Fabricated's own package: each of its mixins says what it serves.
			Set<String> dropped = new LinkedHashSet<>();
			for (String mixin : config.getValue().allMixins()) {
				List<String> serves = mixinModules.get(mixin);
				if (serves == null) unmapped.add(name + ":" + mixin);
				else if (serves.stream().anyMatch(provided::contains)) dropped.add(mixin);
			}
			if (!dropped.isEmpty()) droppedMixins.put(name, dropped);
		}
		return new StandDown(packages, droppedConfigs, droppedMixins, unmapped);
	}

	/**
	 * @param packages     the packages (internal form) left out of the in-game mod's module
	 * @param mixinConfigs the mixin configs no longer declared
	 * @param mixins       the mixins left out of the configs still declared, by config
	 * @param unmapped     mixins no data file maps to a module ({@code config:mixin}); the build refuses them, so this
	 *                     is empty but for a broken build, and they stay
	 */
	public record StandDown(Set<String> packages, Set<String> mixinConfigs, Map<String, Set<String>> mixins, Set<String> unmapped) {
		public boolean isEmpty() {
			return packages.isEmpty() && mixinConfigs.isEmpty() && mixins.isEmpty();
		}
	}

	/**
	 * The in-game mod's mods.toml without the {@code [[mixins]]} entries of {@code configs}. The file is Fabricated's own,
	 * written by its build, so an entry is its header and the key lines up to the next blank line or table.
	 */
	public static String withoutMixinConfigs(String modsToml, Set<String> configs) {
		if (configs.isEmpty()) return modsToml;
		StringBuilder out = new StringBuilder();
		String[] lines = modsToml.split("\n", -1);
		for (int i = 0; i < lines.length; i++) {
			if (lines[i].trim().equals("[[mixins]]")) {
				int end = i + 1;
				while (end < lines.length && !lines[end].isBlank() && !lines[end].trim().startsWith("[")) end++;
				String config = null;
				for (int j = i + 1; j < end; j++) {
					String line = lines[j].trim();
					if (line.startsWith("config")) config = line.substring(line.indexOf('=') + 1).trim().replace("\"", "").replace("'", "");
				}
				if (config != null && configs.contains(config)) {
					i = end - 1;
					continue;
				}
			}
			out.append(lines[i]);
			if (i < lines.length - 1) out.append('\n');
		}
		return out.toString();
	}

	private static List<String> strings(Object value) {
		return Json.asList(value).stream().filter(String.class::isInstance).map(String.class::cast).toList();
	}
}
