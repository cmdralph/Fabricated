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
 */
public final class MixinConfigFile {
	private final Map<String, Object> tree;
	private final String mixinPackage;
	private final List<String> common;
	private final List<String> client;
	private final List<String> server;
	private final String refmap;
	private final boolean required;
	private final String plugin;
	private final int defaultRequire;

	private MixinConfigFile(Map<String, Object> tree) throws JsonException {
		this.tree = tree;
		this.mixinPackage = optString(tree, "package");
		this.common = strings(tree.get("mixins"), "mixins");
		this.client = strings(tree.get("client"), "client");
		this.server = strings(tree.get("server"), "server");
		this.refmap = optString(tree, "refmap");
		this.required = Boolean.TRUE.equals(tree.get("required"));
		this.plugin = optString(tree, "plugin");
		int dr = 0;
		if (tree.get("injectors") instanceof Map<?, ?> injectors && injectors.get("defaultRequire") instanceof Number n) {
			dr = n.intValue();
		}
		this.defaultRequire = dr;
	}

	public static MixinConfigFile parse(String json) throws JsonException {
		return new MixinConfigFile(Json.parseObject(json));
	}

	private static String optString(Map<String, Object> tree, String key) throws JsonException {
		Object v = tree.get(key);
		if (v == null) return null;
		if (!(v instanceof String s)) throw new JsonException("\"" + key + "\" must be a string");
		return s;
	}

	private static List<String> strings(Object value, String key) throws JsonException {
		List<String> ret = new ArrayList<>();
		if (value == null) return ret;
		if (!(value instanceof List<?> list)) throw new JsonException("\"" + key + "\" must be an array");
		for (Object o : list) {
			if (!(o instanceof String s)) throw new JsonException("\"" + key + "\" must contain strings");
			ret.add(s);
		}
		return ret;
	}

	/** Package of the mixin classes, dotted (may be {@code null} for an empty config). */
	public String mixinPackage() {
		return mixinPackage;
	}

	public String refmap() {
		return refmap;
	}

	public boolean required() {
		return required;
	}

	public String plugin() {
		return plugin;
	}

	public int defaultRequire() {
		return defaultRequire;
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
