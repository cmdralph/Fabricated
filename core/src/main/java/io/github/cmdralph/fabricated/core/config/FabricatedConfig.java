package io.github.cmdralph.fabricated.core.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import io.github.cmdralph.fabricated.core.archive.ArchiveLimits;
import io.github.cmdralph.fabricated.core.archive.AtomicFiles;
import io.github.cmdralph.fabricated.core.json.Json;
import io.github.cmdralph.fabricated.core.json.JsonException;

/**
 * User configuration from {@code config/fabricated.json}.
 *
 * <p>Read during NeoForge mod discovery, before NeoForge's own config system exists. Invalid values
 * never stop the game: they fall back to defaults and are reported as warnings.
 */
public record FabricatedConfig(
		EntrypointFailure onEntrypointFailure,
		boolean strictDependencies,
		boolean strictLinkage,
		boolean preferNativeNeoForgeMods,
		boolean cacheEnabled,
		boolean verifyIntegrity,
		int maxUnusedLaunches,
		ArchiveLimits limits,
		Map<String, ModOverride> overrides,
		String mojangMappingsPath,
		List<String> warnings) {
	public static final int SCHEMA = 1;
	public static final String FILE_NAME = "fabricated.json";

	/** What to do when a Fabric entrypoint throws. */
	public enum EntrypointFailure {
		/** Stop loading and show NeoForge's error screen naming the mod. */
		ERROR,
		/** Mark the mod as failed, skip its remaining entrypoints and keep loading. */
		DISABLE
	}

	/** Per-mod overrides, keyed by Fabric mod id. */
	public record ModOverride(boolean disabled, Set<String> skipMixins, Set<String> skipEntrypoints, List<String> aliases,
			boolean preferFabric, boolean ignorePreflight) {
		public static final ModOverride NONE = new ModOverride(false, Set.of(), Set.of(), List.of(), false, false);

		/** Stable text describing the overrides that affect translation output, for cache keys. */
		public String fingerprint() {
			return "skipMixins=" + new TreeSet<>(skipMixins) + ";ignorePreflight=" + ignorePreflight;
		}
	}

	public FabricatedConfig {
		overrides = Collections.unmodifiableMap(new TreeMap<>(overrides));
		warnings = List.copyOf(warnings);
	}

	public static FabricatedConfig defaults() {
		return new FabricatedConfig(EntrypointFailure.ERROR, false, false, true, true, true, 5,
				ArchiveLimits.DEFAULTS, Map.of(), null, List.of());
	}

	/**
	 * Loads the config from {@code configDir}, creating it with defaults when absent. Never throws for
	 * bad content; problems are available from {@link #warnings()}.
	 */
	public static FabricatedConfig load(Path configDir) {
		Path file = configDir.resolve(FILE_NAME);
		if (!Files.exists(file)) {
			FabricatedConfig defaults = defaults();
			try {
				Path parent = file.toAbsolutePath().getParent();
				Files.createDirectories(parent);
				AtomicFiles.writeAtomically(file, defaults.toJson().getBytes(StandardCharsets.UTF_8), parent);
			} catch (IOException e) {
				return defaults.withWarning("Could not create " + file + ": " + e.getMessage());
			}
			return defaults;
		}
		try {
			String text = Files.readString(file, StandardCharsets.UTF_8);
			return parse(text);
		} catch (IOException e) {
			return defaults().withWarning("Could not read " + file + ": " + e.getMessage() + "; using defaults");
		}
	}

	public static FabricatedConfig parse(String text) {
		List<String> warnings = new ArrayList<>();
		Map<String, Object> root;
		try {
			root = Json.parseObject(text);
		} catch (JsonException e) {
			return defaults().withWarning(FILE_NAME + " is not valid JSON (" + e.getMessage() + "); using defaults");
		}
		Reader r = new Reader(root, "", warnings);
		int schema = r.integer("schema", SCHEMA, 1, 1);
		if (schema != SCHEMA) warnings.add("Unknown schema " + schema);

		// Accepted (and ignored) so configs written by older versions don't produce a warning.
		r.get("logLevel");
		EntrypointFailure failure = EntrypointFailure.valueOf(r.choice("onEntrypointFailure", "error", List.of("error", "disable")).toUpperCase(Locale.ROOT));
		boolean strictDeps = r.bool("strictDependencies", false);
		boolean strictLinkage = r.bool("strictLinkage", false);
		boolean preferNative = r.bool("preferNativeNeoForgeMods", true);
		String mappings = r.string("mojangMappingsPath", null);

		Reader cache = r.object("cache");
		boolean cacheEnabled = cache.bool("enabled", true);
		boolean verify = cache.bool("verifyIntegrity", true);
		int unused = cache.integer("maxUnusedLaunches", 5, 1, 1000);

		Reader lim = r.object("limits");
		ArchiveLimits d = ArchiveLimits.DEFAULTS;
		ArchiveLimits limits = new ArchiveLimits(
				lim.longValue("maxJarBytes", d.maxJarBytes(), 1 << 20, 4L << 30),
				lim.longValue("maxEntryBytes", d.maxEntryBytes(), 1 << 16, 2L << 30),
				lim.longValue("maxTotalUncompressedBytes", d.maxTotalUncompressedBytes(), 1 << 20, 16L << 30),
				lim.integer("maxEntries", d.maxEntries(), 16, 1_000_000),
				lim.integer("maxCompressionRatio", d.maxCompressionRatio(), 2, 10_000),
				lim.integer("maxNestingDepth", d.maxNestingDepth(), 0, 8));

		Map<String, ModOverride> mods = new TreeMap<>();
		Reader modsReader = r.object("mods");
		for (String id : modsReader.keys()) {
			Reader m = modsReader.object(id);
			mods.put(id, new ModOverride(
					m.bool("disabled", false),
					Collections.unmodifiableSet(new TreeSet<>(m.strings("skipMixins"))),
					Collections.unmodifiableSet(new TreeSet<>(m.strings("skipEntrypoints"))),
					List.copyOf(m.strings("aliases")),
					m.bool("preferFabric", false),
					m.bool("ignorePreflight", false)));
			m.reportUnknown();
		}
		cache.reportUnknown();
		lim.reportUnknown();
		r.reportUnknown();
		return new FabricatedConfig(failure, strictDeps, strictLinkage, preferNative, cacheEnabled, verify,
				unused, limits, mods, mappings, warnings);
	}

	private FabricatedConfig withWarning(String warning) {
		List<String> w = new ArrayList<>(warnings);
		w.add(warning);
		return new FabricatedConfig(onEntrypointFailure, strictDependencies, strictLinkage, preferNativeNeoForgeMods,
				cacheEnabled, verifyIntegrity, maxUnusedLaunches, limits, overrides, mojangMappingsPath, w);
	}

	/** Serialises this config (used to create the default file). */
	public String toJson() {
		Map<String, Object> root = new LinkedHashMap<>();
		root.put("schema", SCHEMA);
		root.put("onEntrypointFailure", onEntrypointFailure.name().toLowerCase(Locale.ROOT));
		root.put("strictDependencies", strictDependencies);
		root.put("strictLinkage", strictLinkage);
		root.put("preferNativeNeoForgeMods", preferNativeNeoForgeMods);
		Map<String, Object> cache = new LinkedHashMap<>();
		cache.put("enabled", cacheEnabled);
		cache.put("verifyIntegrity", verifyIntegrity);
		cache.put("maxUnusedLaunches", maxUnusedLaunches);
		root.put("cache", cache);
		Map<String, Object> lim = new LinkedHashMap<>();
		lim.put("maxJarBytes", limits.maxJarBytes());
		lim.put("maxEntryBytes", limits.maxEntryBytes());
		lim.put("maxTotalUncompressedBytes", limits.maxTotalUncompressedBytes());
		lim.put("maxEntries", limits.maxEntries());
		lim.put("maxCompressionRatio", limits.maxCompressionRatio());
		lim.put("maxNestingDepth", limits.maxNestingDepth());
		root.put("limits", lim);
		Map<String, Object> modsJson = new LinkedHashMap<>();
		for (Map.Entry<String, ModOverride> e : overrides.entrySet()) {
			ModOverride o = e.getValue();
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("disabled", o.disabled());
			m.put("skipMixins", new ArrayList<>(o.skipMixins()));
			m.put("skipEntrypoints", new ArrayList<>(o.skipEntrypoints()));
			m.put("aliases", o.aliases());
			m.put("preferFabric", o.preferFabric());
			m.put("ignorePreflight", o.ignorePreflight());
			modsJson.put(e.getKey(), m);
		}
		root.put("mods", modsJson);
		root.put("mojangMappingsPath", mojangMappingsPath);
		return Json.write(root, true);
	}

	public ModOverride override(String fabricId) {
		return overrides.getOrDefault(fabricId, ModOverride.NONE);
	}

	/** Typed, validating view of one JSON object. */
	private static final class Reader {
		private final Map<String, Object> obj;
		private final String path;
		private final List<String> warnings;
		private final Set<String> used = new TreeSet<>();

		Reader(Map<String, Object> obj, String path, List<String> warnings) {
			this.obj = obj;
			this.path = path;
			this.warnings = warnings;
		}

		private Object get(String key) {
			used.add(key);
			return obj.get(key);
		}

		private String where(String key) {
			return path.isEmpty() ? key : path + "." + key;
		}

		boolean bool(String key, boolean def) {
			Object v = get(key);
			if (v == null) return def;
			if (v instanceof Boolean b) return b;
			warnings.add(where(key) + " must be true or false; using " + def);
			return def;
		}

		int integer(String key, int def, int min, int max) {
			return (int) longValue(key, def, min, max);
		}

		long longValue(String key, long def, long min, long max) {
			Object v = get(key);
			if (v == null) return def;
			if (v instanceof Number n) {
				long l = n.longValue();
				if (l >= min && l <= max) return l;
				warnings.add(where(key) + " must be between " + min + " and " + max + "; using " + def);
				return def;
			}
			warnings.add(where(key) + " must be a number; using " + def);
			return def;
		}

		String string(String key, String def) {
			Object v = get(key);
			if (v == null) return def;
			if (v instanceof String s) return s;
			warnings.add(where(key) + " must be a string; using default");
			return def;
		}

		String choice(String key, String def, List<String> allowed) {
			String v = string(key, def);
			for (String a : allowed) {
				if (a.equalsIgnoreCase(v)) return a;
			}
			warnings.add(where(key) + " must be one of " + allowed + "; using " + def);
			return def;
		}

		List<String> strings(String key) {
			Object v = get(key);
			List<String> ret = new ArrayList<>();
			if (v == null) return ret;
			if (v instanceof List<?> list) {
				for (Object o : list) {
					if (o instanceof String s) ret.add(s);
					else warnings.add(where(key) + " must only contain strings; ignored " + o);
				}
				return ret;
			}
			warnings.add(where(key) + " must be an array of strings; ignored");
			return ret;
		}

		Reader object(String key) {
			Object v = get(key);
			if (v instanceof Map<?, ?> map) {
				@SuppressWarnings("unchecked")
				Map<String, Object> cast = (Map<String, Object>) map;
				return new Reader(cast, where(key), warnings);
			}
			if (v != null) warnings.add(where(key) + " must be an object; ignored");
			return new Reader(Map.of(), where(key), warnings);
		}

		Set<String> keys() {
			Set<String> keys = new TreeSet<>(obj.keySet());
			used.addAll(keys);
			return keys;
		}

		void reportUnknown() {
			for (String key : obj.keySet()) {
				if (!used.contains(key)) warnings.add("Unknown setting " + where(key) + " ignored");
			}
		}
	}
}
