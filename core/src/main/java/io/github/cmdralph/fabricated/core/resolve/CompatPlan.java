package io.github.cmdralph.fabricated.core.resolve;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import io.github.cmdralph.fabricated.core.json.Json;
import io.github.cmdralph.fabricated.core.json.JsonException;
import io.github.cmdralph.fabricated.core.metadata.FabricModJsonParser;

/**
 * What Fabricated knows about other people's Fabric mods (data file {@code compat-plan.json}, shipped with core):
 * mods whose Fabric build can't work on NeoForge, and Fabric builds that can't run next to a NeoForge mod. A fact
 * about one mod lives here, never as a branch on its id in code.
 *
 * <pre>{@code
 * {
 *   "schema": 1,
 *   "mods": {
 *     "iris": {
 *       "neoForgeBuildOnly": "why its Fabric build can't run on NeoForge",
 *       "notWithNeoForge": { "sodium": "why it can't run next to Sodium for NeoForge" }
 *     }
 *   }
 * }
 * }</pre>
 */
public record CompatPlan(Map<String, Entry> mods) {
	/**
	 * What is known about one mod's Fabric build.
	 *
	 * @param neoForgeBuildOnly why it can't run on NeoForge, or {@code null}; it loads only on request
	 *                          ({@code loadUnsupported})
	 * @param notWithNeoForge   NeoForge mods (by id) next to which it can't run, with why; refused even on request, as
	 *                          the pair is broken whatever the player accepts
	 */
	public record Entry(String neoForgeBuildOnly, Map<String, String> notWithNeoForge) {
		static final Entry NONE = new Entry(null, Map.of());

		public Entry {
			notWithNeoForge = Map.copyOf(notWithNeoForge);
		}
	}

	public static final int SCHEMA = 1;

	public CompatPlan {
		mods = Map.copyOf(mods);
	}

	/** The plan shipped with this build. */
	public static CompatPlan builtin() {
		try (InputStream in = CompatPlan.class.getResourceAsStream("compat-plan.json")) {
			if (in == null) throw new IllegalStateException("This Fabricated build is missing its compat-plan.json");
			return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
		} catch (IOException | JsonException e) {
			throw new IllegalStateException("This Fabricated build's compat-plan.json is broken: " + e.getMessage(), e);
		}
	}

	public static CompatPlan parse(String json) throws JsonException {
		Map<String, Object> root = Json.parseObject(json);
		if (!(root.get("schema") instanceof Number schema) || schema.intValue() != SCHEMA) throw new JsonException("schema must be " + SCHEMA);
		if (!(root.get("mods") instanceof Map<?, ?> mods)) throw new JsonException("mods missing");
		Map<String, Entry> entries = new LinkedHashMap<>();
		for (Map.Entry<?, ?> e : mods.entrySet()) {
			String id = id(e.getKey());
			if (!(e.getValue() instanceof Map<?, ?> m)) throw new JsonException("The entry of " + id + " must be an object");
			String neoForgeBuildOnly = null;
			Map<String, String> notWithNeoForge = new LinkedHashMap<>();
			for (Map.Entry<?, ?> field : m.entrySet()) {
				switch (String.valueOf(field.getKey())) {
					case "neoForgeBuildOnly" -> neoForgeBuildOnly = reason(field.getValue(), id);
					case "notWithNeoForge" -> {
						if (!(field.getValue() instanceof Map<?, ?> pairs)) throw new JsonException("notWithNeoForge of " + id + " must be an object");
						for (Map.Entry<?, ?> pair : pairs.entrySet()) notWithNeoForge.put(id(pair.getKey()), reason(pair.getValue(), id));
					}
					default -> throw new JsonException("Unknown key " + field.getKey() + " in the entry of " + id);
				}
			}
			entries.put(id, new Entry(neoForgeBuildOnly, notWithNeoForge));
		}
		return new CompatPlan(entries);
	}

	/** What is known about a mod (nothing, for most). */
	public Entry entry(String id) {
		return mods.getOrDefault(id, Entry.NONE);
	}

	private static String id(Object key) throws JsonException {
		String id = String.valueOf(key);
		if (!FabricModJsonParser.FABRIC_ID.matcher(id).matches()) throw new JsonException("\"" + id + "\" is not a mod id");
		return id;
	}

	private static String reason(Object value, String id) throws JsonException {
		if (!(value instanceof String reason) || reason.isBlank()) throw new JsonException("A reason of " + id + " must be a non-empty string");
		return reason;
	}
}
