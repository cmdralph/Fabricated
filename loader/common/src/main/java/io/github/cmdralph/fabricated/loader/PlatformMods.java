package io.github.cmdralph.fabricated.loader;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.electronwill.nightconfig.core.UnmodifiableConfig;

import io.github.cmdralph.fabricated.core.Pipeline;

/**
 * What NeoForge's and Forge's mods are to Fabric code, read alike on both ({@code NeoForgeMods}, {@code ForgeMods}): the
 * mods a mods.toml declares, their properties as plain values, and their packages.
 */
final class PlatformMods {
	/** The version a mods.toml gives as its jar's version (the platform reads it from the jar's manifest). */
	private static final String JAR_VERSION = "${file.jarVersion}";

	private PlatformMods() {
	}

	static Set<String> internal(Set<String> dottedPackages) {
		Set<String> packages = new HashSet<>();
		for (String p : dottedPackages) packages.add(p.replace('.', '/'));
		return packages;
	}

	static Set<String> packagesOf(Set<String> classes) {
		Set<String> packages = new HashSet<>();
		for (String name : classes) {
			int slash = name.lastIndexOf('/');
			if (slash > 0) packages.add(name.substring(0, slash));
		}
		return packages;
	}

	/**
	 * The mods a mods.toml declares.
	 *
	 * @param container the jar it is nested in
	 */
	static List<Pipeline.NeoForgeMod> declared(UnmodifiableConfig toml, String jarVersion, Path container) {
		List<Pipeline.NeoForgeMod> mods = new ArrayList<>();
		if (!(toml.get("mods") instanceof List<?> declared)) return mods;
		for (Object entry : declared) {
			if (!(entry instanceof UnmodifiableConfig mod) || !(mod.get("modId") instanceof String id)) continue;
			String version = mod.get("version") instanceof String v ? v : "1";
			String name = mod.get("displayName") instanceof String n ? n : id;
			Object properties = toml.get(List.of("modproperties", id));
			// Its jar is inside the container, which the platform extracts later: to Fabric code, the container is its root.
			mods.add(new Pipeline.NeoForgeMod(id, version.equals(JAR_VERSION) ? jarVersion : version, name,
					withProvides(properties instanceof UnmodifiableConfig config ? plain(config.valueMap()) : Map.of(), strings(mod.get("provides"))),
					List.of(container)));
		}
		return mods;
	}

	static List<String> strings(Object value) {
		return value instanceof List<?> list ? list.stream().filter(String.class::isInstance).map(String.class::cast).toList() : List.of();
	}

	/** The mod's properties with the ids it provides added to those it lists as Fabric's provides, if any. */
	static Map<String, Object> withProvides(Map<String, Object> properties, List<String> provides) {
		if (provides.isEmpty()) return properties;
		Map<String, Object> merged = new LinkedHashMap<>(properties);
		List<Object> ids = new ArrayList<>(merged.get(Pipeline.NeoForgeMod.PROVIDES) instanceof List<?> listed ? listed : List.of());
		for (String id : provides) if (!ids.contains(id)) ids.add(id);
		merged.put(Pipeline.NeoForgeMod.PROVIDES, ids);
		return merged;
	}

	/** Mod properties as plain values: TOML tables become maps; values TOML has and JSON hasn't (dates) become strings. */
	static Map<String, Object> plain(Map<String, Object> properties) {
		Map<String, Object> plain = new LinkedHashMap<>();
		properties.forEach((key, value) -> plain.put(key, plainValue(value)));
		return plain;
	}

	private static Object plainValue(Object value) {
		if (value instanceof UnmodifiableConfig config) return plain(config.valueMap());
		if (value instanceof Map<?, ?> map) {
			Map<String, Object> plain = new LinkedHashMap<>();
			map.forEach((key, v) -> plain.put(String.valueOf(key), plainValue(v)));
			return plain;
		}
		if (value instanceof List<?> list) {
			List<Object> plain = new ArrayList<>(list.size());
			for (Object v : list) plain.add(plainValue(v));
			return plain;
		}
		if (value == null || value instanceof String || value instanceof Boolean || value instanceof Number) return value;
		return String.valueOf(value);
	}
}
