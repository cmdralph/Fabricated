package io.github.cmdralph.fabricated.core.metadata;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import io.github.cmdralph.fabricated.core.json.Json;
import io.github.cmdralph.fabricated.core.json.JsonException;
import io.github.cmdralph.fabricated.core.metadata.FabricModMetadata.EntrypointSpec;
import io.github.cmdralph.fabricated.core.metadata.FabricModMetadata.MixinEntry;
import io.github.cmdralph.fabricated.fabricloader.version.VersionParser;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.ContactInformation;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.ModEnvironment;
import net.fabricmc.loader.api.metadata.Person;

/**
 * Parses {@code fabric.mod.json}. Schema 1 is supported in full; schema 0 (pre-2019) is rejected with
 * an explanation. Unknown keys are ignored, as Fabric Loader does.
 */
public final class FabricModJsonParser {
	/** Fabric's mod id rule. */
	public static final Pattern FABRIC_ID = Pattern.compile("[a-z][a-z0-9-_]{1,63}");

	private FabricModJsonParser() {
	}

	public static FabricModMetadata parse(String text) throws MetadataException {
		Map<String, Object> root;
		try {
			root = Json.parseObject(text);
		} catch (JsonException e) {
			throw new MetadataException("fabric.mod.json is not valid JSON: " + e.getMessage(), e);
		}

		Object schema = root.get("schemaVersion");
		if (schema == null) {
			throw new MetadataException("fabric.mod.json uses schema version 0 (from before 2019), which is not supported. "
					+ "This mod is too old to run on modern Minecraft.");
		}
		if (!(schema instanceof BigDecimal d) || d.compareTo(BigDecimal.ONE) != 0) {
			throw new MetadataException("Unsupported fabric.mod.json schemaVersion " + schema + " (only 1 is supported)");
		}

		String id = requireString(root, "id");
		if (!FABRIC_ID.matcher(id).matches()) {
			throw new MetadataException("Invalid mod id '" + id + "': must match " + FABRIC_ID.pattern());
		}
		Version version;
		try {
			version = VersionParser.parse(requireString(root, "version"), false);
		} catch (VersionParsingException e) {
			throw new MetadataException("Invalid version for mod " + id + ": " + e.getMessage(), e);
		}

		List<String> provides = stringList(root.get("provides"), "provides");
		for (String p : provides) {
			if (!FABRIC_ID.matcher(p).matches()) {
				throw new MetadataException("Invalid provided id '" + p + "' in mod " + id);
			}
		}

		ModEnvironment environment = parseEnvironment(root.get("environment"), "environment");
		Map<String, List<EntrypointSpec>> entrypoints = parseEntrypoints(root.get("entrypoints"));
		List<String> jars = parseJars(root.get("jars"));
		List<MixinEntry> mixins = parseMixins(root.get("mixins"));
		String classTweaker = optionalString(root, "accessWidener");

		List<ModDependency> dependencies = new ArrayList<>();
		for (ModDependency.Kind kind : ModDependency.Kind.values()) {
			parseDependencies(root.get(kind.getKey()), kind, dependencies);
		}

		String name = optionalString(root, "name");
		String description = optionalString(root, "description");
		List<Person> authors = parsePeople(root.get("authors"), "authors");
		List<Person> contributors = parsePeople(root.get("contributors"), "contributors");
		ContactInformation contact = parseContact(root.get("contact"), "contact");
		List<String> license = stringList(root.get("license"), "license");
		TreeMap<Integer, String> icons = parseIcon(root.get("icon"));
		Map<String, String> languageAdapters = parseStringMap(root.get("languageAdapters"), "languageAdapters");

		Map<String, CustomValue> custom = new LinkedHashMap<>();
		Object customObj = root.get("custom");
		if (customObj != null) {
			if (!(customObj instanceof Map<?, ?> map)) throw new MetadataException("\"custom\" must be an object");
			for (Map.Entry<?, ?> e : map.entrySet()) {
				custom.put(String.valueOf(e.getKey()), CustomValueImpl.fromJson(e.getValue()));
			}
		}

		return new FabricModMetadata("fabric", id, version, provides, environment, entrypoints, jars, mixins,
				classTweaker, dependencies, name != null ? name : id, description != null ? description : "",
				authors, contributors, contact, license, icons, languageAdapters, custom);
	}

