package io.github.cmdralph.fabricated.core.mixin;

import java.util.HashMap;
import java.util.Map;

import io.github.cmdralph.fabricated.core.json.Json;
import io.github.cmdralph.fabricated.core.json.JsonException;

/**
 * Mixin reference map: per mixin class, a map from the selector written in the source to the
 * resolved reference. Mixin uses the {@code mappings} section by default, which is what Fabric
 * production mods rely on, so that is the section consulted here.
 */
public final class Refmap {
	public static final Refmap EMPTY = new Refmap(Map.of());

	private final Map<String, Map<String, String>> mappings;

	private Refmap(Map<String, Map<String, String>> mappings) {
		this.mappings = mappings;
	}

	public static Refmap parse(String json) throws JsonException {
		Map<String, Object> root = Json.parseObject(json);
		Map<String, Map<String, String>> mappings = new HashMap<>();
		if (root.get("mappings") instanceof Map<?, ?> m) {
			for (Map.Entry<?, ?> cls : m.entrySet()) {
				if (!(cls.getValue() instanceof Map<?, ?> entries)) continue;
				Map<String, String> converted = new HashMap<>();
				for (Map.Entry<?, ?> e : entries.entrySet()) {
					if (e.getValue() instanceof String s) converted.put(String.valueOf(e.getKey()), s);
				}
				mappings.put(String.valueOf(cls.getKey()).replace('.', '/'), converted);
			}
		}
		return new Refmap(mappings);
	}

	/** Resolves {@code selector} for {@code mixinClass} (internal name), or returns it unchanged. */
	public String resolve(String mixinClass, String selector) {
		Map<String, String> entries = mappings.get(mixinClass);
		if (entries == null) return selector;
		return entries.getOrDefault(selector, selector);
	}
}
