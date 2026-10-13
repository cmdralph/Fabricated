package io.github.cmdralph.fabricated.core.metadata;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import io.github.cmdralph.fabricated.core.metadata.FabricModMetadata.EntrypointSpec;
import io.github.cmdralph.fabricated.core.metadata.FabricModMetadata.MixinEntry;

/**
 * A Fabric mod's own instructions for running under Fabricated: the {@code "fabricated"} block of {@code custom} in its
 * {@code fabric.mod.json}. Data only, read with the rest of the metadata; it can only change how this mod loads.
 *
 * <pre>{@code
 * "custom": { "fabricated": {
 *   "schema": 1,
 *   "skipMixins": ["examplemod.mixins.json:client.TooltipMixin"],
 *   "mixins": ["examplemod.mixins.json", "examplemod.neoforge.mixins.json"],
 *   "entrypoints": { "main": ["com.example.NeoForgeCompat"] },
 *   "skipEntrypoints": ["modmenu"],
 *   "patches": [{ "target": "othermod", "skipMixins": ["othermod.mixins.json:Broken"] }]
 * }}
 * }</pre>
 *
 * @param skipMixins      mixins to leave out ({@code "config.json:Name"} or {@code "Name"}), as the config's setting
 * @param mixins          mixin configs to use instead of the top-level {@code mixins}, or null to keep those
 * @param entrypoints     entrypoints added after the mod's own, by key
 * @param skipEntrypoints keys of the mod's own entrypoints to leave out
 * @param patches         changes this mod (a compat patch) makes to other mods; see {@link Patch}
 * @param warnings        problems with the block; a key with a problem is ignored, the rest still applies
 */