	private static String requireString(Map<String, Object> obj, String key) throws MetadataException {
		Object v = obj.get(key);
		if (!(v instanceof String s) || s.isEmpty()) {
			throw new MetadataException("Missing or invalid \"" + key + "\" (must be a non-empty string)");
		}
		return s;
	}

	private static String optionalString(Map<String, Object> obj, String key) throws MetadataException {
		Object v = obj.get(key);
		if (v == null) return null;
		if (!(v instanceof String s)) throw new MetadataException("\"" + key + "\" must be a string");
		return s;
	}

	private static List<String> stringList(Object value, String what) throws MetadataException {
		List<String> ret = new ArrayList<>();
		if (value == null) return ret;
		if (value instanceof String s) {
			ret.add(s);
			return ret;
		}
		if (!(value instanceof List<?> list)) throw new MetadataException("\"" + what + "\" must be a string or an array of strings");
		for (Object o : list) {
			if (!(o instanceof String s)) throw new MetadataException("\"" + what + "\" must only contain strings");
			ret.add(s);
		}
		return ret;
	}

	private static ModEnvironment parseEnvironment(Object value, String what) throws MetadataException {
		if (value == null) return ModEnvironment.UNIVERSAL;
		if (!(value instanceof String s)) throw new MetadataException("\"" + what + "\" must be a string");
		return switch (s.toLowerCase(java.util.Locale.ROOT)) {
			case "*", "" -> ModEnvironment.UNIVERSAL;
			case "client" -> ModEnvironment.CLIENT;
			case "server" -> ModEnvironment.SERVER;
			default -> throw new MetadataException("Invalid environment '" + s + "' (expected *, client or server)");
		};
	}

	private static Map<String, List<EntrypointSpec>> parseEntrypoints(Object value) throws MetadataException {
		Map<String, List<EntrypointSpec>> ret = new LinkedHashMap<>();
		if (value == null) return ret;
		if (!(value instanceof Map<?, ?> map)) throw new MetadataException("\"entrypoints\" must be an object");
		for (Map.Entry<?, ?> e : map.entrySet()) {
			String key = String.valueOf(e.getKey());
			List<EntrypointSpec> specs = new ArrayList<>();
			for (Object o : Json.asList(e.getValue())) {
				if (o instanceof String s) {
					specs.add(new EntrypointSpec("default", s));
				} else if (o instanceof Map<?, ?> m) {
					Object adapter = m.get("adapter");
					Object v = m.get("value");
					if (!(v instanceof String s)) throw new MetadataException("Entrypoint in \"" + key + "\" is missing a string \"value\"");
					if (adapter != null && !(adapter instanceof String)) throw new MetadataException("Entrypoint adapter must be a string");
					specs.add(new EntrypointSpec(adapter == null ? "default" : (String) adapter, s));
				} else {
					throw new MetadataException("Invalid entrypoint declaration in \"" + key + "\"");
				}
			}
			ret.put(key, List.copyOf(specs));
		}
		return ret;
	}

	private static List<String> parseJars(Object value) throws MetadataException {
		List<String> ret = new ArrayList<>();
		if (value == null) return ret;
		if (!(value instanceof List<?> list)) throw new MetadataException("\"jars\" must be an array");
		for (Object o : list) {
			if (!(o instanceof Map<?, ?> m) || !(m.get("file") instanceof String file)) {
				throw new MetadataException("Each entry in \"jars\" must be an object with a \"file\" string");
			}
			ret.add(file);
		}
		return ret;
	}

