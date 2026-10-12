package io.github.cmdralph.fabricated.core.resolve;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import io.github.cmdralph.fabricated.core.json.Json;
import io.github.cmdralph.fabricated.core.json.JsonException;

/**
 * Per-Minecraft-version decision for every Fabric API module (data file {@code fabric-api-plan.json}).
 *
 * <pre>{@code
 * {
 *   "fabricApiVersion": "0.141.6+1.21.11",
 *   "modules": {
 *     "fabric-api-base": { "plan": "native", "version": "1.0.5" },
 *     "fabric-renderer-indigo": { "plan": "unsupported", "reason": "replaces NeoForge's model pipeline" }
 *   }
 * }
 * }</pre>
 */
public record FabricApiPlan(String fabricApiVersion, Map<String, Module> modules) {
	/** How a module is provided. */
	public enum Plan {
		/** Fabricated implements the module on NeoForge; the upstream jar is not loaded. */
		NATIVE,
		/** The module from the player's Fabric API jar is loaded like any Fabric mod. */
		PASSTHROUGH,
		/** Not loaded; mods requiring it are disabled with an explanation. */
		UNSUPPORTED
	}

	/**
	 * One module decision. {@code version} is set for native modules (the API version provided).
	 * {@code skipMixins} lists pass-through mixins known to conflict with NeoForge
	 * ({@code "config.json:Name"} or {@code "Name"}); they are dropped instead of failing the module, and
	 * {@code reason} documents the behaviour that is lost.
	 */
	public record Module(String id, Plan plan, String version, String reason, List<String> skipMixins) {
	}

	public FabricApiPlan {
		modules = Collections.unmodifiableMap(new LinkedHashMap<>(modules));
	}

	public static FabricApiPlan parse(String json) throws JsonException {
		Map<String, Object> root = Json.parseObject(json);
		if (!(root.get("fabricApiVersion") instanceof String version)) throw new JsonException("fabricApiVersion missing");
		if (!(root.get("modules") instanceof Map<?, ?> mods)) throw new JsonException("modules missing");
		Map<String, Module> modules = new LinkedHashMap<>();
		for (Map.Entry<?, ?> e : mods.entrySet()) {
			String id = String.valueOf(e.getKey());
			if (!(e.getValue() instanceof Map<?, ?> m) || !(m.get("plan") instanceof String plan)) {
				throw new JsonException("Module " + id + " needs a \"plan\"");
			}
			Plan p;
			try {
				p = Plan.valueOf(plan.toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException ex) {
				throw new JsonException("Module " + id + " has unknown plan " + plan);
			}
			String moduleVersion = m.get("version") instanceof String v ? v : null;
			if (p == Plan.NATIVE && moduleVersion == null) throw new JsonException("Native module " + id + " needs a \"version\"");
			String reason = m.get("reason") instanceof String r ? r : "";
			List<String> skip = new ArrayList<>();
			if (m.get("skipMixins") instanceof List<?> list) {
				for (Object o : list) {
					if (!(o instanceof String str)) throw new JsonException("skipMixins of " + id + " must contain strings");
					skip.add(str);
				}
			}
			if (!skip.isEmpty() && p != Plan.PASSTHROUGH) throw new JsonException("skipMixins only applies to passthrough modules (" + id + ")");
			modules.put(id, new Module(id, p, moduleVersion, reason, List.copyOf(skip)));
		}
		return new FabricApiPlan(version, modules);
	}

	/** @return the decision for a module id, or {@code null} if the id is not a Fabric API module */
	public Module module(String id) {
		return modules.get(id);
	}

	public boolean isFabricApiModule(String id) {
		return modules.containsKey(id) || id.equals("fabric-api") || id.equals("fabric");
	}
}
