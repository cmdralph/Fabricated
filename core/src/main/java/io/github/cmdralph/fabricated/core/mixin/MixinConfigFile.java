package io.github.cmdralph.fabricated.core.mixin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.cmdralph.fabricated.core.json.Json;
import io.github.cmdralph.fabricated.core.json.JsonException;

/**
 * A parsed mixin configuration JSON. The original tree is retained so a filtered copy can be produced
 * without losing options this class does not model.
 *
 * @param mixinPackage                 package of the mixin classes, dotted (may be {@code null} for an empty config)
 * @param parent                       the config ({@code parent}) whose {@code required} and injector defaults this
 *                                     one inherits where it sets none, or {@code null}
 * @param overwritesRequireAnnotations {@code overwrites.requireAnnotations}: a mixin method that replaces a target
 *                                     method must be @Overwrite
 */
public record MixinConfigFile(
		Map<String, Object> tree,
		String mixinPackage,
		List<String> common,
		List<String> client,
		List<String> server,
		String refmap,
		boolean required,
		String plugin,
		int defaultRequire,
		String parent,
		boolean overwritesRequireAnnotations) {

	public static MixinConfigFile parse(String json) throws JsonException {
		Map<String, Object> tree = Json.parseObject(json);
		int defaultRequire = tree.get("injectors") instanceof Map<?, ?> injectors && injectors.get("defaultRequire") instanceof Number n
				? n.intValue() : 0;
		return new MixinConfigFile(tree,
				optString(tree, "package"),
				strings(tree.get("mixins"), "mixins"),
				strings(tree.get("client"), "client"),
				strings(tree.get("server"), "server"),
				optString(tree, "refmap"),
				Boolean.TRUE.equals(tree.get("required")),
				optString(tree, "plugin"),
				defaultRequire,
				optString(tree, "parent"),
				tree.get("overwrites") instanceof Map<?, ?> overwrites && Boolean.TRUE.equals(overwrites.get("requireAnnotations")));
	}

	/** This config with what Mixin takes from its parent: {@code required} when unset, a default require of 0 replaced. */
	public MixinConfigFile inheriting(MixinConfigFile parentConfig) {
		return new MixinConfigFile(tree, mixinPackage, common, client, server, refmap,
				tree.containsKey("required") ? required : parentConfig.required(), plugin,
				defaultRequire != 0 ? defaultRequire : parentConfig.defaultRequire(), parent, overwritesRequireAnnotations);
	}

	/** This config listing only {@code mixins} (names relative to the package), on both sides. */
	public MixinConfigFile listing(List<String> mixins) {
		return new MixinConfigFile(tree, mixinPackage, List.copyOf(mixins), List.of(), List.of(), refmap, required, plugin, defaultRequire,
				parent, overwritesRequireAnnotations);
	}

	/** This config asking for MixinExtras {@code version} ({@code mixinextras.minVersion}), whatever it or its parent asked for. */
	public MixinConfigFile requiringMixinExtras(String version) {
		Map<String, Object> copy = new LinkedHashMap<>(tree);
		Map<String, Object> settings = new LinkedHashMap<>();
		if (copy.get("mixinextras") instanceof Map<?, ?> map) map.forEach((key, value) -> settings.put(String.valueOf(key), value));
		settings.put("minVersion", version);
		copy.put("mixinextras", settings);
		return new MixinConfigFile(copy, mixinPackage, common, client, server, refmap, required, plugin, defaultRequire, parent,
				overwritesRequireAnnotations);
	}

	private static String optString(Map<String, Object> tree, String key) throws JsonException {
		Object v = tree.get(key);
		if (v == null) return null;
		if (!(v instanceof String s)) throw new JsonException("\"" + key + "\" must be a string");
		return s;
	}

	private static List<String> strings(Object value, String key) throws JsonException {
		List<String> ret = new ArrayList<>();
		if (value == null) return List.of();
		if (!(value instanceof List<?> list)) throw new JsonException("\"" + key + "\" must be an array");
		for (Object o : list) {
			if (!(o instanceof String s)) throw new JsonException("\"" + key + "\" must contain strings");
			ret.add(s);
		}
		return List.copyOf(ret);
	}

	/** Mixin names (relative to the package) that apply on the given side. */
	public List<String> mixinsFor(boolean clientSide) {
		List<String> ret = new ArrayList<>(common);
		ret.addAll(clientSide ? client : server);
		return ret;
	}

	/** Internal class name of a mixin entry. */
	public String internalName(String mixin) {
		String pkg = mixinPackage == null || mixinPackage.isEmpty() ? "" : mixinPackage + ".";
		return (pkg + mixin).replace('.', '/');
	}

	/** Every mixin name declared anywhere in the config. */
	public List<String> allMixins() {
		List<String> ret = new ArrayList<>(common);
		ret.addAll(client);
		ret.addAll(server);
		return ret;
	}

	/**
	 * Returns a copy of this config with the given mixins (names relative to the package) removed from
	 * every list.
	 */
	public String withoutMixins(Set<String> skip) {
		Map<String, Object> copy = new LinkedHashMap<>(tree);
		for (String key : List.of("mixins", "client", "server")) {
			if (copy.get(key) instanceof List<?> list) {
				List<Object> filtered = new ArrayList<>();
				for (Object o : list) {
					if (!(o instanceof String s) || !skip.contains(s)) filtered.add(o);
				}
				copy.put(key, filtered);
			}
		}
		return Json.write(copy, true);
	}
}