public record CompatHints(Set<String> skipMixins, List<MixinEntry> mixins, Map<String, List<EntrypointSpec>> entrypoints,
		Set<String> skipEntrypoints, List<Patch> patches, List<String> warnings) {
	/**
	 * What a compat patch leaves out of another mod. It only applies while the patch mod declares a dependency on the
	 * target and both load, so the dependency's version range says which versions of the target it fixes.
	 */
	public record Patch(String target, Set<String> skipMixins, Set<String> skipEntrypoints) {
		public Patch {
			skipMixins = Set.copyOf(skipMixins);
			skipEntrypoints = Set.copyOf(skipEntrypoints);
		}
	}

	public static final String KEY = "fabricated";
	public static final int SCHEMA = 1;
	public static final CompatHints NONE = new CompatHints(Set.of(), null, Map.of(), Set.of(), List.of(), List.of());

	private static final int MAX_ENTRIES = 64;
	private static final Pattern SKIP_MIXIN = Pattern.compile("([A-Za-z0-9_./-]{1,128}\\.json:)?[A-Za-z_$][A-Za-z0-9_$.]{0,255}");
	private static final Pattern ENTRYPOINT_KEY = Pattern.compile("[A-Za-z0-9_:-]{1,64}");
	private static final Pattern CONFIG_PATH = Pattern.compile("([A-Za-z0-9_-][A-Za-z0-9_.-]*/)*[A-Za-z0-9_-][A-Za-z0-9_.-]*\\.json");

	public CompatHints {
		skipMixins = Set.copyOf(skipMixins);
		mixins = mixins == null ? null : List.copyOf(mixins);
		entrypoints = Map.copyOf(entrypoints);
		skipEntrypoints = Set.copyOf(skipEntrypoints);
		patches = List.copyOf(patches);
		warnings = List.copyOf(warnings);
	}

	public boolean isEmpty() {
		return skipMixins.isEmpty() && mixins == null && entrypoints.isEmpty() && skipEntrypoints.isEmpty() && patches.isEmpty();
	}

	/** The mod's mixin configs under Fabricated. */
	public List<MixinEntry> mixinsOr(List<MixinEntry> declared) {
		return mixins != null ? mixins : declared;
	}

	/** The mod's entrypoints under Fabricated: its own minus {@link #skipEntrypoints}, then {@link #entrypoints}. */
	public Map<String, List<EntrypointSpec>> entrypointsOf(Map<String, List<EntrypointSpec>> declared) {
		if (entrypoints.isEmpty() && skipEntrypoints.isEmpty()) return declared;
		Map<String, List<EntrypointSpec>> result = new LinkedHashMap<>();
		declared.forEach((key, specs) -> {
			if (!skipEntrypoints.contains(key)) result.put(key, specs);
		});
		entrypoints.forEach((key, specs) -> result.merge(key, specs, (own, added) -> {
			List<EntrypointSpec> all = new ArrayList<>(own);
			all.addAll(added);
			return List.copyOf(all);
		}));
		return result;
	}

	/** One line describing what the hints change, for the report. */
	public String describe() {
		List<String> parts = new ArrayList<>();
		if (mixins != null) parts.add("mixin configs " + mixins.stream().map(MixinEntry::config).toList());
		if (!skipMixins.isEmpty()) parts.add("skips mixins " + skipMixins);
		if (!entrypoints.isEmpty()) parts.add("adds entrypoints " + entrypoints.keySet());
		if (!skipEntrypoints.isEmpty()) parts.add("skips entrypoints " + skipEntrypoints);
		if (!patches.isEmpty()) parts.add("patches " + patches.stream().map(Patch::target).toList());
		return String.join(", ", parts);
	}

	/** Parses the block (the value of {@code custom.fabricated}); never throws, problems become {@link #warnings}. */
	static CompatHints parse(Object block) {
		List<String> warnings = new ArrayList<>();
		if (!(block instanceof Map<?, ?> map)) {
			return new CompatHints(Set.of(), null, Map.of(), Set.of(), List.of(), List.of("custom.fabricated must be an object; ignored"));
		}
		Object schema = map.get("schema");
		if (schema != null && !(schema instanceof Number n && n.intValue() >= 1 && n.intValue() <= SCHEMA)) {
			return new CompatHints(Set.of(), null, Map.of(), Set.of(), List.of(),
					List.of("custom.fabricated uses schema " + schema + ", which needs a newer Fabricated; ignored"));
		}
		Set<String> skipMixins = Set.of();
		List<MixinEntry> mixins = null;
		Map<String, List<EntrypointSpec>> entrypoints = Map.of();
		Set<String> skipEntrypoints = Set.of();
		List<Patch> patches = List.of();
		for (Map.Entry<?, ?> e : map.entrySet()) {
			String key = String.valueOf(e.getKey());
			try {
				switch (key) {
					case "schema" -> {
					}
					case "skipMixins" -> skipMixins = strings(e.getValue(), key, SKIP_MIXIN);
					case "skipEntrypoints" -> skipEntrypoints = strings(e.getValue(), key, ENTRYPOINT_KEY);
					case "mixins" -> {
						List<MixinEntry> parsed = FabricModJsonParser.parseMixins(e.getValue());
						if (parsed.size() > MAX_ENTRIES) throw new MetadataException("more than " + MAX_ENTRIES + " entries");
						for (MixinEntry entry : parsed) {
							if (!CONFIG_PATH.matcher(entry.config()).matches() || entry.config().contains("..")) {
								throw new MetadataException("\"" + entry.config() + "\" is not a mixin config path in this jar");
							}
						}
						mixins = parsed;
					}
					case "entrypoints" -> {
						Map<String, List<EntrypointSpec>> parsed = FabricModJsonParser.parseEntrypoints(e.getValue());
						if (parsed.values().stream().mapToInt(List::size).sum() > MAX_ENTRIES) throw new MetadataException("more than " + MAX_ENTRIES + " entries");
						for (String k : parsed.keySet()) {
							if (!ENTRYPOINT_KEY.matcher(k).matches()) throw new MetadataException("invalid entrypoint key \"" + k + "\"");
						}
						entrypoints = parsed;
					}
					case "patches" -> patches = patches(e.getValue());
					default -> warnings.add("custom.fabricated: unknown key \"" + key + "\" ignored");
				}
			} catch (MetadataException ex) {
				warnings.add("custom.fabricated." + key + " ignored: " + ex.getMessage());
			}
		}
		return new CompatHints(skipMixins, mixins, entrypoints, skipEntrypoints, patches, warnings);
	}

	private static List<Patch> patches(Object value) throws MetadataException {
		if (!(value instanceof List<?> list)) throw new MetadataException("must be a list of objects");
		if (list.size() > 16) throw new MetadataException("more than 16 entries");
		List<Patch> patches = new ArrayList<>();
		for (Object o : list) {
			if (!(o instanceof Map<?, ?> entry) || !(entry.get("target") instanceof String target) || !FabricModJsonParser.FABRIC_ID.matcher(target).matches()) {
				throw new MetadataException("each entry needs a \"target\" mod id");
			}
			for (Object k : entry.keySet()) {
				if (!Set.of("target", "skipMixins", "skipEntrypoints").contains(k)) throw new MetadataException("unknown key \"" + k + "\" in a patch");
			}
			Set<String> mixins = entry.containsKey("skipMixins") ? strings(entry.get("skipMixins"), "skipMixins", SKIP_MIXIN) : Set.of();
			Set<String> entrypoints = entry.containsKey("skipEntrypoints") ? strings(entry.get("skipEntrypoints"), "skipEntrypoints", ENTRYPOINT_KEY) : Set.of();
			patches.add(new Patch(target, mixins, entrypoints));
		}
		return patches;
	}

	private static Set<String> strings(Object value, String key, Pattern pattern) throws MetadataException {
		if (!(value instanceof List<?> list)) throw new MetadataException("must be a list of strings");
		if (list.size() > MAX_ENTRIES) throw new MetadataException("more than " + MAX_ENTRIES + " entries");
		Set<String> result = new LinkedHashSet<>();
		for (Object o : list) {
			if (!(o instanceof String s) || !pattern.matcher(s).matches()) throw new MetadataException("invalid entry " + o);
			result.add(s);
		}
		return result;
	}
}