	private static List<MixinEntry> parseMixins(Object value) throws MetadataException {
		List<MixinEntry> ret = new ArrayList<>();
		if (value == null) return ret;
		if (!(value instanceof List<?> list)) throw new MetadataException("\"mixins\" must be an array");
		for (Object o : list) {
			if (o instanceof String s) {
				ret.add(new MixinEntry(s, ModEnvironment.UNIVERSAL));
			} else if (o instanceof Map<?, ?> m && m.get("config") instanceof String config) {
				ret.add(new MixinEntry(config, parseEnvironment(m.get("environment"), "mixins.environment")));
			} else {
				throw new MetadataException("Each entry in \"mixins\" must be a string or an object with a \"config\" string");
			}
		}
		return ret;
	}

	private static void parseDependencies(Object value, ModDependency.Kind kind, List<ModDependency> out) throws MetadataException {
		if (value == null) return;
		if (!(value instanceof Map<?, ?> map)) throw new MetadataException("\"" + kind.getKey() + "\" must be an object");
		for (Map.Entry<?, ?> e : map.entrySet()) {
			String modId = String.valueOf(e.getKey());
			List<String> requirements = new ArrayList<>();
			Object v = e.getValue();
			if (v instanceof String s) {
				requirements.add(s);
			} else if (v instanceof List<?> list) {
				for (Object o : list) {
					if (!(o instanceof String s)) throw new MetadataException("Version requirement for " + modId + " must be a string");
					requirements.add(s);
				}
			} else {
				throw new MetadataException("Version requirement for " + modId + " must be a string or an array of strings");
			}
			try {
				out.add(new ModDependencyImpl(kind, modId, requirements));
			} catch (VersionParsingException ex) {
				throw new MetadataException("Invalid version requirement for " + modId + ": " + ex.getMessage(), ex);
			}
		}
	}

	private static List<Person> parsePeople(Object value, String what) throws MetadataException {
		List<Person> ret = new ArrayList<>();
		if (value == null) return ret;
		if (!(value instanceof List<?> list)) throw new MetadataException("\"" + what + "\" must be an array");
		for (Object o : list) {
			if (o instanceof String s) {
				ret.add(new ContactInformationImpl.PersonImpl(s, ContactInformation.EMPTY));
			} else if (o instanceof Map<?, ?> m && m.get("name") instanceof String name) {
				ret.add(new ContactInformationImpl.PersonImpl(name, parseContact(m.get("contact"), what + ".contact")));
			} else {
				throw new MetadataException("Each entry in \"" + what + "\" must be a name or an object with a \"name\"");
			}
		}
		return ret;
	}

	private static ContactInformation parseContact(Object value, String what) throws MetadataException {
		if (value == null) return ContactInformation.EMPTY;
		return new ContactInformationImpl(parseStringMap(value, what));
	}

	private static Map<String, String> parseStringMap(Object value, String what) throws MetadataException {
		Map<String, String> ret = new LinkedHashMap<>();
		if (value == null) return ret;
		if (!(value instanceof Map<?, ?> map)) throw new MetadataException("\"" + what + "\" must be an object");
		for (Map.Entry<?, ?> e : map.entrySet()) {
			if (!(e.getValue() instanceof String s)) throw new MetadataException("Values of \"" + what + "\" must be strings");
			ret.put(String.valueOf(e.getKey()), s);
		}
		return ret;
	}

	private static TreeMap<Integer, String> parseIcon(Object value) throws MetadataException {
		TreeMap<Integer, String> ret = new TreeMap<>();
		if (value == null) return ret;
		if (value instanceof String s) {
			ret.put(0, s);
			return ret;
		}
		if (!(value instanceof Map<?, ?> map)) throw new MetadataException("\"icon\" must be a string or an object");
		for (Map.Entry<?, ?> e : map.entrySet()) {
			if (!(e.getValue() instanceof String path)) throw new MetadataException("Icon paths must be strings");
			try {
				int size = Integer.parseInt(String.valueOf(e.getKey()));
				if (size < 1) throw new MetadataException("Icon size must be positive");
				ret.put(size, path);
			} catch (NumberFormatException ex) {
				throw new MetadataException("Icon size '" + e.getKey() + "' is not a number");
			}
		}
		return ret;
	}
}
